package com.example.browser.voiceengine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class OrionHotwordService : Service() {

    private var hotwordEngine: OrionHotwordEngine? = null

    companion object {
        private const val CHANNEL_ID = "orion_voice_channel"
        private const val NOTIFICATION_ID = 1009

        fun startService(context: Context) {
            val intent = Intent(context, OrionHotwordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OrionHotwordService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("OrionHotwordService", "Creating Background Hotword Service.")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildSimpleNotification())
        
        hotwordEngine = OrionHotwordEngine(this) { command ->
            Log.i("OrionHotwordService", "Wake word matched from background service: $command")
            WakeEventManager.triggerWake(applicationContext, command)
        }
        hotwordEngine?.startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OrionHotwordService", "Service started command.")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("OrionHotwordService", "Cleaning up Background Hotword Service.")
        hotwordEngine?.stopEngine()
        hotwordEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Orion Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Orion hotword passive engine"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildSimpleNotification(): Notification {
        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Orion Passive Voice Active")
            .setContentText("Say 'Hello Orion' to speak")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        return builder.build()
    }
}
