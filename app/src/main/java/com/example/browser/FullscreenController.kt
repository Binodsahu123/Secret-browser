package com.example.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

object FullscreenController {

    fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun onEnterFullscreen(context: Context) {
        findActivity(context)?.let { activity ->
            FullscreenVideoManager(activity).enterFullscreen()
        }
    }

    fun onExitFullscreen(context: Context) {
        findActivity(context)?.let { activity ->
            FullscreenVideoManager(activity).exitFullscreen()
        }
    }
}
