package com.example.permissionengine

import android.webkit.PermissionRequest

class WebViewPermissionBridge(private val permissionManager: PermissionManager) {

    fun handleWebViewPermissionRequest(request: PermissionRequest, onSystemPermissionNeeded: (List<String>) -> Unit) {
        val origin = request.origin.toString()
        val resources = request.resources ?: emptyArray()

        permissionManager.stateMachine.reset()
        PermissionLogger.logEvent(origin, resources.joinToString(", "), "REQUEST_DETECTED", "Processing via WebViewPermissionBridge")

        // 1. Is it autocomplete or DRM?
        val onlyAuto = resources.all { PermissionPolicyResolver.isAutoGrantResource(it) }
        if (onlyAuto) {
            PermissionLogger.logEvent(origin, "DRM_MIDI", "GRANTED", "Auto-granted low-risk resources")
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        request.grant(resources)
                    } catch (e: Exception) {
                        PermissionLogger.logFailure(origin, "DRM_MIDI", "Failed auto-grant inside MainLooper", e.toString())
                    }
                }
            } catch (e: Exception) {
                PermissionLogger.logFailure(origin, "DRM_MIDI", "Failed auto-grant", e.toString())
            }
            return
        }

        val totalResources = resources.size
        if (totalResources == 0) {
            try { request.deny() } catch(e: Exception){}
            return
        }

        var processedCount = 0
        val allowedResources = mutableListOf<String>()
        val blockedResources = mutableListOf<String>()
        val askSystemResources = mutableListOf<String>()

        fun checkCompletion() {
            processedCount++
            if (processedCount == totalResources) {
                // All resources evaluated! Now take a single action on request
                if (blockedResources.isNotEmpty()) {
                    PermissionLogger.logEvent(origin, blockedResources.joinToString(", "), "DENIED", "Blocked by site rules")
                    try {
                        request.deny()
                    } catch (e: Exception) {
                        PermissionLogger.logFailure(origin, "AGGREGATED", "Failed request.deny", e.toString())
                    }
                } else if (askSystemResources.isNotEmpty()) {
                    val needed = AndroidRuntimePermissionManager.mapToAndroidPermissions(askSystemResources.toTypedArray())
                    if (needed.isNotEmpty()) {
                        onSystemPermissionNeeded(needed)
                    } else {
                        // fallback
                        try {
                            val combined = (allowedResources + askSystemResources).toTypedArray()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    request.grant(combined)
                                } catch (e: Exception) {
                                    PermissionLogger.logFailure(origin, "AGGREGATED", "Failed request.grant layout inside MainLooper", e.toString())
                                }
                            }
                        } catch (e: Exception) {
                            PermissionLogger.logFailure(origin, "AGGREGATED", "Failed request.grant layout", e.toString())
                        }
                    }
                } else {
                    // All allowed!
                    try {
                        val combined = allowedResources.toTypedArray()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                request.grant(combined)
                            } catch (e: Exception) {
                                PermissionLogger.logFailure(origin, "AGGREGATED", "Failed request.grant inside MainLooper", e.toString())
                            }
                        }
                        PermissionLogger.logEvent(origin, allowedResources.joinToString(", "), "GRANTED", "Successfully granted all resources")
                    } catch (e: Exception) {
                        PermissionLogger.logFailure(origin, "AGGREGATED", "Failed request.grant", e.toString())
                    }
                }
            }
        }

        for (res in resources) {
            val type = PermissionPolicyResolver.mapResourceToPermissionType(res)
            permissionManager.handleRequest(origin, type) { decision ->
                synchronized(resources) {
                    when (decision) {
                        "ALLOW" -> {
                            allowedResources.add(res)
                        }
                        "BLOCK" -> {
                            blockedResources.add(res)
                        }
                        "ASK_SYSTEM" -> {
                            askSystemResources.add(res)
                        }
                    }
                    checkCompletion()
                }
            }
        }
    }
}
