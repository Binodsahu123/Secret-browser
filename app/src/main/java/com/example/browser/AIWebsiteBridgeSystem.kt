package com.example.browser

import android.content.Context
import android.webkit.WebView

class AIWebsiteBridgeSystem private constructor(private val context: Context) {
    private val webViews = mutableMapOf<String, WebView>()

    fun getOrCreateWebView(service: String): WebView {
        return webViews.getOrPut(service) {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(
                    when (service) {
                        "ChatGPT" -> "https://chat.openai.com"
                        "Gemini" -> "https://gemini.google.com"
                        else -> "https://google.com"
                    }
                )
            }
        }
    }

    companion object {
        @Volatile
        private var instance: AIWebsiteBridgeSystem? = null

        fun getInstance(context: Context): AIWebsiteBridgeSystem {
            return instance ?: synchronized(this) {
                instance ?: AIWebsiteBridgeSystem(context.applicationContext).also { instance = it }
            }
        }
    }
}
