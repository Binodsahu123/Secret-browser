package com.example.searchengine

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import java.net.URLEncoder

interface SearchEngine {
    fun buildSearchUrl(query: String, engineName: String = "Google"): String
}

class SearchEngineImpl : SearchEngine {
    override fun buildSearchUrl(query: String, engineName: String): String {
        val trimmed = query.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return when (engineName.lowercase()) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "yahoo" -> "https://search.yahoo.com/search?p=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }
}

class SearchSuggestions {
    fun getSuggestions(query: String): List<String> {
        return if (query.isEmpty()) emptyList() else listOf(
            "$query news",
            "$query weather",
            "$query definition",
            "$query map"
        )
    }
}

class VoiceSearch {
    fun getVoiceSearchIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
    }
}
