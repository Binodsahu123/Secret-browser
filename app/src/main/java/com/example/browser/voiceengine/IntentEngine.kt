package com.example.browser.voiceengine

object IntentEngine {
    enum class IntentType {
        OPEN_YOUTUBE,
        OPEN_GOOGLE,
        OPEN_GMAIL,
        OPEN_DOWNLOADS,
        OPEN_HISTORY,
        OPEN_SETTINGS,
        OPEN_NEW_TAB,
        OPEN_INCOGNITO,
        CLOSE_CURRENT_TAB,
        RESTORE_LAST_TAB,
        SEARCH_WEB,
        UNKNOWN
    }

    data class ParsedIntent(
        val type: IntentType,
        val payload: String = ""
    )

    fun determineIntent(speechText: String): ParsedIntent {
        val clean = speechText.trim().lowercase()
            .removePrefix("hello orion").removePrefix("hey orion").removePrefix("orion assistant").removePrefix("orion").trim()
        
        return when {
            clean.contains("open youtube") || clean == "youtube" -> 
                ParsedIntent(IntentType.OPEN_YOUTUBE)
                
            clean.contains("open google") || clean == "google" -> 
                ParsedIntent(IntentType.OPEN_GOOGLE)

            clean.contains("open gmail") || clean == "gmail" -> 
                ParsedIntent(IntentType.OPEN_GMAIL)
                
            clean.contains("open downloads") || clean.contains("show downloads") || clean == "downloads" -> 
                ParsedIntent(IntentType.OPEN_DOWNLOADS)
                
            clean.contains("open history") || clean.contains("show history") || clean == "history" -> 
                ParsedIntent(IntentType.OPEN_HISTORY)
                
            clean.contains("open settings") || clean == "settings" -> 
                ParsedIntent(IntentType.OPEN_SETTINGS)
                
            clean.contains("open new tab") || clean.contains("create new tab") || clean.contains("new tab") -> 
                ParsedIntent(IntentType.OPEN_NEW_TAB)
                
            clean.contains("open incognito") || clean.contains("create incognito") || clean.contains("incognito") -> 
                ParsedIntent(IntentType.OPEN_INCOGNITO)
                
            clean.contains("close current tab") || clean.contains("close tab") -> 
                ParsedIntent(IntentType.CLOSE_CURRENT_TAB)

            clean.contains("restore last tab") || clean.contains("restore tab") || clean.contains("reopen last tab") -> 
                ParsedIntent(IntentType.RESTORE_LAST_TAB)

            clean.startsWith("search ") || clean.startsWith("search web ") -> {
                val query = clean.removePrefix("search web").removePrefix("search").trim()
                ParsedIntent(IntentType.SEARCH_WEB, query)
            }
            
            else -> {
                if (clean.length > 2) {
                    ParsedIntent(IntentType.SEARCH_WEB, clean)
                } else {
                    ParsedIntent(IntentType.UNKNOWN, clean)
                }
            }
        }
    }
}
