package com.example.mediadetectorengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TraceLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: String = "INFO", // "INFO", "WARNING", "ERROR", "SUCCESS"
    val component: String = "Engine" // "JS_PROBE", "NATIVE_RULE", "KOTLIN_CORE", "DOWNLOADER", "VIEWER"
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object MediaDebugLogger {
    private val _logs = MutableStateFlow<List<TraceLog>>(emptyList())
    val logs: StateFlow<List<TraceLog>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: String = "INFO", component: String = "Engine") {
        val entry = TraceLog(tag = tag, message = message, level = level, component = component)
        _logs.update { current ->
            (current + entry).takeLast(100) // Keep last 100 entries
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
