package com.example.extensionengine

import java.util.regex.Pattern

class PermissionManager {

    // Inspects whether the given active extension has authority over the specific site URL.
    // Parses standard Chrome host pattern matching:
    // - <all_urls>
    // - http://*/*
    // - *://*.domain.com/*
    fun hasHostPermission(hostPermissions: List<String>, permissions: List<String>, url: String?): Boolean {
        if (url == null || url.isBlank() || url.startsWith("about:") || url.startsWith("orion:")) {
            return false
        }

        // Host permissions can sometimes reside inside the "permissions" array in MV2
        val combinedHosts = hostPermissions + permissions.filter { isUrlPattern(it) }
        
        for (pattern in combinedHosts) {
            if (pattern == "<all_urls>" || pattern == "*://*/*") {
                return true
            }
            try {
                val compiledRegex = convertPatternToRegex(pattern)
                if (compiledRegex.matcher(url).matches()) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore invalid regex compile and proceed
            }
        }
        return false
    }

    // Checks if the extension was granted standard API permission string (e.g., "storage", "tabs").
    fun hasApiPermission(permissions: List<String>, requiredPermission: String): Boolean {
        return permissions.contains(requiredPermission)
    }

    private fun isUrlPattern(pattern: String): Boolean {
        return pattern.contains("://") || pattern == "<all_urls>" || pattern.startsWith("*:")
    }

    private fun convertPatternToRegex(pattern: String): Pattern {
        var glob = pattern
        if (glob.startsWith("*://")) {
            glob = "https?://" + glob.substring(4)
        } else {
            glob = glob.replace("http://", "http://")
            glob = glob.replace("https://", "https://")
        }
        
        // Escape standard characters while wildcarding *
        glob = glob.replace(".", "\\.")
        glob = glob.replace("*", ".*")
        glob = glob.replace("?", ".?")
        
        return Pattern.compile("^$glob", Pattern.CASE_INSENSITIVE)
    }
}
