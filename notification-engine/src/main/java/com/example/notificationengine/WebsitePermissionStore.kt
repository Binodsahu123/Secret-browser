package com.example.notificationengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-performance store for querying and saving permission states of websites.
 * Coordinates with the Room Database directly.
 */
class WebsitePermissionStore(private val context: Context) {
    private val db = NotificationDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "WebsitePermissionStore"

    /**
     * Set subscription or permission state for a website.
     */
    suspend fun setPermission(websiteUrl: String, websiteName: String, permission: String) = withContext(Dispatchers.IO) {
        val resolvedRss = NotificationRegistry.resolveRssUrl(websiteUrl)
        val existing = db.subscriptionDao().getSubscription(websiteUrl)
        
        val newSub = if (existing != null) {
            existing.copy(
                permission = permission,
                enabled = permission == "ALLOW",
                customRssUrl = resolvedRss
            )
        } else {
            NotificationSubscription(
                websiteUrl = websiteUrl,
                websiteName = websiteName,
                permission = permission,
                enabled = permission == "ALLOW",
                customRssUrl = resolvedRss
            )
        }
        
        db.subscriptionDao().insertSubscription(newSub)
        Log.d(TAG, "Assigned permission $permission to website $websiteName ($websiteUrl) with feed $resolvedRss")
    }

    /**
     * Get permission state ('ALLOW', 'BLOCK', 'ASK').
     */
    suspend fun getPermission(websiteUrl: String): String = withContext(Dispatchers.IO) {
        val sub = db.subscriptionDao().getSubscription(websiteUrl)
        return@withContext sub?.permission ?: "ASK"
    }

    /**
     * Checks if notifications are allowed for a website.
     */
    suspend fun isAllowed(websiteUrl: String): Boolean = withContext(Dispatchers.IO) {
        val sub = db.subscriptionDao().getSubscription(websiteUrl)
        return@withContext sub != null && sub.permission == "ALLOW" && sub.enabled && (sub.pauseUntil < System.currentTimeMillis())
    }
}
