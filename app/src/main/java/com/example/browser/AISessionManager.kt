package com.example.browser

object AISessionManager {
    data class AISession(val rawPageText: String = "")

    private val sessions = mutableMapOf<String, AISession>()

    fun getSession(tabId: String): AISession? {
        return sessions[tabId]
    }

    fun updateSession(tabId: String, text: String) {
        sessions[tabId] = AISession(text)
    }
}
