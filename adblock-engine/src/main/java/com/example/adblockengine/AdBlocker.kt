package com.example.adblockengine

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object AdBlocker {
    var globalAdBlockEnabled = true
    var globalTrackersEnabled = true
    var youtubeAdSkipEnabled = true
    
    // Memory caches for domains and rules
    val whitelistedSites = ConcurrentHashMap.newKeySet<String>()
    val blockedSites = ConcurrentHashMap.newKeySet<String>()
    val dynamicRules = ConcurrentHashMap.newKeySet<String>()

    // Compiled high-speed lookups for subresource requests (O(1) lookup + suffix check)
    val dynamicBlockedHosts = ConcurrentHashMap.newKeySet<String>()
    val dynamicKeywords = ConcurrentHashMap.newKeySet<String>()
    
    private val staticAdHosts = hashSetOf(
        "doubleclick.net", "google-analytics.com", "googlesyndication.com",
        "googleadservices.com", "adservice.google.com", "googletagservices.com",
        "adsystem.com", "adservice.com", "adnxs.com", "adsymptotic.com",
        "adroll.com", "mopub.com", "rubiconproject.com", "pubmatic.com",
        "taboola.com", "outbrain.com", "adsrvr.org", "scorecardresearch"
    )

    private val staticAdKeywords = hashSetOf(
        "sponsor", "advertis", "analytics", "tracking", "appads", "adcolony",
        "unityads", "chartboost", "vungle"
    )

    fun compileRules() {
        val hosts = mutableSetOf<String>()
        val keywords = mutableSetOf<String>()
        for (rule in dynamicRules) {
            val trimmed = rule.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("||")) {
                var host = trimmed.substring(2)
                val limit = host.indexOfAny(charArrayOf('^', '/', '$', '*'))
                if (limit != -1) {
                    host = host.substring(0, limit)
                }
                host = host.trim().lowercase()
                if (host.isNotEmpty()) {
                    hosts.add(host)
                }
            } else {
                val clean = trimmed.split("$")[0].trim().lowercase()
                if (clean.isNotEmpty()) {
                    keywords.add(clean)
                }
            }
        }
        dynamicBlockedHosts.clear()
        dynamicBlockedHosts.addAll(hosts)
        dynamicKeywords.clear()
        dynamicKeywords.addAll(keywords)
    }

    fun init(context: Context) {
        // Load cached rules on startup if available
        try {
            val file = File(context.filesDir, "adblock_cached_rules.txt")
            if (file.exists()) {
                val rules = file.readLines()
                dynamicRules.addAll(rules.filter { it.isNotBlank() })
                compileRules()
            }
            
            // Load whitelisted domains from simple preferences
            val prefs = context.getSharedPreferences("orion_adblock_prefs", Context.MODE_PRIVATE)
            prefs.getStringSet("whitelist", emptySet())?.let {
                whitelistedSites.addAll(it)
            }
            prefs.getStringSet("blacklist", emptySet())?.let {
                blockedSites.addAll(it)
            }
            globalAdBlockEnabled = prefs.getBoolean("global_enabled", true)
            globalTrackersEnabled = prefs.getBoolean("trackers_enabled", true)
            youtubeAdSkipEnabled = prefs.getBoolean("youtube_skip_enabled", true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun savePreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences("orion_adblock_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putStringSet("whitelist", whitelistedSites)
                putStringSet("blacklist", blockedSites)
                putBoolean("global_enabled", globalAdBlockEnabled)
                putBoolean("trackers_enabled", globalTrackersEnabled)
                putBoolean("youtube_skip_enabled", youtubeAdSkipEnabled)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shouldBlock(url: String?, documentUrl: String?): Boolean {
        if (url == null || url.isEmpty()) return false
        
        // 1. Extract host of current web page to check per-site whitelist
        val docHost = getDomainName(documentUrl)
        if (docHost != null && whitelistedSites.contains(docHost)) {
            return false // Whitelisted site
        }

        // Check if current site is explicitly blacklisted (extra blocking)
        val isExplicitlyBlocked = docHost != null && blockedSites.contains(docHost)
        
        // If everything is turned off and not explicitly blacklisted, don't block
        if (!globalAdBlockEnabled && !globalTrackersEnabled && !isExplicitlyBlocked) {
            return false
        }

        try {
            val reqUri = Uri.parse(url)
            val reqHost = reqUri.host?.lowercase() ?: ""
            val reqPath = reqUri.path?.lowercase() ?: ""
            val fullUrlLower = url.lowercase()

            // 2. static domain checks
            for (ad in staticAdHosts) {
                if (reqHost == ad || reqHost.endsWith("." + ad)) {
                    return true
                }
            }
            for (kw in staticAdKeywords) {
                if (reqHost.contains(kw) || reqPath.contains(kw)) {
                    return true
                }
            }

            // 3. Dynamic compiled rule domain checks: O(1) hash lookups!
            if (dynamicBlockedHosts.contains(reqHost)) {
                return true
            }
            var index = reqHost.indexOf('.')
            while (index != -1) {
                val parent = reqHost.substring(index + 1)
                if (parent.isNotEmpty() && dynamicBlockedHosts.contains(parent)) {
                    return true
                }
                index = reqHost.indexOf('.', index + 1)
            }

            // 4. Dynamic keyword rules check
            if (dynamicKeywords.isNotEmpty()) {
                for (keyword in dynamicKeywords) {
                    if (fullUrlLower.contains(keyword)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return false
    }

    fun getDomainName(url: String?): String? {
        if (url == null || url.startsWith("orion://") || url == "about:blank") return null
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadBlocklists(context: Context): Int = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt"
        )
        val newRules = mutableSetOf<String>()
        
        for (urlString in urls) {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.startsWith("!") || trimmed.startsWith("[")) continue
                        
                        // Parse simple EasyList items
                        if (trimmed.startsWith("||")) {
                            val cleanRule = trimmed.split("^")[0].split("$")[0]
                            if (cleanRule.length > 5) {
                                newRules.add(cleanRule)
                            }
                        } else if (trimmed.contains("/ad") || trimmed.contains("&ad_") || trimmed.contains("/ads/")) {
                            val cleanRule = trimmed.split("$")[0]
                            if (cleanRule.length in 5..30) {
                                newRules.add(cleanRule)
                            }
                        }
                    }
                    reader.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (newRules.isNotEmpty()) {
            dynamicRules.clear()
            dynamicRules.addAll(newRules)
            compileRules()
            
            // Persist locally
            try {
                val file = File(context.filesDir, "adblock_cached_rules.txt")
                file.writeText(newRules.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dynamicRules.size
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
    }
}
