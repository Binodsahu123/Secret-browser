package com.example.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.util.Log

object DesktopModeManager {
    private const val TAG = "DesktopModeManager"

    // Backwards-compatible constants matching existing usage
    const val MOBILE_UA = UserAgentManager.MOBILE_UA_CHROME
    const val DESKTOP_UA = UserAgentManager.DESKTOP_UA_CHROME
    const val DESKTOP_UA_MAC = UserAgentManager.DESKTOP_UA_SAFARI

    // Local runtime cache for site modes
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
        // Delegate to high-speed native rewrite engine with safe Kotlin fallback
        return try {
            NativeDesktopEngine.nativeRewriteUrl(urlStr, isDesktop)
        } catch (e: UnsatisfiedLinkError) {
            if (isDesktop) UrlRewriteEngine.rewriteToDesktop(urlStr) else UrlRewriteEngine.rewriteToMobile(urlStr)
        }
    }

    fun setDesktopMode(context: Context, webView: WebView, domain: String, enabled: Boolean) {
        val canonical = getCanonicalHost(domain)
        siteDesktopMode[canonical] = enabled
        
        val prefs = context.getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("desktop_mode_$canonical", enabled).apply()
        prefs.edit().putBoolean("desktop_mode_$domain", enabled).apply()

        // Sync diagnostics live telemetry values
        DeveloperDesktopDiagnostics.logToggleEvent(domain, enabled)
        DeveloperDesktopDiagnostics.updateMonitorState(
            desktopModeEnabled = enabled,
            userAgentType = if (enabled) "Desktop" else "Mobile",
            viewportWidth = if (enabled) 1280 else 360,
            urlRewriteSuccess = true,
            currentUrl = webView.url.orEmpty()
        )

        applyMode(webView, enabled)

        // Switch URL using UrlRewriteEngine (Desktop or Mobile)
        val currentUrl = webView.url ?: ""
        val resolvedUrl = resolveDesktopUrl(currentUrl, enabled)
        if (resolvedUrl != currentUrl) {
            DeveloperDesktopDiagnostics.updateConnectionState(
                userAgentApplied = true,
                viewportApplied = true,
                cssRulesApplied = true,
                hostRewriteApplied = true,
                hostRewriteSkipped = false,
                desktopPageLoaded = true
            )
            webView.post {
                webView.loadUrl(resolvedUrl)
            }
        } else {
            DeveloperDesktopDiagnostics.logRewriteSkip(domain, "Domain is already normalized")
            DeveloperDesktopDiagnostics.updateConnectionState(
                userAgentApplied = true,
                viewportApplied = true,
                cssRulesApplied = true,
                hostRewriteApplied = false,
                hostRewriteSkipped = true,
                desktopPageLoaded = true
            )
        }
    }

    fun applyMode(webView: WebView, isDesktop: Boolean) {
        // Delegate Settings to WebViewDesktopBridge
        WebViewDesktopBridge.configureWebViewSettings(webView, isDesktop)

        // Inject optimal CSS, viewport overlays and device metric mock structures
        WebViewDesktopBridge.injectDesktopRuntimeEnvironment(webView, isDesktop)

        // Reload page to apply changes
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
