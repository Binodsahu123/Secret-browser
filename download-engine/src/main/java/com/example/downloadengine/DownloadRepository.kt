package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val context: Context) {
    private val db = DownloadDatabase.getDatabase(context)
    private val dao = db.downloadDao()

    fun getAllDownloadsFlow(): Flow<List<DownloadItem>> {
        return dao.getAllDownloadsFlow()
    }

    fun getDownloadsByCategoryFlow(category: String): Flow<List<DownloadItem>> {
        return dao.getDownloadsByCategoryFlow(category)
    }

    suspend fun getDownloadById(id: Long): DownloadItem? {
        return dao.getDownloadById(id)
    }

    suspend fun insertOrUpdateDownload(item: DownloadItem) {
        dao.insertDownload(item)
    }

    suspend fun updateProgress(id: Long, status: String, progress: Int, downloaded: Long, speed: String) {
        dao.updateProgress(id, status, progress, downloaded, speed)
    }

    suspend fun deleteDownload(id: Long) {
        dao.deleteDownload(id)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }
}
