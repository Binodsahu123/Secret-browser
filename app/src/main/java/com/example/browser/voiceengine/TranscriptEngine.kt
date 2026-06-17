package com.example.browser.voiceengine

object TranscriptEngine {
    fun cleanLiveTranscript(text: String): String {
        return text.trim()
    }

    fun formatFeedback(actionType: String, description: String): String {
        return "Action: $actionType - $description"
    }
}
