package com.example.browser.voiceengine

import android.util.Log
import com.example.browser.BrowserViewModel
import com.example.searchengine.VoiceActionType
import com.example.searchengine.VoiceCommandResult

class ActionRouter(private val viewModel: BrowserViewModel) {

    fun executeIntent(intent: IntentEngine.ParsedIntent) {
        Log.i("ActionRouterPrivate", "Routing voice intent: ${intent.type} ('${intent.payload}')")
        
        // Map to VoiceCommandResult
        val result = when (intent.type) {
            IntentEngine.IntentType.OPEN_YOUTUBE -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://youtube.com")
            }
            IntentEngine.IntentType.OPEN_GOOGLE -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://google.com")
            }
            IntentEngine.IntentType.OPEN_GMAIL -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://gmail.com")
            }
            IntentEngine.IntentType.OPEN_DOWNLOADS -> {
                VoiceCommandResult(VoiceActionType.OPEN_DOWNLOADS)
            }
            IntentEngine.IntentType.OPEN_HISTORY -> {
                VoiceCommandResult(VoiceActionType.OPEN_HISTORY)
            }
            IntentEngine.IntentType.OPEN_SETTINGS -> {
                VoiceCommandResult(VoiceActionType.OPEN_SETTINGS)
            }
            IntentEngine.IntentType.OPEN_NEW_TAB -> {
                VoiceCommandResult(VoiceActionType.NEW_TAB)
            }
            IntentEngine.IntentType.OPEN_INCOGNITO -> {
                VoiceCommandResult(VoiceActionType.OPEN_INCOGNITO)
            }
            IntentEngine.IntentType.CLOSE_CURRENT_TAB -> {
                VoiceCommandResult(VoiceActionType.CLOSE_TAB)
            }
            IntentEngine.IntentType.RESTORE_LAST_TAB -> {
                VoiceCommandResult(VoiceActionType.OPEN_LAST_CLOSED_TAB)
            }
            IntentEngine.IntentType.SEARCH_WEB -> {
                VoiceCommandResult(VoiceActionType.SEARCH, intent.payload)
            }
            else -> {
                VoiceCommandResult(VoiceActionType.SEARCH, intent.payload)
            }
        }

        viewModel.executeVoiceCommandResult(result)
    }
}
