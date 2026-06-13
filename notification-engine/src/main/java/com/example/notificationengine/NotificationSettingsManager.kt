package com.example.notificationengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationSettingsManager(private val context: Context) {
    private val db = NotificationDatabase.getDatabase(context)
    private val TAG = "NotificationSettingsMgr"

    /**
     * Pauses notifications for a subscription website for a specified duration in milliseconds.
     */
    suspend fun pauseNotifications(websiteUrl: String, durationMs: Long) = withContext(Dispatchers.IO) {
        val sub = db.subscriptionDao().getSubscription(websiteUrl)
        if (sub != null) {
            val pauseUntil = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
            db.subscriptionDao().insertSubscription(sub.copy(pauseUntil = pauseUntil))
            Log.d(TAG, "Paused notifications for $websiteUrl until $pauseUntil (${durationMs}ms duration)")
        }
    }

    /**
     * Resumes notifications immediately.
     */
    suspend fun resumeNotifications(websiteUrl: String) = withContext(Dispatchers.IO) {
        val sub = db.subscriptionDao().getSubscription(websiteUrl)
        if (sub != null) {
            db.subscriptionDao().insertSubscription(sub.copy(pauseUntil = 0L))
            Log.d(TAG, "Resumed notifications for $websiteUrl")
        }
    }

    /**
     * Updates notification configurations like priority, sounds, vibration, or silent flags.
     */
    suspend fun updateConfig(
        websiteUrl: String,
        isMuted: Boolean,
        priority: Int,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val sub = db.subscriptionDao().getSubscription(websiteUrl)
        if (sub != null) {
            db.subscriptionDao().insertSubscription(sub.copy(
                isMuted = isMuted,
                priority = priority,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled
            ))
            Log.d(TAG, "Updated config for $websiteUrl: mute=$isMuted, priority=$priority, sound=$soundEnabled, vib=$vibrationEnabled")
        }
    }

    /**
     * Helper to retrieve current configuration values for editing.
     */
    suspend fun getSubscription(websiteUrl: String): NotificationSubscription? = withContext(Dispatchers.IO) {
        return@withContext db.subscriptionDao().getSubscription(websiteUrl)
    }
}
