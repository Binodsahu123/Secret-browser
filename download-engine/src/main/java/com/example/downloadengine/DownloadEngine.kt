package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface DownloadEngine {
    fun getDownloadsFlow(): Flow<List<DownloadItem>>
    fun getDownloadsByCategory(category: String): Flow<List<DownloadItem>>
    suspend fun startDownload(url: String, fileName: String, mimeType: String, threads: Int = 4): Long
    suspend fun pauseDownload(id: Long)
    suspend fun resumeDownload(id: Long)
    suspend fun cancelDownload(id: Long)
    suspend fun deleteDownload(id: Long)
    fun setConfig(config: DownloadConfig)
    fun getConfig(): DownloadConfig
}

class DownloadManagerImpl(private val context: Context) : DownloadEngine {
    private val db = DownloadDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentConfig = DownloadConfig()

    init {
        DownloadQueueManager.setProgressCallback { item ->
            scope.launch {
                db.downloadDao().insertDownload(item)
            }
        }
    }

    override fun getDownloadsFlow(): Flow<List<DownloadItem>> {
        return db.downloadDao().getAllDownloadsFlow()
    }

    override fun getDownloadsByCategory(category: String): Flow<List<DownloadItem>> {
        return db.downloadDao().getDownloadsByCategoryFlow(category)
    }

    override suspend fun startDownload(url: String, fileName: String, mimeType: String, threads: Int): Long {
        val id = System.currentTimeMillis()
        val category = DownloadScheduler.getCategoryForMimeType(mimeType)
        val initialItem = DownloadItem(
            id = id,
            title = fileName,
            url = url,
            mimeType = mimeType,
            status = "PENDING",
            progress = 0,
            threads = threads,
            category = category
        )
        db.downloadDao().insertDownload(initialItem)
        
        DownloadQueueManager.startDownloadTask(context, initialItem, currentConfig) { updated ->
            db.downloadDao().insertDownload(updated)
        }
        return id
    }

    override suspend fun pauseDownload(id: Long) {
        val existing = db.downloadDao().getDownloadById(id)
        if (existing != null) {
            DownloadQueueManager.cancelTask(id)
            val updated = existing.copy(status = "PAUSED", speed = "Paused")
            db.downloadDao().insertDownload(updated)
        }
    }

    override suspend fun resumeDownload(id: Long) {
        val existing = db.downloadDao().getDownloadById(id)
        if (existing != null) {
            val updated = existing.copy(status = "PENDING", speed = "Pending")
            db.downloadDao().insertDownload(updated)
            DownloadQueueManager.startDownloadTask(context, updated, currentConfig) { newest ->
                db.downloadDao().insertDownload(newest)
            }
        }
    }

    override suspend fun cancelDownload(id: Long) {
        val existing = db.downloadDao().getDownloadById(id)
        if (existing != null) {
            DownloadQueueManager.cancelTask(id)
            val updated = existing.copy(status = "CANCELLED", speed = "Cancelled")
            db.downloadDao().insertDownload(updated)
        }
    }

    override suspend fun deleteDownload(id: Long) {
        DownloadQueueManager.cancelTask(id)
        db.downloadDao().deleteDownload(id)
    }

    override fun setConfig(config: DownloadConfig) {
        currentConfig = config
    }

    override fun getConfig(): DownloadConfig {
        return currentConfig
    }
}

class DownloadWorkers {
    fun enqueueDownloadCheck() {
        // Compatibility helper
    }
}
