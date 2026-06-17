package com.example.browser

import android.util.Log

object TranscriptManager {
    private var lastTranscript: String = ""

    fun updateTranscript(text: String) {
        lastTranscript = text
        Log.d("TranscriptManager", "Updated live transcript: $text")
    }

    fun getLastTranscript(): String {
        return lastTranscript
    }

    fun clearTranscript() {
        lastTranscript = ""
    }
}
