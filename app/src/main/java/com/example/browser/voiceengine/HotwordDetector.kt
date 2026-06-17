package com.example.browser.voiceengine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class HotwordDetector(
    private val context: Context,
    private val onWake: (String?) -> Unit,
    private val onFinishedOrFailed: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForHotword = false

    fun startDetection() {
        if (isListeningForHotword) return
        isListeningForHotword = true
        startRecognizer()
    }

    fun stopDetection() {
        isListeningForHotword = false
        stopRecognizer()
    }

    private fun startRecognizer() {
        if (!isListeningForHotword) return
        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.d("HotwordDetector", "High-Res recognizer finished with error: $error")
                    onFinishedOrFailed()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        processText(matches[0])
                    } else {
                        onFinishedOrFailed()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        if (checkMatchedPhrases(text)) {
                            processText(text)
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            Log.d("HotwordDetector", "High-Res speech recognizer started active check")
        } catch (e: Exception) {
            Log.e("HotwordDetector", "Error starting high-res check", e)
            onFinishedOrFailed()
        }
    }

    private fun stopRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
    }

    private fun checkMatchedPhrases(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("hello orion") || lower.contains("hey orion") || lower.contains("orion assistant")
    }

    private fun processText(text: String) {
        val lower = text.lowercase()
        Log.i("HotwordDetector", "Matched text: '$text'")
        
        var matched = false
        var command: String? = null
        val keywords = listOf("hello orion", "hey orion", "orion assistant")
        for (kw in keywords) {
            if (lower.contains(kw)) {
                matched = true
                val index = lower.indexOf(kw)
                val rawCommand = text.substring(index + kw.length).trim()
                if (rawCommand.isNotEmpty()) {
                    command = rawCommand
                }
                break
            }
        }

        if (matched) {
            onWake(command)
        } else {
            onFinishedOrFailed()
        }
    }
}
