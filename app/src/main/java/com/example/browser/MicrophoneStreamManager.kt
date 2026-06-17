package com.example.browser

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MicrophoneStreamManager(
    private val context: Context,
    private val onAudioFrame: (ShortArray, Int) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @Synchronized
    fun startStreaming() {
        if (isRecording) return
        if (!VoicePermissionManager.hasRecordAudioPermission(context)) {
            Log.e("MicrophoneStream", "Cannot start mic stream: Permission not granted")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("MicrophoneStream", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize)
                while (isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        onAudioFrame(buffer.clone(), readBytes)
                    }
                }
            }
            Log.i("MicrophoneStream", "Successfully started active low-power mic stream")
        } catch (e: SecurityException) {
            Log.e("MicrophoneStream", "Security exception when opening micro stream", e)
        } catch (e: Exception) {
            Log.e("MicrophoneStream", "Error initiating microphone capture", e)
        }
    }

    @Synchronized
    fun stopStreaming() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("MicrophoneStream", "Error stopping audio capture stream", e)
        }
        audioRecord = null
        Log.i("MicrophoneStream", "Microphone stream closed successfully")
    }
}
