package com.example.browser

import android.net.Uri

object UrlRewriteEngine {
    fun rewriteToDesktop(url: String): String {
        if (url.isBlank() || url.startsWith("orion://") || url.startsWith("about:") || url.startsWith("file:")) {
            return url
        }
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return url
            val newHost = when {
                host.equals("m.youtube.com", ignoreCase = true) -> "www.youtube.com"
                host.equals("m.facebook.com", ignoreCase = true) -> "www.facebook.com"
                host.equals("m.reddit.com", ignoreCase = true) -> "www.reddit.com"
                host.equals("mobile.twitter.com", ignoreCase = true) || 
                host.equals("m.twitter.com", ignoreCase = true) || 
                host.equals("mobile.x.com", ignoreCase = true) || 
                host.equals("m.x.com", ignoreCase = true) -> "x.com"
                host.contains(".m.wikipedia.org", ignoreCase = true) -> host.replace(".m.wikipedia.org", ".wikipedia.org", ignoreCase = true)
                else -> null
            }
            if (newHost != null && newHost != host) {
                return uri.buildUpon().authority(newHost).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return url
    }

    fun rewriteToMobile(url: String): String {
        if (url.isBlank() || url.startsWith("orion://") || url.startsWith("about:") || url.startsWith("file:")) {
            return url
        }
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return url
            val newHost = when {
                (host.equals("youtube.com", ignoreCase = true) || host.equals("www.youtube.com", ignoreCase = true)) && 
                !uri.path.orEmpty().contains("embed") -> "m.youtube.com"
                host.equals("facebook.com", ignoreCase = true) || host.equals("www.facebook.com", ignoreCase = true) -> "m.facebook.com"
                host.equals("reddit.com", ignoreCase = true) || host.equals("www.reddit.com", ignoreCase = true) -> "m.reddit.com"
                host.equals("twitter.com", ignoreCase = true) || host.equals("x.com", ignoreCase = true) || host.equals("www.x.com", ignoreCase = true) -> "mobile.twitter.com"
                host.contains("wikipedia.org", ignoreCase = true) && !host.contains(".m.wikipedia.org", ignoreCase = true) -> {
                    if (host.equals("wikipedia.org", ignoreCase = true)) {
                        "m.wikipedia.org"
                    } else {
                        val parts = host.split(".")
                        if (parts.size == 3 && !parts[0].equals("www", ignoreCase = true) && parts[1].equals("wikipedia", ignoreCase = true)) {
                            "${parts[0]}.m.wikipedia.org"
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }
            if (newHost != null && newHost != host) {
                return uri.buildUpon().authority(newHost).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return url
    }
}
