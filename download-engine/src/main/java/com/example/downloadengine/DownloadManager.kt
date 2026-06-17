package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class DownloadManagerImpl(private val context: Context) : DownloadEngine {
    private val repository = DownloadRepository(context)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentConfig = DownloadConfig(maxThreadsPerDownload = 32)

    init {
        DownloadQueueManager.setProgressCallback { item ->
            scope.launch {
                repository.insertOrUpdateDownload(item)
            }
        }
    }

    override fun getDownloadsFlow(): Flow<List<DownloadItem>> {
        return repository.getAllDownloadsFlow()
    }

    override fun getDownloadsByCategory(category: String): Flow<List<DownloadItem>> {
        return repository.getDownloadsByCategoryFlow(category)
    }

    override suspend fun startDownload(url: String, fileName: String, mimeType: String, threads: Int): Long {
        val id = System.currentTimeMillis()
        val category = DownloadScheduler.getCategoryForMimeType(mimeType)
        
        // Dynamic acceleration clamping: support up to 32 concurrency threads
        val activeThreads = threads.coerceIn(1, 32)

        val initialItem = DownloadItem(
            id = id,
            title = fileName,
            url = url,
            mimeType = mimeType,
            status = "PENDING",
            progress = 0,
            threads = activeThreads,
            category = category
        )
        repository.insertOrUpdateDownload(initialItem)
        
        DownloadQueueManager.startDownloadTask(context, initialItem, currentConfig) { updated ->
            repository.insertOrUpdateDownload(updated)
        }
        return id
    }

    override suspend fun pauseDownload(id: Long) {
        val existing = repository.getDownloadById(id)
        if (existing != null) {
            DownloadQueueManager.cancelTask(id)
            val updated = existing.copy(status = "PAUSED", speed = "Paused")
            repository.insertOrUpdateDownload(updated)
        }
    }

    override suspend fun resumeDownload(id: Long) {
        val existing = repository.getDownloadById(id)
        if (existing != null) {
            val updated = existing.copy(status = "PENDING", speed = "Pending")
            repository.insertOrUpdateDownload(updated)
            DownloadQueueManager.startDownloadTask(context, updated, currentConfig) { newest ->
                repository.insertOrUpdateDownload(newest)
            }
        }
    }

    override suspend fun cancelDownload(id: Long) {
        val existing = repository.getDownloadById(id)
        if (existing != null) {
            DownloadQueueManager.cancelTask(id)
            val updated = existing.copy(status = "CANCELLED", speed = "Cancelled")
            repository.insertOrUpdateDownload(updated)
        }
    }

    override suspend fun deleteDownload(id: Long) {
        DownloadQueueManager.cancelTask(id)
        repository.deleteDownload(id)
    }

    override fun setConfig(config: DownloadConfig) {
        currentConfig = config
    }

    override fun getConfig(): DownloadConfig {
        return currentConfig
    }
}
