package com.example.browser

import android.content.Context
import android.util.Log
import com.example.browser.voiceengine.IntentEngine

class CommandRouter(
    private val context: Context,
    private val actionRouter: ActionRouter
) {
    fun routeCommand(rawText: String) {
        Log.i("CommandRouter", "Parsing and routing raw voice input: $rawText")
        
        // Match the intent
        val parsedIntent = IntentEngine.determineIntent(rawText)
        
        // Log locally to history
        VoiceHistoryManager.addEntry(context, rawText, "command")
        
        // Execute the routing
        actionRouter.executeIntent(parsedIntent)
    }
}
