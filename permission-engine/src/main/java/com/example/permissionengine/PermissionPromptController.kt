package com.example.permissionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionRequestPrompt(
    val origin: String,
    val permissionType: String,
    val onDecision: (decision: String) -> Unit // "ALLOW_ONCE", "ALLOW_ALWAYS", "BLOCK"
)

class PermissionPromptController {
    private val _pendingPrompt = MutableStateFlow<PermissionRequestPrompt?>(null)
    val pendingPrompt: StateFlow<PermissionRequestPrompt?> = _pendingPrompt.asStateFlow()

    fun showPrompt(origin: String, permissionType: String, onDecision: (String) -> Unit) {
        _pendingPrompt.value = PermissionRequestPrompt(origin, permissionType) { decision ->
            _pendingPrompt.value = null
            onDecision(decision)
        }
    }

    fun dismissPrompt() {
        _pendingPrompt.value = null
    }
}
