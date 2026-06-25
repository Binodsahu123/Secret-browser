package com.example.permissionengine

import android.util.Log

object PermissionLogger {
    private const val TAG = "OrionPermissionEngine"

    fun logEvent(origin: String, permission: String, state: String, detail: String) {
        Log.i(TAG, "[EVENT] Origin: $origin | Permission: $permission | State: $state | Detail: $detail")
    }

    fun logSuccess(origin: String, permission: String, androidResult: String, grantResult: String, verificationResult: String) {
        Log.i(TAG, "🟢 [SUCCESS] Origin: $origin | Permission: $permission | Android: $androidResult | WebView: $grantResult | Verification: $verificationResult | Result: SUCCESS")
    }

    fun logFailure(origin: String, permission: String, reason: String, stackTrace: String? = null) {
        Log.e(TAG, "🔴 [FAILURE] Origin: $origin | Permission: $permission | Reason: $reason")
        if (stackTrace != null) {
            Log.e(TAG, "Stacktrace: $stackTrace")
        }
    }
}
