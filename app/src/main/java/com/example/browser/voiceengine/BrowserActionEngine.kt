package com.example.browser.voiceengine

import android.content.Context
import android.util.Log
import com.example.browser.ActionRouter

class BrowserActionEngine(
    private val actionRouter: ActionRouter
) {
    fun dispatchIntent(intent: IntentEngine.ParsedIntent) {
        Log.i("BrowserActionEngine", "Dispatching ParsedIntent: ${intent.type} ('${intent.payload}')")
        try {
            actionRouter.executeIntent(intent)
        } catch (e: Exception) {
            Log.e("BrowserActionEngine", "Error routing voice command payload", e)
        }
    }
}
