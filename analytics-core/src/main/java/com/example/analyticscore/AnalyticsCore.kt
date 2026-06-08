package com.example.analyticscore

import android.util.Log

object AnalyticsCore {
    private const val TAG = "AnalyticsCore"

    fun logEvent(event: String, params: Map<String, Any> = emptyMap()) {
        Log.i(TAG, "Event logged: $event, params: $params")
    }

    fun logError(throwable: Throwable, message: String) {
        Log.e(TAG, "Error: $message", throwable)
    }
}
