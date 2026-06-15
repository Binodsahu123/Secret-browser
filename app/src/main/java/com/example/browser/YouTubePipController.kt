package com.example.browser

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

object YouTubePipController {
    fun enterPip(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    @Suppress("DEPRECATION")
                    activity.enterPictureInPictureMode()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                @Suppress("DEPRECATION")
                activity.enterPictureInPictureMode()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
