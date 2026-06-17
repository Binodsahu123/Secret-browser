package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.flow.Flow

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

class DownloadWorkers {
    fun enqueueDownloadCheck() {
        // Compatibility helper
    }
}
