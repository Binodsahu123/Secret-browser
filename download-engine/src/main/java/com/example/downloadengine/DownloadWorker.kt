package com.example.downloadengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

object DownloadWorker {
    fun checkAndResumeDownloads(context: Context, engine: DownloadEngine) {
        val config = engine.getConfig()
        if (config.autoResume) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val list = engine.getDownloadsFlow().first()
                    list.forEach { item ->
                        if (item.status == "PENDING" || item.status == "RUNNING") {
                            engine.resumeDownload(item.id)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
