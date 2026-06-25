package com.example.browser

import android.webkit.WebSettings
import android.webkit.WebView
import android.util.Log

object WebViewDesktopBridge {
    private const val TAG = "WebViewDesktopBridge"

    fun configureWebViewSettings(
        webView: WebView,
        isDesktop: Boolean,
        isJavaScriptEnabled: Boolean = true,
        isHardwareAccelerationEnabled: Boolean = true
    ) {
        try {
            val settings = webView.settings
            
            // Core standard settings
            settings.javaScriptEnabled = isJavaScriptEnabled
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            
            // Layout and viewports
            if (isDesktop) {
                // Configure Chrome-class Wide Desktop Viewport
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                
                // Set layout algorithm to default for standard unconstrained rendering
                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                
                // Enable mixed content mode for complex sites
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                
                // Setup true desktop user agent
                val url = webView.url.orEmpty()
                settings.userAgentString = UserAgentManager.getDesktopUserAgent(url)
            } else {
                // Restore Mobile responsive settings
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                
                settings.userAgentString = UserAgentManager.getMobileUserAgent()
            }
            
            // Enable rendering optimizations
            webView.setLayerType(
                if (isHardwareAccelerationEnabled) WebView.LAYER_TYPE_HARDWARE else WebView.LAYER_TYPE_SOFTWARE,
                null
            )
            
            Log.i(TAG, "WebView settings successfully configured for Desktop: $isDesktop")
        } catch (e: Exception) {
            Log.e(TAG, "Failed configuring WebView settings", e)
        }
    }

    fun injectDesktopRuntimeEnvironment(webView: WebView, isDesktop: Boolean) {
        if (!isDesktop) {
            // Restore mobile viewport
            webView.evaluateJavascript(ViewportManager.getMobileViewportRestoreScript(), null)
            webView.evaluateJavascript(DesktopCssEnvironment.getMobileCssRestoreScript(), null)
            webView.evaluateJavascript(DeviceMetricsOverride.getMetricsOverrideScript(false), null)
            return
        }

        // 1. Inject forced 1280px viewport meta
        webView.evaluateJavascript(ViewportManager.getDesktopViewportScript(), null)

        // 2. Inject custom CSS overriding hardcoded mobile width bounds
        webView.evaluateJavascript(DesktopCssEnvironment.getDesktopCssOverrideScript(), null)

        // 3. Inject physical screen metrics overrides
        webView.evaluateJavascript(DeviceMetricsOverride.getMetricsOverrideScript(true), null)

        Log.i(TAG, "Desktop runtime overrides successfully injected into WebView!")
    }

    fun runLayoutProbes(webView: WebView) {
        try {
            // Evaluates layout conformance via probes
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var bodyWidth = document.body ? document.body.clientWidth : 0;
                        return bodyWidth;
                    } catch(e) { return 0; }
                })();
                """.trimIndent()
            ) { result ->
                val width = result?.toIntOrNull() ?: 0
                Log.d(TAG, "Layout probe returned body width: $width px")
            }
        } catch(e: Exception) {
            Log.e(TAG, "Failed running layout probes", e)
        }
    }
}
