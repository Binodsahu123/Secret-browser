package com.example.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView

object DesktopModeManager {
    // Mobile user agent (default)
    const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 Orion/2.0"

    // Desktop user agent (true desktop UA)
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Orion/2.0"

    // Mac desktop UA (alternative)
    const val DESKTOP_UA_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Orion/2.0"

    // Per-site desktop mode storage
    private val siteDesktopMode = HashMap<String, Boolean>()

    private fun getCanonicalHost(host: String): String {
        val h = host.lowercase().trim()
        return when {
            h == "youtube.com" || h == "www.youtube.com" || h == "m.youtube.com" -> "youtube.com"
            h == "facebook.com" || h == "www.facebook.com" || h == "m.facebook.com" -> "facebook.com"
            h == "reddit.com" || h == "www.reddit.com" || h == "m.reddit.com" -> "reddit.com"
            h == "twitter.com" || h == "www.twitter.com" || h == "m.twitter.com" || h == "mobile.twitter.com" ||
            h == "x.com" || h == "www.x.com" || h == "m.x.com" || h == "mobile.x.com" -> "x.com"
            h.endsWith(".wikipedia.org") || h == "wikipedia.org" -> "wikipedia.org"
            h.startsWith("m.") -> h.substring(2)
            h.startsWith("mobile.") -> h.substring(7)
            h.startsWith("www.") -> h.substring(4)
            else -> h
        }
    }

    fun resolveDesktopUrl(urlStr: String, isDesktop: Boolean): String {
        if (urlStr.isBlank()) return urlStr
        try {
            val uri = Uri.parse(urlStr)
            val host = uri.host ?: return urlStr
            
            if (isDesktop) {
                // Redirect mobile subdomains to desktop domains when Desktop Mode is enabled
                val newHost = when {
                    host.equals("m.youtube.com", ignoreCase = true) -> "www.youtube.com"
                    host.equals("m.facebook.com", ignoreCase = true) -> "www.facebook.com"
                    host.equals("m.reddit.com", ignoreCase = true) -> "www.reddit.com"
                    host.equals("mobile.twitter.com", ignoreCase = true) || host.equals("m.twitter.com", ignoreCase = true) || host.equals("mobile.x.com", ignoreCase = true) || host.equals("m.x.com", ignoreCase = true) -> "x.com"
                    host.contains(".m.wikipedia.org", ignoreCase = true) -> host.replace(".m.wikipedia.org", ".wikipedia.org", ignoreCase = true)
                    else -> host
                }
                if (newHost != host) {
                    return uri.buildUpon().authority(newHost).toString()
                }
            } else {
                // Restore mobile subdomain for key video and social sites if navigating back to mobile mode
                val newHost = when {
                    host.equals("youtube.com", ignoreCase = true) || host.equals("www.youtube.com", ignoreCase = true) -> "m.youtube.com"
                    host.equals("facebook.com", ignoreCase = true) || host.equals("www.facebook.com", ignoreCase = true) -> "m.facebook.com"
                    host.equals("reddit.com", ignoreCase = true) || host.equals("www.reddit.com", ignoreCase = true) -> "m.reddit.com"
                    host.equals("twitter.com", ignoreCase = true) || host.equals("x.com", ignoreCase = true) || host.equals("www.x.com", ignoreCase = true) -> "mobile.twitter.com"
                    else -> host
                }
                if (newHost != host) {
                    return uri.buildUpon().authority(newHost).toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return urlStr
    }

    fun setDesktopMode(context: Context, webView: WebView, domain: String, enabled: Boolean) {
        val canonical = getCanonicalHost(domain)
        siteDesktopMode[canonical] = enabled
        val prefs = context.getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("desktop_mode_$canonical", enabled).apply()
        prefs.edit().putBoolean("desktop_mode_$domain", enabled).apply()
        applyMode(webView, enabled)

        // Switch URL to the desktopped / mobiled variant
        val currentUrl = webView.url ?: ""
        val resolvedUrl = resolveDesktopUrl(currentUrl, enabled)
        if (resolvedUrl != currentUrl) {
            webView.post {
                webView.loadUrl(resolvedUrl)
            }
        }
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
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                // Force desktop viewport
                webView.evaluateJavascript("""
                    var meta = document.querySelector('meta[name=viewport]');
                    if(meta) {
                        meta.content = 'width=1280, initial-scale=0.25, minimum-scale=0.1';
                    } else {
                        var newMeta = document.createElement('meta');
                        newMeta.name = 'viewport';
                        newMeta.content = 'width=1280, initial-scale=0.25, minimum-scale=0.1';
                        document.head.appendChild(newMeta);
                    }
                """.trimIndent(), null)
            } else {
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
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
        val canonical = getCanonicalHost(domain)
        val cached = siteDesktopMode[canonical]
        if (cached != null) return cached
        val prefs = context.getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("desktop_mode_$canonical", false)
        siteDesktopMode[canonical] = enabled
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
