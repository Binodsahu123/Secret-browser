package com.example.browser

import kotlin.math.log10
import kotlin.math.sqrt

object AudioProcessor {
    fun calculateRms(buffer: ShortArray, length: Int): Double {
        if (length <= 0) return 0.0
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / length)
    }

    fun calculateDecibels(rms: Double): Double {
        if (rms <= 0.0) return 0.0
        // Reference amplitude of 1 for raw 16-bit counts
        val db = 20 * log10(rms)
        return if (db.isInfinite() || db.isNaN()) 0.0 else db
    }

    fun isActivityDetected(buffer: ShortArray, length: Int, dbThreshold: Double = 40.0): Boolean {
        val rms = calculateRms(buffer, length)
        val dbs = calculateDecibels(rms)
        return dbs > dbThreshold
    }
}
