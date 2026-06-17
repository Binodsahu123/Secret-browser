package com.example.browser

import android.util.Log
import com.example.searchengine.VoiceActionType
import com.example.searchengine.VoiceCommandResult
import com.example.browser.voiceengine.IntentEngine


class ActionRouter(private val viewModel: BrowserViewModel) {

    fun executeIntent(intent: IntentEngine.ParsedIntent) {
        Log.i("ActionRouter", "Routing custom voice intent: ${intent.type} ('${intent.payload}')")
        viewModel.lastVoiceCommandType = intent.type.name
        
        val result = when (intent.type) {
            IntentEngine.IntentType.SEARCH_YOUTUBE -> {
                val query = intent.payload
                val url = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
                VoiceCommandResult(VoiceActionType.NAVIGATE, url)
            }
            IntentEngine.IntentType.PLAY_MUSIC -> {
                val query = intent.payload
                if (query.isNotEmpty()) {
                    val url = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
                    VoiceCommandResult(VoiceActionType.NAVIGATE, url)
                } else {
                    viewModel.conversationalFollowUpType = "music"
                    viewModel.playVoiceAssistantResponseSpoken("Sure. What type of music would you like to listen to?") {
                        viewModel.startOrionVoiceListening(viewModel.getApplication())
                    }
                    return
                }
            }
            IntentEngine.IntentType.READ_NEWS -> {
                VoiceCommandResult(VoiceActionType.SEARCH, "latest news")
            }
            IntentEngine.IntentType.OPEN_YOUTUBE -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://youtube.com")
            }
            IntentEngine.IntentType.OPEN_GOOGLE -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://google.com")
            }
            IntentEngine.IntentType.OPEN_GMAIL -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://gmail.com")
            }
            IntentEngine.IntentType.OPEN_FACEBOOK -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://facebook.com")
            }
            IntentEngine.IntentType.OPEN_INSTAGRAM -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://instagram.com")
            }
            IntentEngine.IntentType.OPEN_X -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://x.com")
            }
            IntentEngine.IntentType.OPEN_CHATGPT -> {
                VoiceCommandResult(VoiceActionType.NAVIGATE, "https://chatgpt.com")
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
            IntentEngine.IntentType.TRANSLATE_PAGE -> {
                VoiceCommandResult(VoiceActionType.TRANSLATE_PAGE, "Hindi")
            }
            IntentEngine.IntentType.SUMMARIZE_PAGE -> {
                VoiceCommandResult(VoiceActionType.SUMMARIZE_PAGE)
            }
            IntentEngine.IntentType.READ_THIS_PAGE -> {
                VoiceCommandResult(VoiceActionType.READ_PAGE_ALOUD)
            }
            IntentEngine.IntentType.SEARCH_ANDROID_NEWS -> {
                VoiceCommandResult(VoiceActionType.SEARCH, "Android news")
            }
            IntentEngine.IntentType.SEARCH_AI_NEWS -> {
                VoiceCommandResult(VoiceActionType.SEARCH, "AI news")
            }
            IntentEngine.IntentType.SEARCH_GOOGLE -> {
                val resolvedUrl = VoiceCommandDatabase.resolveQueryToUrl(intent.payload)
                if (resolvedUrl != null) {
                    VoiceCommandResult(VoiceActionType.NAVIGATE, resolvedUrl)
                } else {
                    VoiceCommandResult(VoiceActionType.SEARCH, intent.payload)
                }
            }
            else -> {
                // Determine fallbacks
                val rawText = intent.payload.lowercase().trim()
                val resolvedUrl = VoiceCommandDatabase.resolveQueryToUrl(rawText)
                if (resolvedUrl != null) {
                    VoiceCommandResult(VoiceActionType.NAVIGATE, resolvedUrl)
                } else {
                    when {
                        rawText.contains("instagram") -> {
                            VoiceCommandResult(VoiceActionType.NAVIGATE, "https://instagram.com")
                        }
                        rawText.contains("open x") || rawText == "x" || rawText.contains("twitter") -> {
                            VoiceCommandResult(VoiceActionType.NAVIGATE, "https://x.com")
                        }
                        rawText.contains("restore last") || rawText.contains("reopen last") -> {
                            VoiceCommandResult(VoiceActionType.OPEN_LAST_CLOSED_TAB)
                        }
                        rawText.contains("translate") -> {
                            VoiceCommandResult(VoiceActionType.TRANSLATE_PAGE, "Hindi")
                        }
                        rawText.contains("summarize") -> {
                            VoiceCommandResult(VoiceActionType.SUMMARIZE_PAGE)
                        }
                        rawText.contains("read") || rawText.contains("speak") -> {
                            VoiceCommandResult(VoiceActionType.READ_PAGE_ALOUD)
                        }
                        else -> {
                            if (rawText.isNotEmpty()) {
                                if (rawText.contains(".") && !rawText.contains(" ")) {
                                    val url = if (!rawText.startsWith("http")) "https://$rawText" else rawText
                                    VoiceCommandResult(VoiceActionType.NAVIGATE, url)
                                } else {
                                    VoiceCommandResult(VoiceActionType.SEARCH, intent.payload)
                                }
                            } else {
                                VoiceCommandResult(VoiceActionType.SEARCH, "Android news")
                            }
                        }
                    }
                }
            }
        }

        viewModel.executeVoiceCommandResult(result)
    }
}
