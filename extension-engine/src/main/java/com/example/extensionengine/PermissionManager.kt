package com.example.extensionengine

import android.content.Context
import java.util.regex.Pattern

class PermissionManager(private val context: Context) {

    // Inspects whether the given active extension has authority over the specific site URL.
    fun hasHostPermission(extensionId: String, hostPermissions: List<String>, permissions: List<String>, url: String?): Boolean {
        if (url == null || url.isBlank() || url.startsWith("about:") || url.startsWith("orion:") || url.startsWith("file://")) {
            return false
        }

        // Read dynamic user choices from SharedPreferences
        val prefs = context.getSharedPreferences("orion_extension_permissions", Context.MODE_PRIVATE)
        val blockedHosts = prefs.getStringSet("hosts_blocked_$extensionId", emptySet()) ?: emptySet()
        val alwaysHosts = prefs.getStringSet("hosts_always_$extensionId", emptySet()) ?: emptySet()

        // 1. If any blocked pattern matches, block immediately
        for (pattern in blockedHosts) {
            if (matchesPattern(pattern, url)) {
                return false
            }
        }

        // 2. If always allowed pattern matches, grant
        for (pattern in alwaysHosts) {
            if (matchesPattern(pattern, url)) {
                return true
            }
        }

        // 3. Fallback: check if the manifest actually declared permission for it
        val combinedHosts = hostPermissions + permissions.filter { isUrlPattern(it) }
        
        for (pattern in combinedHosts) {
            val p = pattern.trim()
            if (p == "<all_urls>" || p == "*://*/*" || p == "*://*" || p == "*" || p == "http://*/*" || p == "https://*/*") {
                return true
            }
            try {
                val compiledRegex = convertPatternToRegex(p)
                if (compiledRegex.matcher(url).matches()) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore dynamic patterns and try next
            }
        }
        return false
    }

    // Checks if the extension was granted standard API permission string (e.g., "storage", "tabs").
    fun hasApiPermission(extensionId: String, permissions: List<String>, requiredPermission: String): Boolean {
        val prefs = context.getSharedPreferences("orion_extension_permissions", Context.MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_$extensionId", emptySet()) ?: emptySet()
        if (blockedSet.contains(requiredPermission)) {
            return false
        }
        val alwaysSet = prefs.getStringSet("always_$extensionId", emptySet()) ?: emptySet()
        if (alwaysSet.contains(requiredPermission)) {
            return true
        }
        return permissions.contains(requiredPermission)
    }

    private fun isUrlPattern(pattern: String): Boolean {
        return pattern.contains("://") || pattern == "<all_urls>" || pattern.startsWith("*:")
    }

    private fun matchesPattern(pattern: String, url: String): Boolean {
        val p = pattern.trim()
        if (p == "<all_urls>" || p == "*://*/*" || p == "*://*" || p == "*" || p == "http://*/*" || p == "https://*/*") {
            return true
        }
        return try {
            val compiledRegex = convertPatternToRegex(p)
            compiledRegex.matcher(url).matches()
        } catch (e: Exception) {
            false
        }
    }

    private fun convertPatternToRegex(pattern: String): Pattern {
        var glob = pattern
        
        // Handle schemes like *:// or http:// or https://
        val schemeRegex = when {
            glob.startsWith("*://") -> {
                glob = glob.substring(4)
                "https?://"
            }
            glob.startsWith("http://") -> {
                glob = glob.substring(7)
                "http://"
            }
            glob.startsWith("https://") -> {
                glob = glob.substring(8)
                "https://"
            }
            else -> "https?://"
        }
        
        // Split host and path
        val parts = glob.split("/", limit = 2)
        var host = parts[0]
        val path = if (parts.size > 1) parts[1] else "*"
        
        // Escape host
        val escapedHost = if (host.startsWith("*.")) {
            val domain = host.substring(2).replace(".", "\\.")
            "(.*\\.)?$domain"
        } else {
            host.replace(".", "\\.").replace("*", ".*")
        }
        
        // Escape path
        val escapedPath = path.replace(".", "\\.")
                              .replace("*", ".*")
                              .replace("?", "\\?")
        
        val fullRegex = "^$schemeRegex$escapedHost/$escapedPath$"
        return Pattern.compile(fullRegex, Pattern.CASE_INSENSITIVE)
    }
}
