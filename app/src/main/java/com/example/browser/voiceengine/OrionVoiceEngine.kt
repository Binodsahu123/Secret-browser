package com.example.browser.voiceengine

import android.content.Context
import android.util.Log
import com.example.browser.ActionRouter
import com.example.browser.BrowserViewModel
import com.example.browser.VoiceHistoryEntry
import com.example.searchengine.VoiceActionType
import com.example.searchengine.VoiceCommandResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ORION COHESIVE UNIFIED VOICE SYSTEM
 * Coordinates and manages all voice features, wake-word detection, active speech recognition,
 * intent parsing, and execution in a modular single entry point.
 */
class OrionVoiceEngine(
    private val context: Context,
    private val viewModel: BrowserViewModel? = null
) {
    // Single source of truth for action routing
    private val actionRouter = viewModel?.let { ActionRouter(it) }

    // Coordinated modules
    val wakeWordModule = WakeWordModule()
    val speechRecognitionModule = SpeechRecognitionModule()
    val transcriptModule = TranscriptModule()
    val intentModule = IntentModule()
    val commandModule = CommandModule()
    val browserActionModule = BrowserActionModule()
    val tabControlModule = TabControlModule()
    val downloadControlModule = DownloadControlModule()
    val historySearchModule = HistorySearchModule()
    val bookmarkModule = BookmarkModule()
    val aiAssistantModule = AIAssistantModule()
    val translationModule = TranslationModule()
    val readerModule = ReaderModule()
    val voiceResponseModule = VoiceResponseModule()
    val sessionModule = SessionModule()
    val permissionModule = PermissionModule()
    val analyticsModule = AnalyticsModule()
    val timeoutModule = TimeoutModule()
    val recoveryModule = RecoveryModule()

    // 1. WakeWordModule
    inner class WakeWordModule {
        private var wakeWordEngine: WakeWordEngine? = null

        fun startListening(onWakeDetected: (String?) -> Unit) {
            Log.i("OrionVoiceEngine", "WakeWordModule: Starting wake-phrase sensing (Hello/Hey Orion)")
            stopListening()
            wakeWordEngine = WakeWordEngine(context) { command ->
                onWakeDetected(command)
            }
            wakeWordEngine?.startListening()
        }

        fun stopListening() {
            Log.i("OrionVoiceEngine", "WakeWordModule: Stopping wake-phrase sensing")
            wakeWordEngine?.stopListening()
            wakeWordEngine = null
        }
    }

    // 2. SpeechRecognitionModule
    inner class SpeechRecognitionModule {
        private var activeEngine: SpeechRecognitionEngine? = null

        fun startListening(
            languageCode: String,
            onReady: () -> Unit,
            onHearing: () -> Unit,
            onVolumeChanged: (Float) -> Unit,
            onPartial: (String) -> Unit,
            onResult: (String) -> Unit,
            onErrorOccurred: (String, Int) -> Unit
        ) {
            Log.i("OrionVoiceEngine", "SpeechRecognitionModule: Triggering active speech listener.")
            stopListening()
            activeEngine = SpeechRecognitionEngine(
                context = context,
                onReady = onReady,
                onHearing = onHearing,
                onVolumeChanged = onVolumeChanged,
                onPartial = onPartial,
                onResult = onResult,
                onErrorOccurred = onErrorOccurred
            )
            activeEngine?.startListening(languageCode)
        }

        fun stopListening() {
            Log.i("OrionVoiceEngine", "SpeechRecognitionModule: Stopping active listener")
            activeEngine?.stopListening()
            activeEngine = null
        }
    }

    // 3. TranscriptModule
    inner class TranscriptModule {
        fun cleanTranscript(text: String): String {
            return TranscriptEngine.cleanLiveTranscript(text)
        }

        fun formatFeedback(actionType: String, description: String): String {
            return TranscriptEngine.formatFeedback(actionType, description)
        }
    }

    // 4. IntentModule
    inner class IntentModule {
        fun determineIntent(speechText: String): IntentEngine.ParsedIntent {
            return IntentEngine.determineIntent(speechText)
        }
    }

    // 5. CommandModule
    inner class CommandModule {
        fun routeCommand(speechText: String): Boolean {
            Log.i("OrionVoiceEngine", "CommandModule: Routing command to IntentParser: $speechText")
            val parsedIntent = intentModule.determineIntent(speechText)
            analyticsModule.logCommandMetric("command", parsedIntent.type.name)
            
            if (parsedIntent.type != IntentEngine.IntentType.UNKNOWN) {
                browserActionModule.dispatchIntent(parsedIntent)
                return true
            }
            return false
        }
    }

    // 6. BrowserActionModule
    inner class BrowserActionModule {
        private val browserActionEngine = actionRouter?.let { BrowserActionEngine(it) }

        fun dispatchIntent(intent: IntentEngine.ParsedIntent) {
            Log.i("OrionVoiceEngine", "BrowserActionModule: Dispatching parsed intent: ${intent.type}")
            browserActionEngine?.dispatchIntent(intent)
        }
    }

    // 7. TabControlModule
    inner class TabControlModule {
        fun openNewNormalTab() {
            Log.i("OrionVoiceEngine", "TabControlModule: Executing open normal tab")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.NEW_TAB))
        }

        fun openNewIncognitoTab() {
            Log.i("OrionVoiceEngine", "TabControlModule: Executing open incognito tab")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.OPEN_INCOGNITO))
        }

        fun closeCurrentTab() {
            Log.i("OrionVoiceEngine", "TabControlModule: Executing close current tab")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.CLOSE_TAB))
        }

        fun restoreLastClosedTab() {
            Log.i("OrionVoiceEngine", "TabControlModule: Executing restore last tab")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.OPEN_LAST_CLOSED_TAB))
        }
    }

    // 8. DownloadControlModule
    inner class DownloadControlModule {
        fun openDownloadsHub() {
            Log.i("OrionVoiceEngine", "DownloadControlModule: Triggering open downloads hub")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.OPEN_DOWNLOADS))
        }
    }

    // 9. HistorySearchModule
    inner class HistorySearchModule {
        fun openHistoryHub() {
            Log.i("OrionVoiceEngine", "HistorySearchModule: Triggering open history hub")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.OPEN_HISTORY))
        }
    }

    // 10. BookmarkModule
    inner class BookmarkModule {
        fun openBookmarksHub() {
            Log.i("OrionVoiceEngine", "BookmarkModule: Triggering open bookmarks hub")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.OPEN_BOOKMARKS))
        }
    }

    // 11. AIAssistantModule
    inner class AIAssistantModule {
        fun runPageSummary() {
            Log.i("OrionVoiceEngine", "AIAssistantModule: Compiling brief page summary")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.SUMMARIZE_PAGE))
        }

        fun runPageExplanation() {
            Log.i("OrionVoiceEngine", "AIAssistantModule: Compiling conversational page explanation")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.EXPLAIN_PAGE))
        }
    }

    // 12. TranslationModule
    inner class TranslationModule {
        fun translateCurrentPage(targetLanguage: String) {
            Log.i("OrionVoiceEngine", "TranslationModule: Initiated webpage translation to: $targetLanguage")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.TRANSLATE_PAGE, targetLanguage))
        }
    }

    // 13. ReaderModule
    inner class ReaderModule {
        fun readPageAloud() {
            Log.i("OrionVoiceEngine", "ReaderModule: Reading page text content aloud")
            viewModel?.executeVoiceCommandResult(VoiceCommandResult(VoiceActionType.READ_PAGE_ALOUD))
        }
    }

    // 14. VoiceResponseModule
    inner class VoiceResponseModule {
        fun speakVoiceResponse(spokenText: String, onDone: (() -> Unit)? = null) {
            Log.i("OrionVoiceEngine", "VoiceResponseModule: Spoken voice output: $spokenText")
            viewModel?.playVoiceAssistantResponseSpoken(spokenText, onDone)
        }
    }

    // 15. SessionModule
    inner class SessionModule {
        var isActiveSession: Boolean
            get() = viewModel?.isActiveSessionRunning ?: false
            set(value) {
                viewModel?.isActiveSessionRunning = value
            }

        fun prolongSessionTime(durationMs: Long = 60000L) {
            Log.d("OrionVoiceEngine", "SessionModule: Session prolonged.")
            viewModel?.sessionEndTime = System.currentTimeMillis() + durationMs
        }
    }

    // 16. PermissionModule
    inner class PermissionModule {
        fun checkRecordAudioPermission(): Boolean {
            val res = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            )
            return res == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // 17. AnalyticsModule
    inner class AnalyticsModule {
        fun logCommandMetric(eventType: String, commandType: String) {
            Log.i("OrionVoiceEngine", "AnalyticsModule: Log event $eventType -> command: $commandType")
            viewModel?.lastVoiceCommandType = commandType
        }
    }

    // 18. TimeoutModule
    inner class TimeoutModule {
        private var watchdogJob: Job? = null

        fun startWatchdog(onTimeout: () -> Unit) {
            watchdogJob?.cancel()
            sessionModule.prolongSessionTime(60000L)
            val vm = viewModel ?: return
            watchdogJob = vm.viewModelScope.launch(Dispatchers.Main) {
                while (sessionModule.isActiveSession) {
                    delay(1000L)
                    if (System.currentTimeMillis() > (viewModel?.sessionEndTime ?: 0L)) {
                        Log.i("OrionVoiceEngine", "TimeoutModule: Active listening session idle-timeout reached.")
                        onTimeout()
                        break
                    }
                }
            }
        }

        fun stopWatchdog() {
            watchdogJob?.cancel()
            watchdogJob = null
        }
    }

    // 19. RecoveryModule
    inner class RecoveryModule {
        fun processErrorAndAttemptRecovery(errorMessage: String, errorCode: Int, onRecover: () -> Unit) {
            Log.e("OrionVoiceEngine", "RecoveryModule: Captured error ($errorCode): $errorMessage")
            val vm = viewModel ?: return
            val sessionTime = viewModel?.sessionEndTime ?: 0L
            if (sessionModule.isActiveSession && System.currentTimeMillis() < sessionTime) {
                Log.i("OrionVoiceEngine", "RecoveryModule: Inside timeout window, restarting active listener.")
                vm.viewModelScope.launch(Dispatchers.Main) {
                    delay(600L)
                    if (sessionModule.isActiveSession) {
                        onRecover()
                    }
                }
            }
        }
    }
}
