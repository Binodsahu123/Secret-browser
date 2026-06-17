package com.example.browser.voiceengine

import android.content.Context
import android.util.Log

class OrionHotwordEngine(
    private val context: Context,
    private val onWakeWordDetected: (String) -> Unit
) {
    fun startEngine() {
        Log.i("OrionHotwordEngine", "startEngine ignored (hotword sensing is disabled).")
    }

    fun stopEngine() {
        Log.i("OrionHotwordEngine", "stopEngine ignored.")
    }
}
