package com.example.mediadetectorengine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object MediaMetadataExtractor {

    suspend fun extractMetadata(urlInput: String): DetectedMedia = withContext(Dispatchers.IO) {
        val detected = MediaDetector.detectFromUrl(urlInput) ?: DetectedMedia(
            url = urlInput,
            fileName = MediaDetector.getFileNameFromUrl(urlInput)
        )

        try {
            val url = URL(urlInput)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val contentLength = connection.contentLengthLong
                val contentType = connection.contentType ?: ""
                
                // Read content disposition for precise file name if available
                var dispFileName = ""
                val disposition = connection.getHeaderField("Content-Disposition")
                if (!disposition.isNullOrBlank()) {
                    val key = "filename="
                    val index = disposition.lowercase(Locale.ROOT).indexOf(key)
                    if (index >= 0) {
                        dispFileName = disposition.substring(index + key.length)
                            .trim()
                            .replace("\"", "")
                            .substringBefore(";")
                    }
                }

                val finalMime = if (contentType.isNotBlank()) contentType.substringBefore(";") else detected.mimeType
                val finalName = if (dispFileName.isNotBlank()) dispFileName else detected.fileName

                return@withContext detected.copy(
                    fileSize = if (contentLength > 0) contentLength else detected.fileSize,
                    mimeType = finalMime,
                    fileName = finalName
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext detected
    }
}
