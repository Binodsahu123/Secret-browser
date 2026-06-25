package com.example.permissionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionState {
    IDLE,
    REQUEST_DETECTED,
    CHECKING_CACHE,
    SHOWING_PROMPT,
    REQUESTING_ANDROID_PERMISSION,
    GRANTED,
    DENIED,
    FAILED,
    VERIFICATION,
    COMPLETE
}

class PermissionStateMachine {
    private val _currentState = MutableStateFlow(PermissionState.IDLE)
    val currentState: StateFlow<PermissionState> = _currentState.asStateFlow()

    fun transitionTo(newState: PermissionState) {
        android.util.Log.d("PermissionStateMachine", "State transition: ${_currentState.value} -> $newState")
        _currentState.value = newState
    }

    fun reset() {
        _currentState.value = PermissionState.IDLE
    }
}
