package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log

object DownloadWorker {
    private const val TAG = "DownloadWorker"

    /**
     * Periodically or reactively checks for halted tasks and triggers safe download resumes.
     */
    fun checkAndResumeDownloads(context: Context, engine: DownloadEngine) {
        val config = engine.getConfig()
        if (config.autoResume) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val list = engine.getDownloadsFlow().first()
                    list.forEach { item ->
                        if (item.status == "PENDING" || item.status == "RUNNING") {
                            Log.i(TAG, "DownloadWorker: Restoring interrupted item: ${item.title} (${item.id})")
                            engine.resumeDownload(item.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DownloadWorker failed to evaluate resume targets", e)
                }
            }
        }
    }
}
