package com.example.browserengine

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object PerformanceMonitor {
    private const val TAG = "PerformanceMonitor"
    private val timers = ConcurrentHashMap<String, Long>()

    fun startTimer(key: String) {
        timers[key] = SystemClock.elapsedRealtime()
    }

    fun stopTimer(key: String): Long {
        val startTime = timers.remove(key) ?: return -1
        val duration = SystemClock.elapsedRealtime() - startTime
        Log.i(TAG, "[PerformanceMonitor] $key completed in: ${duration}ms")
        return duration
    }

    fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // in MB
    }

    fun printTelemetry() {
        val memory = getMemoryUsage()
        Log.i(TAG, "[PerformanceMonitor] Telemetry -> RAM Utilization: ${memory}MB")
    }
}
