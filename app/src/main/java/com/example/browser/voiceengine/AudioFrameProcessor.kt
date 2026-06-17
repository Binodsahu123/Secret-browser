package com.example.browser.voiceengine

import kotlin.math.sqrt

object AudioFrameProcessor {
    fun calculateRms(buffer: ShortArray, readSize: Int): Double {
        if (readSize <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / readSize)
    }

    fun hasVoiceActivity(buffer: ShortArray, readSize: Int, threshold: Double = 150.0): Boolean {
        val rms = calculateRms(buffer, readSize)
        return rms > threshold
    }
}
