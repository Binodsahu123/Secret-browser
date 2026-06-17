package com.example.browser.voiceengine

import android.content.Context
import android.util.Log

class WakeWordEngine(
    private val context: Context,
    private val onWakeDetected: (String?) -> Unit
) {
    fun startListening() {
        Log.i("WakeWordEngine", "startListening ignored.")
    }

    fun stopListening() {
        Log.i("WakeWordEngine", "stopListening ignored.")
    }
}
