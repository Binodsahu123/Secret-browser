package com.example.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.example.data.PermissionDatabase
import com.example.data.PermissionEntity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object DynamicPermissionEngine {
    private const val TAG = "DynamicPermissionEngine"
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // JS Callbacks Registry: Transaction ID -> State Holder
    data class PendingPermissionCallback(
        val origin: String,
        val permissionType: String,
        val webView: WebView,
        val onResult: (Boolean) -> Unit
    )
    
    private val pendingCallbacks = ConcurrentHashMap<String, PendingPermissionCallback>()

    fun registerPendingRequest(
        transactionId: String,
        origin: String,
        permissionType: String,
        webView: WebView,
        onResult: (Boolean) -> Unit
    ) {
        pendingCallbacks[transactionId] = PendingPermissionCallback(origin, permissionType, webView, onResult)
    }

    fun completeRequest(transactionId: String, allowed: Boolean) {
        val callback = pendingCallbacks.remove(transactionId) ?: return
        
        // Dispatch resolution back to JavaScript on the main/WebView thread
        Handler(Looper.getMainLooper()).post {
            try {
                callback.onResult(allowed)
                // 1 = PERMISSION_ALLOWED ("granted"), 2 = PERMISSION_BLOCKED / DENIED ("denied")
                val statusInt = if (allowed) 1 else 2
                
                // Trigger window.resolveAndroidPermissionBridge in WebView to resume JS Promise
                val script = """
                    if (window.resolveAndroidPermissionBridge) { 
                        window.resolveAndroidPermissionBridge('$transactionId', $statusInt, { useFallbackRoute: false, streamUrl: null }); 
                    }
                """.trimIndent()
                
                callback.webView.evaluateJavascript(script, null)
                Log.d(TAG, "Successfully resolved web-based JS permission $transactionId with status $statusInt")
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating JS callback for $transactionId", e)
            }
        }
    }

    /**
     * Tier 1 Security Evaluation for web origins: Cached Decisions lookup.
     */
    fun evaluatePermissionAsync(
        context: Context,
        origin: String,
        permissionType: String,
        onResult: (Int) -> Unit
    ) {
        mainScope.launch(Dispatchers.IO) {
            try {
                val db = PermissionDatabase.getDatabase(context)
                val state = db.permissionDao().getPermissionState(origin, permissionType) ?: 0 // Default: ASK (0)
                
                withContext(Dispatchers.Main) {
                    onResult(state)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed evaluating Room setting for DB lookup", e)
                withContext(Dispatchers.Main) {
                    onResult(0) // Safe Fallback: ASK (0)
                }
            }
        }
    }

    /**
     * Persistently saves site user decisions to the local SQLite database.
     */
    fun savePermissionDecision(
        context: Context,
        origin: String,
        permissionType: String,
        allowed: Boolean
    ) {
        mainScope.launch(Dispatchers.IO) {
            try {
                // 1. Save to Room database
                val db = PermissionDatabase.getDatabase(context)
                val state = if (allowed) 1 else 2 // 1: ALLOWED, 2: BLOCKED
                val entity = PermissionEntity(
                    originUrl = origin,
                    permissionType = permissionType,
                    permissionState = state
                )
                db.permissionDao().savePermissionChoice(entity)
                Log.i(TAG, "Saved site setting choice to Database: $origin -> $permissionType -> $state")

                // 2. Sync to Shared SharedPreferences so standard onPermissionRequest immediately allows it
                val cleanDomain = try {
                    val uri = android.net.Uri.parse(origin)
                    var host = uri.host ?: origin
                    if (host.startsWith("www.")) {
                        host = host.substring(4)
                    }
                    host.lowercase()
                } catch (e: Exception) {
                    origin.lowercase()
                }
                val perm = permissionType.lowercase()
                val statusStr = if (allowed) "Allow" else "Block"
                val sharedPrefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("site_perm_exception/$perm/$cleanDomain", statusStr).apply()
                Log.i(TAG, "Synced site setting choice to SharedPreferences: site_perm_exception/$perm/$cleanDomain -> $statusStr")
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving site settings choice", e)
            }
        }
    }
}
