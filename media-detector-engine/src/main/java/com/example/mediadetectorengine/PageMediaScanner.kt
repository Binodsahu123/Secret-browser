package com.example.mediadetectorengine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PageMediaScanner(private val context: Context) {
    private val TAG = "PageMediaScanner"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _detectionState = MutableStateFlow<MediaDetectionState>(MediaDetectionState.Idle)
    val detectionState: StateFlow<MediaDetectionState> = _detectionState.asStateFlow()

    private val detectedCandidates = mutableMapOf<String, MediaCandidateModel>()

    private var activeWebView: WebView? = null
    private var webViewBridge: WebViewMediaBridge? = null

    init {
        setupBridge()
    }

    private fun setupBridge() {
        webViewBridge = WebViewMediaBridge(
            onCandidates = { candidates ->
                MediaDebugLogger.log(TAG, "Bridge reports ${candidates.size} media candidates discovered.", "SUCCESS", "KOTLIN_CORE")
                synchronized(detectedCandidates) {
                    candidates.forEach { candidate ->
                        detectedCandidates[candidate.url] = candidate
                    }
                    val list = detectedCandidates.values.toList()
                    _detectionState.value = MediaDetectionState.CandidatesFound(list)
                }
            },
            onRoute = { spaUrl ->
                MediaDebugLogger.log(TAG, "SPA route changed to $spaUrl, resetting scan data.", "INFO", "KOTLIN_CORE")
                clearScanData()
                triggerFullScan()
            },
            onTriggerScan = {
                MediaDebugLogger.log(TAG, "Re-scan triggered by dynamic observer.", "INFO", "KOTLIN_CORE")
                triggerFullScan()
            }
        )
    }

    fun attachWebView(webView: WebView) {
        mainHandler.post {
            try {
                if (activeWebView == webView) return@post
                
                activeWebView = webView
                webView.settings.javaScriptEnabled = true
                webView.addJavascriptInterface(webViewBridge!!, "OrionMediaBridge")
                
                MediaDebugLogger.log(TAG, "OrionMediaBridge successfully attached to active WebView container.", "INFO", "KOTLIN_CORE")
                clearScanData()
            } catch (e: Exception) {
                Log.e(TAG, "Failed attaching web view bridge", e)
                MediaDebugLogger.log(TAG, "Attachment failure: ${e.localizedMessage}", "ERROR", "KOTLIN_CORE")
            }
        }
    }

    fun detachWebView() {
        mainHandler.post {
            try {
                activeWebView?.removeJavascriptInterface("OrionMediaBridge")
                activeWebView = null
                MediaDebugLogger.log(TAG, "WebView detached from scanner.", "INFO", "KOTLIN_CORE")
            } catch (e: Exception) {
                Log.e(TAG, "Detach error", e)
            }
        }
    }

    fun clearScanData() {
        synchronized(detectedCandidates) {
            detectedCandidates.clear()
        }
        _detectionState.value = MediaDetectionState.Idle
    }

    fun triggerFullScan() {
        mainHandler.post {
            val webView = activeWebView ?: return@post
            _detectionState.value = MediaDetectionState.Scanning
            MediaDebugLogger.log(TAG, "Injecting Orion JS Probes into page context...", "INFO", "KOTLIN_CORE")

            val mediaProbe = JsMediaProbe.loadAssetScript(context, "orion_media_probe.js")
            val networkProbe = JsMediaProbe.loadAssetScript(context, "orion_network_probe.js")
            val spaWatcher = JsMediaProbe.loadAssetScript(context, "orion_spa_watcher.js")

            if (mediaProbe.isNotEmpty()) {
                webView.evaluateJavascript(mediaProbe, null)
            }
            if (networkProbe.isNotEmpty()) {
                webView.evaluateJavascript(networkProbe, null)
            }
            if (spaWatcher.isNotEmpty()) {
                webView.evaluateJavascript(spaWatcher, null)
            }
        }
    }
}
