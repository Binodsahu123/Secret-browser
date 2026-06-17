package com.example.browser.voiceengine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

class OrionHotwordService : Service() {

    companion object {
        fun startService(context: Context) {
            Log.i("OrionHotwordService", "startService called: Ignored (Background Wake-phrase active monitoring disabled).")
        }

        fun stopService(context: Context) {
            Log.i("OrionHotwordService", "stopService called: Ignored.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("OrionHotwordService", "onCreate called and ignored.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
