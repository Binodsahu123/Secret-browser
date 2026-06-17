package com.example.browser

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log

class VideoOrientationManager(private val activity: Activity) {
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun lockToLandscape() {
        try {
            originalOrientation = activity.requestedOrientation
            // Force sensor landscape so the screen automatically rotates between left and right landscape depending on how the user holds the device
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (e: Exception) {
            Log.e("VideoOrientation", "Lock to landscape failed", e)
        }
    }

    fun restoreOrientation() {
        try {
            // Restore back to user portrait or previous orientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } catch (e: Exception) {
            Log.e("VideoOrientation", "Restore orientation failed", e)
        }
    }
}
