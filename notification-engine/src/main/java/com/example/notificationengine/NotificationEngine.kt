package com.example.notificationengine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Interface representing the primary public gateway wrapper for the Notification Module.
 */
interface NotificationEngine {
    fun initialize(context: Context)
    fun getJavascriptPolyfill(websiteUrl: String, callback: (String) -> Unit)
}

/**
 * Production-ready implementation of the Web Notification interceptor, bridging Javascript to Native APIs.
 */
class NotificationEngineImpl(private val context: Context) : NotificationEngine {
    private val permissionStore = WebsitePermissionStore(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TAG = "NotificationEngineImpl"

    override fun initialize(context: Context) {
        Log.i(TAG, "Initializing Notification Engine and Scheduling Background Sync Service")
        BackgroundNotificationService.startEngine(context)
    }

    /**
     * Resolves and builds a custom Javascript code injection string to polyfill standard Notification API on the fly.
     */
    override fun getJavascriptPolyfill(websiteUrl: String, callback: (String) -> Unit) {
        scope.launch {
            val permission = permissionStore.getPermission(websiteUrl)
            val jsPermission = when (permission) {
                "ALLOW" -> "granted"
                "BLOCK" -> "denied"
                else -> "default"
            }

            val script = """
                (function() {
                    // Check if already polyfilled to avoid duplicate overrides
                    if (window.Notification && window.Notification.isSwiftPolyfill) return;

                    function SwiftNotification(title, options) {
                        this.title = title;
                        this.options = options || {};
                        
                        // Hand off instant notification requests directly to Native Client Bridge
                        if (SwiftNotification.permission === 'granted') {
                            if (window.AndroidNotificationBridge) {
                                window.AndroidNotificationBridge.postNotification(title, this.options.body || '', window.location.href);
                            }
                        }
                    }

                    SwiftNotification.isSwiftPolyfill = true;
                    SwiftNotification.permission = '$jsPermission';

                    SwiftNotification.requestPermission = function(callback) {
                        return new Promise(function(resolve, reject) {
                            if (!window.AndroidNotificationBridge) {
                                resolve('default');
                                if (callback) callback('default');
                                return;
                            }
                            // Request Permission via Native Interceptor
                            window.AndroidNotificationBridge.requestPermission(window.location.origin, document.title);
                            
                            // Periodic poll checking to wait for Native Dialog callbacks
                            var checkInterval = setInterval(function() {
                                var currentPerm = window.AndroidNotificationBridge.getSavedPermission(window.location.origin);
                                if (currentPerm !== 'ASK') {
                                    clearInterval(checkInterval);
                                    var webPerm = currentPerm === 'ALLOW' ? 'granted' : 'denied';
                                    SwiftNotification.permission = webPerm;
                                    resolve(webPerm);
                                    if (callback) callback(webPerm);
                                }
                            }, 500);
                        });
                    };

                    // Overwrite the web API globally
                    window.Notification = SwiftNotification;
                    console.log('SwiftBrowser: WebView Notification API successfully polyfilled for ' + window.location.origin);
                })();
            """.trimIndent()
            callback(script)
        }
    }
}

/**
 * Native Bridge mapping for Javascript to trigger alerts and permission dialog controls inside WebViews.
 */
class AndroidNotificationBridge(
    private val context: Context,
    private val updateDialogState: (show: Boolean, origin: String, title: String) -> Unit
) {
    private val permissionStore = WebsitePermissionStore(context)
    private val db = NotificationDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val TAG = "NotificationBridge"

    @JavascriptInterface
    fun requestPermission(origin: String, pageTitle: String) {
        Log.i(TAG, "Website requested permission: $origin ($pageTitle)")
        handler.post {
            // Callback to trigger visual Compose popup dialog inside the active BrowserScreen layout
            updateDialogState(true, origin, pageTitle)
        }
    }

    @JavascriptInterface
    fun getSavedPermission(origin: String): String {
        var result = "ASK"
        try {
            val syncJob = scope.launch {
                result = permissionStore.getPermission(origin)
            }
            while (!syncJob.isCompleted) { Thread.sleep(50) } // Lightweight sync query blocking hook
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get permission: ${e.message}")
        }
        return result
    }

    @JavascriptInterface
    fun postNotification(title: String, body: String, clickUrl: String) {
        Log.i(TAG, "Instant Notification Posted from WebView: $title")
        scope.launch {
            val host = NotificationRegistry.getHostDomain(clickUrl)
            val isAllowed = permissionStore.isAllowed(clickUrl)
            if (isAllowed) {
                // Instantly construct and display the notice
                val sub = db.subscriptionDao().getSubscription(clickUrl)
                val channelId = NotificationChannelManager.getChannelId(
                    priority = sub?.priority ?: 1,
                    soundEnabled = sub?.soundEnabled ?: true,
                    vibrationEnabled = sub?.vibrationEnabled ?: true,
                    isMuted = sub?.isMuted ?: false
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(clickUrl)
                    setPackage(context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("notification_click_url", clickUrl)
                    putExtra("NOTIFICATION_URL", clickUrl)
                }

                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    clickUrl.hashCode(),
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("[$host] $title")
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(clickUrl.hashCode(), builder.build())

                // Insert into history log
                val historyItem = NotificationHistoryItem(
                    websiteUrl = clickUrl,
                    websiteName = host.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                    title = title,
                    body = body,
                    clickUrl = clickUrl
                )
                db.historyDao().insertHistoryItem(historyItem)
            }
        }
    }
}
