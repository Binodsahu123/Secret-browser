package com.example.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebView

object DesktopModeManager {
    // Mobile user agent (default)
    const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.6099.144 Mobile Safari/537.36"

    // Desktop user agent (true desktop UA)
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.6099.144 Safari/537.36"

    // Mac desktop UA (alternative)
    const val DESKTOP_UA_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.6099.144 Safari/537.36"

    // Per-site desktop mode storage
    private val siteDesktopMode = HashMap<String, Boolean>()

    fun setDesktopMode(context: Context, webView: WebView, domain: String, enabled: Boolean) {
        siteDesktopMode[domain] = enabled
        val prefs = context.getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("desktop_mode_$domain", enabled).apply()
        applyMode(webView, enabled)
    }

    fun applyMode(webView: WebView, isDesktop: Boolean) {
        webView.settings.apply {
            userAgentString = if (isDesktop) DESKTOP_UA else MOBILE_UA

            // Desktop mode specific settings
            if (isDesktop) {
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // Force desktop viewport
                webView.evaluateJavascript("""
                    var meta = document.querySelector('meta[name=viewport]');
                    if(meta) {
                        meta.content = 'width=1280, initial-scale=0.1';
                    } else {
                        var newMeta = document.createElement('meta');
                        newMeta.name = 'viewport';
                        newMeta.content = 'width=1280, initial-scale=0.1';
                        document.head.appendChild(newMeta);
                    }
                """.trimIndent(), null)
            } else {
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // Restore mobile viewport
                webView.evaluateJavascript("""
                    var meta = document.querySelector('meta[name=viewport]');
                    if(meta) {
                        meta.content = 'width=device-width, initial-scale=1.0';
                    }
                """.trimIndent(), null)
            }
        }

        // Reload current page with new UA
        webView.post {
            webView.reload()
        }
    }

    fun isDesktopMode(context: Context, domain: String): Boolean {
        val cached = siteDesktopMode[domain]
        if (cached != null) return cached
        val prefs = context.getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("desktop_mode_$domain", false)
        siteDesktopMode[domain] = enabled
        return enabled
    }

    fun toggleDesktopMode(context: Context, webView: WebView, currentUrl: String): Boolean {
        val domain = Uri.parse(currentUrl).host ?: return false
        val current = isDesktopMode(context, domain)
        val newMode = !current
        setDesktopMode(context, webView, domain, newMode)
        return newMode
    }
}
