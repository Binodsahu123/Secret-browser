package com.example.notificationengine

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class NotificationHistoryManager(private val context: Context) {
    private val db = NotificationDatabase.getDatabase(context)

    fun getAllHistoryFlow(): Flow<List<NotificationHistoryItem>> {
        return db.historyDao().getAllHistoryFlow()
    }

    suspend fun getAllHistoryList(): List<NotificationHistoryItem> {
        return db.historyDao().getAllHistory()
    }

    fun getHistoryForWebsite(websiteUrl: String): Flow<List<NotificationHistoryItem>> {
        return db.historyDao().getHistoryForWebsiteFlow(websiteUrl)
    }

    suspend fun addHistoryItem(websiteUrl: String, websiteName: String, title: String, body: String, clickUrl: String) {
        val item = NotificationHistoryItem(
            websiteUrl = websiteUrl,
            websiteName = websiteName,
            title = title,
            body = body,
            clickUrl = clickUrl,
            timestamp = System.currentTimeMillis()
        )
        db.historyDao().insertHistoryItem(item)
    }

    suspend fun markAsRead(id: Int) {
        db.historyDao().markAsRead(id)
    }

    suspend fun markAllAsRead() {
        db.historyDao().markAllAsRead()
    }

    suspend fun deleteItem(id: Int) {
        db.historyDao().deleteHistoryItem(id)
    }

    suspend fun clearHistory() {
        db.historyDao().clearAllHistory()
    }

    /**
     * Filters a list of history items by standard intervals:
     * - "today"
     * - "yesterday"
     * - "last_7_days"
     * - "all"
     */
    fun filterHistory(items: List<NotificationHistoryItem>, ageFilter: String): List<NotificationHistoryItem> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        // Start of yesterday
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startOfYesterday = calendar.timeInMillis

        // Start of last 7 days
        calendar.add(Calendar.DAY_OF_YEAR, -5) // Already back 1 day, subtract 5 more to hit 7 days total
        val startOfLast7Days = calendar.timeInMillis

        return when (ageFilter) {
            "today" -> items.filter { it.timestamp >= startOfToday }
            "yesterday" -> items.filter { it.timestamp in startOfYesterday until startOfToday }
            "last_7_days" -> items.filter { it.timestamp >= startOfLast7Days }
            else -> items
        }
    }

    /**
     * Searches notification logs for matching title/body or website name.
     */
    fun searchHistory(items: List<NotificationHistoryItem>, query: String): List<NotificationHistoryItem> {
        if (query.isBlank()) return items
        val lower = query.lowercase().trim()
        return items.filter {
            it.title.lowercase().contains(lower) ||
            it.body.lowercase().contains(lower) ||
            it.websiteName.lowercase().contains(lower)
        }
    }
}
