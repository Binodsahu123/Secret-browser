package com.example.browser

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class DesktopCompatibilityRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("orion_desktop_compatibility", Context.MODE_PRIVATE)
    private val cachedRules = ConcurrentHashMap<String, DesktopSiteRule>()

    init {
        // Hydrate default site rules for high-priority sites
        preloadDefaultRules()
    }

    private fun preloadDefaultRules() {
        val rules = listOf(
            DesktopSiteRule(
                domain = "youtube.com",
                desktopSubdomainRewrite = "www.youtube.com",
                customCssOverrides = """
                    /* Ensure wide screen layout is unconstrained */
                    ytd-app { max-width: 100% !important; }
                    /* Fix video player overlay layouts in desktop web mode */
                    .html5-video-player { min-height: 480px !important; }
                """.trimIndent()
            ),
            DesktopSiteRule(
                domain = "facebook.com",
                desktopSubdomainRewrite = "www.facebook.com"
            ),
            DesktopSiteRule(
                domain = "reddit.com",
                desktopSubdomainRewrite = "www.reddit.com",
                customCssOverrides = "body { min-width: 1200px !important; }"
            ),
            DesktopSiteRule(
                domain = "twitter.com",
                desktopSubdomainRewrite = "x.com"
            ),
            DesktopSiteRule(
                domain = "x.com",
                desktopSubdomainRewrite = "x.com"
            ),
            DesktopSiteRule(
                domain = "wikipedia.org",
                customCssOverrides = "#content { margin-left: 10em !important; }"
            )
        )
        for (rule in rules) {
            cachedRules[rule.domain] = rule
        }
    }

    fun getRuleForHost(host: String): DesktopSiteRule? {
        val cleanHost = getCanonicalHost(host)
        return cachedRules[cleanHost] ?: cachedRules.keys.find { cleanHost.endsWith(it) }?.let { cachedRules[it] }
    }

    fun isDesktopEnabledForHost(host: String): Boolean {
        val cleanHost = getCanonicalHost(host)
        return prefs.getBoolean("host_desktop_enabled_$cleanHost", false)
    }

    fun setDesktopEnabledForHost(host: String, enabled: Boolean) {
        val cleanHost = getCanonicalHost(host)
        prefs.edit().putBoolean("host_desktop_enabled_$cleanHost", enabled).apply()
    }

    private fun getCanonicalHost(host: String): String {
        val h = host.lowercase().trim()
        return when {
            h.startsWith("m.") -> h.substring(2)
            h.startsWith("mobile.") -> h.substring(7)
            h.startsWith("www.") -> h.substring(4)
            else -> h
        }
    }

    fun calculateCompatibilityScore(host: String): Int {
        val rule = getRuleForHost(host) ?: return 85 // Default score for unprofiled sites
        var score = 90
        if (rule.desktopSubdomainRewrite != null) score += 5
        if (rule.customCssOverrides != null) score += 5
        return score.coerceAtMost(100)
    }
}
