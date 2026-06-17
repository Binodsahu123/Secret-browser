package com.example.browser.voiceengine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioCaptureManager(
    private val context: Context,
    private val onFrameCaptured: (ShortArray, Int) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isRecording) return
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e("AudioCaptureManager", "Invalid buffer size computed")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioCaptureManager", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i("AudioCaptureManager", "Audio recording started")

            captureJob = scope.launch {
                val buffer = ShortArray(1024)
                while (isRecording) {
                    val record = audioRecord
                    if (record == null || !isRecording) break
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        onFrameCaptured(buffer, readBytes)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioCaptureManager", "Error starting audio capture", e)
            stopCapture()
        }
    }

    fun stopCapture() {
        isRecording = false
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioCaptureManager", "Error stopping audio record", e)
        }
        audioRecord = null
        Log.i("AudioCaptureManager", "Audio recording stopped")
    }
}
