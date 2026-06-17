package com.example.browser

object IntentEngine {
    enum class IntentType {
        OPEN_YOUTUBE,
        OPEN_GOOGLE,
        OPEN_GMAIL,
        OPEN_FACEBOOK,
        OPEN_INSTAGRAM,
        OPEN_X,
        OPEN_CHATGPT,
        OPEN_DOWNLOADS,
        OPEN_HISTORY,
        OPEN_SETTINGS,
        OPEN_NEW_TAB,
        OPEN_INCOGNITO,
        CLOSE_CURRENT_TAB,
        RESTORE_LAST_TAB,
        SEARCH_WEB,
        TRANSLATE_PAGE,
        SUMMARIZE_PAGE,
        READ_THIS_PAGE,
        SEARCH_ANDROID_NEWS,
        SEARCH_AI_NEWS,
        SEARCH_GOOGLE,
        SEARCH_YOUTUBE,
        PLAY_MUSIC,
        READ_NEWS,
        UNKNOWN
    }

    data class ParsedIntent(
        val type: IntentType,
        val payload: String = ""
    )

    fun determineIntent(speechText: String): ParsedIntent {
        val clean = speechText.trim().lowercase()
            .removePrefix("hello orion").removePrefix("hey orion").removePrefix("orion").trim()
        
        return when {
            clean.contains("search youtube") -> {
                val query = clean.replace("search youtube", "").trim()
                ParsedIntent(IntentType.SEARCH_YOUTUBE, query)
            }
            clean.contains("play music on youtube") -> {
                val query = clean.replace("play music on youtube", "").trim()
                ParsedIntent(IntentType.PLAY_MUSIC, query)
            }
            clean.contains("play music") -> {
                val query = clean.replace("play music", "").trim()
                ParsedIntent(IntentType.PLAY_MUSIC, query)
            }
            clean.contains("read news") || clean.contains("show news") || clean == "news" -> 
                ParsedIntent(IntentType.READ_NEWS)

            clean.contains("open youtube") || clean == "youtube" -> 
                ParsedIntent(IntentType.OPEN_YOUTUBE)
                
            clean.contains("open google") || clean == "google" -> 
                ParsedIntent(IntentType.OPEN_GOOGLE)

            clean.contains("open gmail") || clean == "gmail" -> 
                ParsedIntent(IntentType.OPEN_GMAIL)
                
            clean.contains("open facebook") || clean == "facebook" -> 
                ParsedIntent(IntentType.OPEN_FACEBOOK)

            clean.contains("open instagram") || clean == "instagram" -> 
                ParsedIntent(IntentType.OPEN_INSTAGRAM)

            clean.contains("open x") || clean == "x" || clean.contains("open twitter") -> 
                ParsedIntent(IntentType.OPEN_X)
                
            clean.contains("open chatgpt") || clean == "chatgpt" -> 
                ParsedIntent(IntentType.OPEN_CHATGPT)
                
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

            clean.contains("translate page") || clean.contains("translate this page") -> 
                ParsedIntent(IntentType.TRANSLATE_PAGE)

            clean.contains("summarize page") || clean.contains("summarize this page") -> 
                ParsedIntent(IntentType.SUMMARIZE_PAGE)

            clean.contains("read this page") || clean.contains("read page") || clean.contains("speak page") -> 
                ParsedIntent(IntentType.READ_THIS_PAGE)
                
            clean.contains("search android browser news") || clean.contains("search android news") || clean.contains("android news") -> 
                ParsedIntent(IntentType.SEARCH_ANDROID_NEWS, "Android browser news")
                
            clean.contains("search ai news") || clean.contains("ai news") -> 
                ParsedIntent(IntentType.SEARCH_AI_NEWS, "AI news")

            clean.startsWith("search ") || clean.startsWith("search web ") -> {
                val query = clean.removePrefix("search web").removePrefix("search").trim()
                ParsedIntent(IntentType.SEARCH_GOOGLE, query)
            }
            
            else -> ParsedIntent(IntentType.UNKNOWN, clean)
        }
    }
}
