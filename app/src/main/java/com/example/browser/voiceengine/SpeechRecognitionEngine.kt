package com.example.browser.voiceengine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionEngine(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onHearing: () -> Unit,
    private val onVolumeChanged: (Float) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onErrorOccurred: (String, Int) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isEngineListening = false

    fun startListening(languageCode: String) {
        if (isEngineListening) {
            stopListening()
        }
        isEngineListening = true
        Log.i("SpeechRecognitionEngine", "Starting active speech recognition for locale: $languageCode")

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onReady()
                    }

                    override fun onBeginningOfSpeech() {
                        onHearing()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = (rmsdB + 2f) / 10f
                        onVolumeChanged(normalized.coerceIn(0f, 1f))
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client connection issue"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Waiting for speech..."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone busy"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out..."
                            else -> "Error ($error)"
                        }
                        Log.d("SpeechRecognitionEngine", "Active speech recognizer error: $msg ($error)")
                        onErrorOccurred(msg, error)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onResult(matches[0])
                        } else {
                            onErrorOccurred("No speech matched", SpeechRecognizer.ERROR_NO_MATCH)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onPartial(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
            }

            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechRecognitionEngine", "Failed to start active speech recognizer", e)
            onErrorOccurred("Engine crash", -1)
        }
    }

    fun stopListening() {
        isEngineListening = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.e("SpeechRecognitionEngine", "Error stopping recognizer", e)
        }
        recognizer = null
    }
}
