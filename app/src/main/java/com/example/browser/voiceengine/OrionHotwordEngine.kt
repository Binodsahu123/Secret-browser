package com.example.browser.voiceengine

import android.content.Context
import android.util.Log

class OrionHotwordEngine(
    private val context: Context,
    private val onWakeDetected: (String?) -> Unit
) {
    private var audioCaptureManager: AudioCaptureManager? = null
    private var hotwordDetector: HotwordDetector? = null
    private var isEngineRunning = false
    private var isHighResDetectorActive = false

    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        Log.i("OrionHotwordEngine", "Starting Orion Hotword Engine - Idle Mode")
        startPassiveCapture()
    }

    private fun startPassiveCapture() {
        if (!isEngineRunning || isHighResDetectorActive) return
        
        audioCaptureManager = AudioCaptureManager(context) { buffer, readSize ->
            val hasVoice = AudioFrameProcessor.hasVoiceActivity(buffer, readSize)
            if (hasVoice) {
                Log.i("OrionHotwordEngine", "Voice activity detected! Escalating to High-Res Detector.")
                // Stop direct capture to release MIC for SpeechRecognizer
                audioCaptureManager?.stopCapture()
                audioCaptureManager = null
                
                // Start High-Res Detector
                startHighResDetector()
            }
        }.apply {
            startCapture()
        }
    }

    private fun startHighResDetector() {
        isHighResDetectorActive = true
        hotwordDetector = HotwordDetector(context, 
            onWake = { command ->
                Log.i("OrionHotwordEngine", "Wake word matched! Command: $command")
                stopHighResDetector()
                onWakeDetected(command)
            },
            onFinishedOrFailed = {
                Log.i("OrionHotwordEngine", "High-Res Detector finished. Returning to Passive Capture.")
                stopHighResDetector()
                // Restart passive silent capture
                startPassiveCapture()
            }
        ).apply {
            startDetection()
        }
    }

    private fun stopHighResDetector() {
        isHighResDetectorActive = false
        hotwordDetector?.stopDetection()
        hotwordDetector = null
    }

    fun stopEngine() {
        isEngineRunning = false
        Log.i("OrionHotwordEngine", "Stopping Orion Hotword Engine")
        
        audioCaptureManager?.stopCapture()
        audioCaptureManager = null

        stopHighResDetector()
    }
}
