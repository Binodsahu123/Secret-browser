package com.example.browser.voiceengine

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
        val raw = speechText.trim().lowercase()
        
        // Clean wake words
        val clean = raw
            .replace("hello orion", "")
            .replace("hey orion", "")
            .replace("orion assistant", "")
            .replace("orion", "")
            .replace("ओरियन", "")
            .trim()

        if (clean.isEmpty()) {
            return ParsedIntent(IntentType.UNKNOWN, "")
        }

        return when {
            // Youtube opening
            clean.contains("open youtube") || clean == "youtube" || 
            clean.contains("youtube खोलो") || clean.contains("youtube चालू") || 
            clean.contains("यूट्यूब खोलो") || clean.contains("यूट्यूब") -> 
                ParsedIntent(IntentType.OPEN_YOUTUBE)

            // Google opening
            clean.contains("open google") || clean == "google" || 
            clean.contains("google खोलो") || clean.contains("गूगल") -> 
                ParsedIntent(IntentType.OPEN_GOOGLE)

            // Gmail opening
            clean.contains("open gmail") || clean == "gmail" || 
            clean.contains("जीमेल खोलो") || clean.contains("जीमेल") -> 
                ParsedIntent(IntentType.OPEN_GMAIL)

            // Instagram opening
            clean.contains("open instagram") || clean == "instagram" || 
            clean.contains("इंस्टाग्राम खोलो") -> 
                ParsedIntent(IntentType.OPEN_INSTAGRAM)

            // Downloads opening
            clean.contains("open downloads") || clean.contains("show downloads") || clean == "downloads" || 
            clean.contains("download दिखाओ") || clean.contains("डाउनलोड दिखाओ") || clean.contains("डाउनलोड") -> 
                ParsedIntent(IntentType.OPEN_DOWNLOADS)

            // History opening
            clean.contains("open history") || clean.contains("show history") || clean == "history" || 
            clean.contains("history दिखाओ") || clean.contains("इतिहास दिखाओ") || clean.contains("इतिहास") -> 
                ParsedIntent(IntentType.OPEN_HISTORY)

            // Settings opening
            clean.contains("open settings") || clean == "settings" || 
            clean.contains("settings खोलो") || clean.contains("सेटिंग खोलो") || clean.contains("सेटिंग") -> 
                ParsedIntent(IntentType.OPEN_SETTINGS)

            // New Tab
            clean.contains("open new tab") || clean.contains("create new tab") || clean.contains("new tab") || 
            clean.contains("नया टैब") || clean.contains("नया टैब खोलो") || clean.contains("नया टैब चालू") -> 
                ParsedIntent(IntentType.OPEN_NEW_TAB)

            // Incognito Tab
            clean.contains("open incognito") || clean.contains("create incognito") || clean.contains("incognito") || 
            clean.contains("प्राइवेट टैब") || clean.contains("प्राइवेट विंडो") || clean.contains("इन्कॉग्निटो") -> 
                ParsedIntent(IntentType.OPEN_INCOGNITO)

            // Close Tab
            clean.contains("close current tab") || clean.contains("close tab") || 
            clean.contains("टैब बंद करो") || clean.contains("tab बंद करो") || clean.contains("बंद करो") -> 
                ParsedIntent(IntentType.CLOSE_CURRENT_TAB)

            // Restore/Reopen Tab
            clean.contains("restore last tab") || clean.contains("restore tab") || clean.contains("reopen last tab") || 
            clean.contains("पुराना टैब") || clean.contains("पिछला टैब") || clean.contains("पुराना टैब वापस") -> 
                ParsedIntent(IntentType.RESTORE_LAST_TAB)

            // Summarize Page
            clean.contains("summarize") || clean.contains("summary") || 
            clean.contains("सारांश") || clean.contains("शॉर्ट में बताओ") || clean.contains("पेज का शार्ट") -> 
                ParsedIntent(IntentType.SUMMARIZE_PAGE)

            // Translate Page
            clean.contains("translate") || clean.contains("अनुवाद") || 
            clean.contains("हिंदी करो") || clean.contains("ट्रांसलेट") -> 
                ParsedIntent(IntentType.TRANSLATE_PAGE, "Hindi")

            // Read aloud
            clean.contains("read aloud") || clean.contains("read this page") || clean.contains("read page") || 
            clean.contains("पढ़कर सुनाओ") || clean.contains("पेज पढ़ो") || clean.contains("बोलकर सुनाओ") || clean.contains("पढ़ो") -> 
                ParsedIntent(IntentType.READ_THIS_PAGE)

            // Search Android News
            clean.contains("search android news") || clean.contains("android news") || 
            clean.contains("एंड्रॉयड न्यूज़") || clean.contains("एंड्रॉयड समाचार") -> 
                ParsedIntent(IntentType.SEARCH_ANDROID_NEWS, "Android news")

            // Search AI News
            clean.contains("search ai news") || clean.contains("ai news") || 
            clean.contains("एआई न्यूज़") || clean.contains("आर्टिफिशियल इंटेलिजेंस") -> 
                ParsedIntent(IntentType.SEARCH_AI_NEWS, "AI news")

            // Search queries starting with explicit verbs
            clean.startsWith("search ") || clean.startsWith("search web ") || clean.startsWith("सर्च करो ") || clean.startsWith("खोजो ") -> {
                val query = clean
                    .removePrefix("search web")
                    .removePrefix("search")
                    .removePrefix("सर्च करो")
                    .removePrefix("खोजो")
                    .trim()
                ParsedIntent(IntentType.SEARCH_GOOGLE, query)
            }

            // Fallback: If it's a long phrase, threat as Search Google
            else -> {
                if (clean.length > 2) {
                    ParsedIntent(IntentType.SEARCH_GOOGLE, clean)
                } else {
                    ParsedIntent(IntentType.UNKNOWN, clean)
                }
            }
        }
    }
}

