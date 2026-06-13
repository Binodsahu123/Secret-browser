package com.example.extensionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class DebugErrorType {
    RUNTIME, MANIFEST, PERMISSION, STORAGE, MESSAGE
}

data class ExtensionDebugLog(
    val id: String = UUID.randomUUID().toString(),
    val extensionId: String,
    val extensionName: String,
    val type: DebugErrorType,
    val message: String,
    val severity: String, // "ERROR", "WARNING", "INFO"
    val timestamp: Long = System.currentTimeMillis()
)

data class ExtensionPerformanceMetric(
    val extensionId: String,
    val extensionName: String,
    val cpuUsagePercent: Double,
    val memoryUsageMb: Double,
    val storageUsageKb: Double,
    val activeWorkersCount: Int
)

class ExtensionDebuggerEngine {
    private val _logs = MutableStateFlow<List<ExtensionDebugLog>>(emptyList())
    val logs: StateFlow<List<ExtensionDebugLog>> = _logs.asStateFlow()

    private val _metrics = MutableStateFlow<List<ExtensionPerformanceMetric>>(emptyList())
    val metrics: StateFlow<List<ExtensionPerformanceMetric>> = _metrics.asStateFlow()

    fun logError(extensionId: String, extensionName: String, type: DebugErrorType, message: String, severity: String = "ERROR") {
        val entry = ExtensionDebugLog(
            extensionId = extensionId,
            extensionName = extensionName,
            type = type,
            message = message,
            severity = severity
        )
        _logs.update { it + entry }
        
        // Also log to InspectorEngine's Web Console if available via reflection to decouple module compile targets
        try {
            val inspectorEngineClass = Class.forName("com.example.developertoolsengine.InspectorEngine")
            val companionField = inspectorEngineClass.getField("Companion")
            val companionInstance = companionField.get(null)
            
            val getInstanceMethod = companionInstance.javaClass.getMethod("getInstance")
            val inspectorInstance = getInstanceMethod.invoke(companionInstance)
            
            val logLevelClass = Class.forName("com.example.developertoolsengine.LogLevel")
            val enumValName = when (severity) {
                "ERROR" -> "ERROR"
                "WARNING" -> "WARNING"
                else -> "INFO"
            }
            val logLevelEnum = java.lang.Enum.valueOf(logLevelClass as Class<out Enum<*>>, enumValName)
            
            val logConsoleMethod = inspectorEngineClass.getMethod("logConsole", logLevelClass, String::class.java)
            logConsoleMethod.invoke(inspectorInstance, logLevelEnum, "[Extension: $extensionName] $message")
        } catch (e: Exception) {
            // Ignore decoupling exception gracefully
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun updateMetrics(newMetrics: List<ExtensionPerformanceMetric>) {
        _metrics.value = newMetrics
    }

    companion object {
        val instance = ExtensionDebuggerEngine()
    }
}
