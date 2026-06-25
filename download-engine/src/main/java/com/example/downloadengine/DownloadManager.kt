package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class DownloadManagerImpl(private val context: Context) : DownloadEngine {
    private val repository = DownloadRepository(context)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentConfig = DownloadConfig(maxConcurrentDownloads = 3, maxThreadsPerDownload = 8)

    private fun startDownloadForegroundServiceSafely() {
        try {
            val intent = android.content.Intent().apply {
                setClassName(context.packageName, "com.example.downloadnotificationengine.DownloadForegroundService")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    init {
        DownloadQueueManager.setProgressCallback { item ->
            scope.launch {
                repository.insertOrUpdateDownload(item)
                if (item.status == "RUNNING") {
                    startDownloadForegroundServiceSafely()
                }
                if (item.status == "COMPLETED" || item.status == "FAILED" || item.status == "CANCELLED") {
                    triggerQueueCheck()
                }
            }
        }

        scope.launch {
            while (true) {
                try {
                    delay(2000)
                    processScheduling()
                    processQueue()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun triggerQueueCheck() {
        scope.launch {
            try {
                processQueue()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun processScheduling() {
        val now = System.currentTimeMillis()
        val scheduled = repository.getScheduledDownloads()
        for (item in scheduled) {
            if (item.scheduledTime > 0L && item.scheduledTime <= now) {
                val activated = item.copy(status = "PENDING", speed = "Processing scheduled...", scheduledTime = 0L)
                repository.insertOrUpdateDownload(activated)
                triggerQueueCheck()
            }
        }
    }

    private suspend fun processQueue() {
        val running = repository.getRunningDownloads()
        val runningCount = running.size
        val maxAllowed = currentConfig.maxConcurrentDownloads

        if (runningCount < maxAllowed) {
            val pending = repository.getPendingDownloadsSorted()
            if (pending.isNotEmpty()) {
                val neededSlots = maxAllowed - runningCount
                val toStart = pending.take(neededSlots)
                for (item in toStart) {
                    val loadingItem = item.copy(status = "RUNNING", speed = "Connecting...")
                    repository.insertOrUpdateDownload(loadingItem)
                    
                    DownloadQueueManager.startDownloadTask(context, loadingItem, currentConfig) { updated ->
                        repository.insertOrUpdateDownload(updated)
                    }
                }
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
        startDownloadForegroundServiceSafely()
        triggerQueueCheck()
        try {
            DownloadWorker.enqueueDownload(context, id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return id
    }

    override suspend fun pauseDownload(id: Long) {
        val existing = repository.getDownloadById(id)
        if (existing != null) {
            DownloadQueueManager.cancelTask(id)
            val updated = existing.copy(status = "PAUSED", speed = "Paused")
            repository.insertOrUpdateDownload(updated)
            triggerQueueCheck()
        }
    }

    override suspend fun resumeDownload(id: Long) {
        val existing = repository.getDownloadById(id)
        if (existing != null) {
            val updated = existing.copy(status = "PENDING", speed = "Pending")
            repository.insertOrUpdateDownload(updated)
            startDownloadForegroundServiceSafely()
            triggerQueueCheck()
            try {
                DownloadWorker.enqueueDownload(context, id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun cancelDownload(id: Long) {
        val existing = repository.getDownloadById(id)
        if (existing != null) {
            DownloadQueueManager.cancelTask(id)
            val updated = existing.copy(status = "CANCELLED", speed = "Cancelled")
            repository.insertOrUpdateDownload(updated)
            triggerQueueCheck()
        }
    }

    override suspend fun deleteDownload(id: Long) {
        DownloadQueueManager.cancelTask(id)
        repository.deleteDownload(id)
        triggerQueueCheck()
    }

    override fun setConfig(config: DownloadConfig) {
        currentConfig = config
    }

    override fun getConfig(): DownloadConfig {
        return currentConfig
    }
}
