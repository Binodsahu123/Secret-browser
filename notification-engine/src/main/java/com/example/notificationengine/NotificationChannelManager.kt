package com.example.notificationengine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object NotificationChannelManager {
    private const val CHANNEL_DEFAULT_ID = "orion_browser_notifications"
    private const val CHANNEL_HIGH_ID = "orion_browser_high_priority"
    private const val CHANNEL_SILENT_ID = "orion_browser_silent"

    /**
     * Set up all default notification channels in the application context.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Default Channel
            val defaultChan = NotificationChannel(
                CHANNEL_DEFAULT_ID,
                "Website Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Receives updates from your permitted websites"
                enableVibration(true)
            }

            // 2. High Priority Channel
            val highChan = NotificationChannel(
                CHANNEL_HIGH_ID,
                "High Priority Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "A urgent alert channel for subscribed sources"
                enableLights(true)
                enableVibration(true)
            }

            // 3. Silent/Low Priority Channel
            val silentChan = NotificationChannel(
                CHANNEL_SILENT_ID,
                "Silent Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quietly collects subscriptions and logs feeds"
                enableVibration(false)
                setSound(null, null)
            }

            manager.createNotificationChannel(defaultChan)
            manager.createNotificationChannel(highChan)
            manager.createNotificationChannel(silentChan)
        }
    }

    /**
     * Resolves which notification channel is appropriate for a specific website subscription configurations.
     */
    fun getChannelId(priority: Int, soundEnabled: Boolean, vibrationEnabled: Boolean, isMuted: Boolean): String {
        if (isMuted) return CHANNEL_SILENT_ID
        if (!soundEnabled && !vibrationEnabled) return CHANNEL_SILENT_ID
        return if (priority >= 2) CHANNEL_HIGH_ID else CHANNEL_DEFAULT_ID
    }
}
