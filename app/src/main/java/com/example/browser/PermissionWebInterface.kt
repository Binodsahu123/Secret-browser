package com.example.browser

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class PermissionWebInterface(
    private val webView: WebView,
    private val context: Context,
    private val onTriggerSystemPrompt: (transactionId: String, origin: String, type: String) -> Unit
) {
    private val mainScope = CoroutineScope(Dispatchers.Main)

    @JavascriptInterface
    fun requestPermission(payloadStr: String) {
        mainScope.launch(Dispatchers.Main.immediate) {
            try {
                Log.d("PermissionWebInterface", "Ingested legacy web permission payload: $payloadStr")
                val json = JSONObject(payloadStr)
                val transactionId = json.getString("transactionId")
                val origin = json.getString("origin")
                val permissionType = json.getString("permissionType") // "MICROPHONE" or "CAMERA"

                evaluateAndPrompt(transactionId, origin, permissionType)
            } catch (e: Exception) {
                Log.e("PermissionWebInterface", "Failed to parse legacy permission interface request payload", e)
            }
        }
    }

    @JavascriptInterface
    fun requestHardwareAccess(payloadStr: String) {
        mainScope.launch(Dispatchers.Main.immediate) {
            try {
                Log.d("PermissionWebInterface", "Ingested hardware access payload: $payloadStr")
                val json = JSONObject(payloadStr)
                val transactionId = json.getString("id")
                val origin = json.getString("url")
                
                // Determine permission type based on request constraints
                val isAudio = json.optBoolean("audioRequested", true)
                val permissionType = if (isAudio) "MICROPHONE" else "CAMERA"

                evaluateAndPrompt(transactionId, origin, permissionType)
            } catch (e: Exception) {
                Log.e("PermissionWebInterface", "Failed to parse hardware access request payload", e)
            }
        }
    }

    private fun evaluateAndPrompt(transactionId: String, origin: String, permissionType: String) {
        // Check local cached decisions from databases first before prompting user (3-Tier Sec Policy)
        DynamicPermissionEngine.evaluatePermissionAsync(context, origin, permissionType) { cachedState ->
            when (cachedState) {
                1 -> { // ALLOWED
                    Log.d("PermissionWebInterface", "Tier 1 HIT: Auto-resolve grant for $origin -> $permissionType")
                    DynamicPermissionEngine.registerPendingRequest(transactionId, origin, permissionType, webView) { /* no-op */ }
                    DynamicPermissionEngine.completeRequest(transactionId, true)
                }
                2 -> { // BLOCKED
                    Log.d("PermissionWebInterface", "Tier 1 HIT: Auto-resolve block for $origin -> $permissionType")
                    DynamicPermissionEngine.registerPendingRequest(transactionId, origin, permissionType, webView) { /* no-op */ }
                    DynamicPermissionEngine.completeRequest(transactionId, false)
                }
                else -> { // ASK (0) - Trigger custom Material prompt overlays or standard OS request synchronization
                    DynamicPermissionEngine.registerPendingRequest(transactionId, origin, permissionType, webView) { /* no-op */ }
                    onTriggerSystemPrompt(transactionId, origin, permissionType)
                }
            }
        }
    }
}
