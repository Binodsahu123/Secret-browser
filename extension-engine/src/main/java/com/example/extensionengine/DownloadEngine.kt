package com.example.extensionengine

/**
 * Interface that intercepts visual downloads, schedules down-link activities, and interacts with system notification engines.
 */
interface DownloadEngine {
    /**
     * Spawns a download task for the selected URL.
     */
    fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
        suggestedFilename: String? = null
    )

    /**
     * Cancels a currently running download by its download ID.
     */
    fun cancelDownload(downloadId: String)
}
