package com.example.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale

class OrionSpeechBridge(private val webView: WebView, private val viewModel: BrowserViewModel) {

    private val context: Context = webView.context
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeId: String? = null

    @JavascriptInterface
    fun startSpeech(id: String, lang: String, continuous: Boolean, interimResults: Boolean) {
        webView.post {
            try {
                // Cancel previous active recognition if any
                cancelSpeechSilent()

                activeId = id

                // Check if speech services are available
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    triggerJsError(id, "service-not-allowed")
                    triggerJsEnd(id)
                    return@post
                }

                // Check RECORD_AUDIO permission at system level
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val pendingObj = BrowserViewModel.PendingSpeechRequest(
                        id = id,
                        lang = lang,
                        continuous = continuous,
                        interimResults = interimResults,
                        bridge = this
                    )
                    viewModel.setPendingSpeechRequest(pendingObj)
                    return@post
                }

                startSpeechWithPermission(id, lang, continuous, interimResults)

            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                triggerJsError(id, "unknown")
                triggerJsEnd(id)
                cleanup()
            }
        }
    }

    fun startSpeechWithPermission(id: String, lang: String, continuous: Boolean, interimResults: Boolean) {
        webView.post {
            try {
                // Completely clean up and destroy any previous instance to prevent ERROR_RECOGNIZER_BUSY or client-stuck state
                cleanup()

                activeId = id

                // Re-create a fresh SpeechRecognizer instance for this specific session.
                // We proactively check if Google's standard voice service is available in PackageManager. If yes, we bind to it; otherwise we fall back safely to system default.
                val recognizer = try {
                    val packageName = "com.google.android.googlequicksearchbox"
                    val className = "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
                    val checkIntent = Intent(android.speech.RecognitionService.SERVICE_INTERFACE).apply {
                        setClassName(packageName, className)
                    }
                    val list = context.packageManager.queryIntentServices(checkIntent, 0)
                    val isGoogleAvailable = !list.isNullOrEmpty()

                    if (isGoogleAvailable) {
                        SpeechRecognizer.createSpeechRecognizer(
                            context,
                            android.content.ComponentName(packageName, className)
                        )
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } catch (e: Exception) {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }.also {
                    speechRecognizer = it
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                    val locale = if (lang.isNotEmpty()) {
                        try {
                            Locale.forLanguageTag(lang)
                        } catch (e: Exception) {
                            Locale.getDefault()
                        }
                    } else {
                        Locale.getDefault()
                    }
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())

                    if (interimResults) {
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        triggerJsStart(id)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val errorType = when (error) {
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                            SpeechRecognizer.ERROR_NETWORK -> "network"
                            SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                            SpeechRecognizer.ERROR_CLIENT -> "aborted"
                            SpeechRecognizer.ERROR_SERVER -> "no-speech"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                            SpeechRecognizer.ERROR_NO_MATCH -> "no-speech"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "aborted"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                            else -> "unknown"
                        }
                        triggerJsError(id, errorType)
                        triggerJsEnd(id)
                        cleanup() // Fully clean up the failed instance
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            triggerJsResult(id, recognizedText, true)
                        }
                        triggerJsEnd(id)
                        cleanup() // Fully clean up after completing successfully
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            triggerJsResult(id, recognizedText, false)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)

            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                triggerJsError(id, "unknown")
                triggerJsEnd(id)
                cleanup()
            }
        }
    }

    @JavascriptInterface
    fun stopSpeech(id: String) {
        webView.post {
            if (activeId == id) {
                try {
                    speechRecognizer?.stopListening()
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @JavascriptInterface
    fun abortSpeech(id: String) {
        webView.post {
            if (activeId == id) {
                cleanup()
            }
        }
    }

    private fun cancelSpeechSilent() {
        cleanup()
    }

    private fun cleanup() {
        try {
            speechRecognizer?.cancel()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
        activeId = null
    }

    private fun executeJs(script: String) {
        webView.post {
            try {
                webView.evaluateJavascript(script, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun triggerJsStart(id: String) {
        executeJs("if(window.OrionSpeechBridgeCallback) { window.OrionSpeechBridgeCallback.onStart('$id'); }")
    }

    private fun triggerJsResult(id: String, text: String, isFinal: Boolean) {
        val safeText = text.replace("'", "\\'")
        executeJs("if(window.OrionSpeechBridgeCallback) { window.OrionSpeechBridgeCallback.onResult('$id', '$safeText', $isFinal); }")
    }

    fun triggerJsError(id: String, errorType: String) {
        executeJs("if(window.OrionSpeechBridgeCallback) { window.OrionSpeechBridgeCallback.onError('$id', '$errorType'); }")
    }

    fun triggerJsEnd(id: String) {
        executeJs("if(window.OrionSpeechBridgeCallback) { window.OrionSpeechBridgeCallback.onEnd('$id'); }")
    }
}
