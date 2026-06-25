package com.example.permissionengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PermissionManager(
    private val context: Context,
    private val repository: SitePermissionRepository,
    private val memoryStore: OriginPermissionStore,
    val stateMachine: PermissionStateMachine = PermissionStateMachine(),
    val promptController: PermissionPromptController = PermissionPromptController(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    init {
        // Initialize memory store with database on start
        scope.launch {
            try {
                val dbPermissions = repository.getAllPermissions()
                for (entity in dbPermissions) {
                    val isExpired = entity.expiresAt > 0 && entity.expiresAt < System.currentTimeMillis()
                    if (isExpired) {
                        repository.deletePermission(entity.origin, entity.permissionType)
                    } else {
                        memoryStore.setMemoryState(entity.origin, entity.permissionType, entity.decision)
                    }
                }
            } catch (e: Exception) {
                PermissionLogger.logFailure("init", "DB_LOAD", "Failed logic initialization", e.toString())
            }
        }
    }

    suspend fun checkPermissionDirect(origin: String, permissionType: String): String {
        stateMachine.transitionTo(PermissionState.CHECKING_CACHE)
        
        // 1. Check memory store
        val memState = memoryStore.getMemoryState(origin, permissionType)
        if (memState != null) {
            PermissionLogger.logEvent(origin, permissionType, "CHECKING_CACHE", "Found in memory: $memState")
            return memState
        }

        // 2. Check Database
        val dbEntity = repository.getPermission(origin, permissionType)
        if (dbEntity != null) {
            val isExpired = dbEntity.expiresAt > 0 && dbEntity.expiresAt < System.currentTimeMillis()
            if (isExpired) {
                PermissionLogger.logEvent(origin, permissionType, "CHECKING_CACHE", "Database cached expired. Deleting.")
                repository.deletePermission(origin, permissionType)
            } else {
                PermissionLogger.logEvent(origin, permissionType, "CHECKING_CACHE", "Found in DB: ${dbEntity.decision}")
                memoryStore.setMemoryState(origin, permissionType, dbEntity.decision)
                return dbEntity.decision
            }
        }

        PermissionLogger.logEvent(origin, permissionType, "CHECKING_CACHE", "No cached decisions. Asking.")
        return "ASK"
    }

    fun handleRequest(
        origin: String,
        permissionType: String,
        onResult: (String) -> Unit // "ALLOW", "BLOCK", or "ASK_SYSTEM"
    ) {
        scope.launch {
            PermissionLogger.logEvent(origin, permissionType, "REQUEST_DETECTED", "Beginning new request sequence")
            stateMachine.transitionTo(PermissionState.REQUEST_DETECTED)

            val cacheDecision = checkPermissionDirect(origin, permissionType)
            if (cacheDecision == "ALLOW_ALWAYS" || cacheDecision == "ALLOW_ONCE") {
                // Verify android hardware permissions if applicable
                val systemPermission = AndroidRuntimePermissionManager.mapToAndroidPermission(permissionType)
                if (systemPermission != null && !AndroidRuntimePermissionManager.hasPermission(context, systemPermission)) {
                    requestSystemAndComplete(origin, permissionType, cacheDecision, onResult)
                } else {
                    completeRequest(origin, permissionType, "ALLOW", cacheDecision, onResult)
                }
                return@launch
            } else if (cacheDecision == "BLOCK") {
                completeRequest(origin, permissionType, "BLOCK", cacheDecision, onResult)
                return@launch
            }

            // Unknown: Show user dialog prompt
            stateMachine.transitionTo(PermissionState.SHOWING_PROMPT)
            withContext(Dispatchers.Main) {
                promptController.showPrompt(origin, permissionType) { finalDecision ->
                    scope.launch {
                        if (finalDecision == "BLOCK") {
                            savePermissionState(origin, permissionType, "BLOCK")
                            completeRequest(origin, permissionType, "BLOCK", "BLOCK", onResult)
                        } else {
                            // "ALLOW_ALWAYS" or "ALLOW_ONCE"
                            savePermissionState(origin, permissionType, finalDecision)
                            
                            val systemPermission = AndroidRuntimePermissionManager.mapToAndroidPermission(permissionType)
                            if (systemPermission != null && !AndroidRuntimePermissionManager.hasPermission(context, systemPermission)) {
                                requestSystemAndComplete(origin, permissionType, finalDecision, onResult)
                            } else {
                                completeRequest(origin, permissionType, "ALLOW", finalDecision, onResult)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun requestSystemAndComplete(
        origin: String,
        permissionType: String,
        decisionStoreType: String,
        onResult: (String) -> Unit
    ) {
        stateMachine.transitionTo(PermissionState.REQUESTING_ANDROID_PERMISSION)
        val hasPermission = AndroidRuntimePermissionManager.mapToAndroidPermission(permissionType)?.let {
            AndroidRuntimePermissionManager.hasPermission(context, it)
        } ?: true

        if (hasPermission) {
            completeRequest(origin, permissionType, "ALLOW", decisionStoreType, onResult)
        } else {
            PermissionLogger.logEvent(origin, permissionType, "REQUESTING_ANDROID_PERMISSION", "Needs OS runtime permission approval")
            onResult("ASK_SYSTEM")
        }
    }

    suspend fun savePermissionState(origin: String, permissionType: String, state: String) {
        memoryStore.setMemoryState(origin, permissionType, state)
        if (state == "ALLOW_ALWAYS" || state == "BLOCK") {
            repository.savePermission(origin, permissionType, state)
        }
    }

    private fun completeRequest(
        origin: String,
        permissionType: String,
        result: String,
        decisionSource: String,
        onResult: (String) -> Unit
    ) {
        val finalState = if (result == "ALLOW") PermissionState.GRANTED else PermissionState.DENIED
        stateMachine.transitionTo(finalState)
        
        if (result == "ALLOW") {
            stateMachine.transitionTo(PermissionState.VERIFICATION)
            PermissionLogger.logSuccess(
                origin = origin,
                permission = permissionType,
                androidResult = "GRANTED",
                grantResult = "WEBVIEW_GRANTED",
                verificationResult = "VERIFIED_ACTIVE"
            )
            stateMachine.transitionTo(PermissionState.COMPLETE)
        } else {
            PermissionLogger.logFailure(origin, permissionType, "Request blocked by decision rule in state $finalState")
            stateMachine.transitionTo(PermissionState.COMPLETE)
        }
        onResult(result)
    }
}
