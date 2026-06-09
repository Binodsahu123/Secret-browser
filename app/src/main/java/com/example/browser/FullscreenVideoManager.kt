package com.example.browser

import android.app.Activity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class FullscreenVideoManager(private val activity: Activity) {
    private val orientationManager = VideoOrientationManager(activity)

    fun enterFullscreen() {
        // 1. Hide system bars (status and navigation) to achieve immersive full screen
        val window = activity.window
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        WindowInsetsControllerCompat(window, decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 2. Lock orientation to landscape mode
        orientationManager.lockToLandscape()
    }

    fun exitFullscreen() {
        // 1. Show show system bars back
        val window = activity.window
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        WindowInsetsControllerCompat(window, decorView).let { controller ->
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }

        // 2. Restore orientation to normal portrait mode
        orientationManager.restoreOrientation()
    }
}
