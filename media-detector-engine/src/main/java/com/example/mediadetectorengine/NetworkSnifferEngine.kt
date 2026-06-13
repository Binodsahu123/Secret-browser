package com.example.mediadetectorengine

import android.webkit.WebResourceRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

interface MediaDetectionListener {
    fun onMediaDetected(media: DetectedMedia)
}

object NetworkSnifferEngine {
    private const val TAG = "NetworkSnifferEngine"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val listeners = mutableListOf<MediaDetectionListener>()
    
    private val _allDetectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val allDetectedMedia: StateFlow<List<DetectedMedia>> = _allDetectedMedia.asStateFlow()

    fun registerListener(listener: MediaDetectionListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun unregisterListener(listener: MediaDetectionListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun notifyMediaDetected(media: DetectedMedia) {
        Log.i(TAG, "Media Detected and Sniffed! URL: ${media.url}, MIME: ${media.mimeType}, IsStream: ${media.isStream}")
        
        // Save to internal state flow
        _allDetectedMedia.update { current ->
            if (current.any { it.url == media.url }) current
            else current + media
        }

        // Notify active listener of the callback
        synchronized(listeners) {
            listeners.forEach {
                try {
                    it.onMediaDetected(media)
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking media detection listener", e)
                }
            }
        }
    }

    fun clearAll() {
        _allDetectedMedia.value = emptyList()
    }
}

object MediaStreamDetector {
    private const val TAG = "MediaStreamDetector"

    // Set of streaming/progressive/media extensions to look for
    private val MEDIA_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "mpg", "mpeg", "3gp", "flv",
        "mp3", "wav", "m4a", "aac", "ogg", "flac", "opus",
        "m3u8", "mpd",
        "jpg", "jpeg", "png", "webp", "gif", "svg", "pdf", "zip"
    )

    fun isMediaRequest(url: String, headers: Map<String, String>?): Boolean {
        val lowerUrl = url.lowercase(Locale.ROOT)
        
        // Check extensions in the main URL path
        val path = url.substringBefore("?").substringBefore("#")
        val extension = path.substringAfterLast(".", "").lowercase(Locale.ROOT)
        
        if (MEDIA_EXTENSIONS.contains(extension)) {
            return true
        }

        // Check query params and parts of URL representing common formats
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd") || lowerUrl.contains(".mp4") || lowerUrl.contains(".mp3")) {
            return true
        }

        // Catch common keywords in media streaming endpoints
        if (lowerUrl.contains("/hls/") || lowerUrl.contains("/manifest") || lowerUrl.contains("playlist.m3u8") ||
            lowerUrl.contains("chunklist") || lowerUrl.contains("stream/video") || lowerUrl.contains("stream/audio") ||
            lowerUrl.contains("videoplayback") || lowerUrl.contains("get_video") || lowerUrl.contains(".m4s") ||
            lowerUrl.contains("mediadelivery") || lowerUrl.contains("/master.m3u8")
        ) {
            return true
        }

        // Check if certain header attributes imply a media streaming context
        headers?.let {
            val accept = it["Accept"]?.lowercase(Locale.ROOT) ?: ""
            if (accept.contains("video/") || accept.contains("audio/")) {
                return true
            }
        }

        return false
    }

    fun detectMediaDetails(url: String, callback: (DetectedMedia) -> Unit) {
        // Run network inspection on IO thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileName = MediaDetector.getFileNameFromUrl(url)
                val cleanFileName = if (fileName.length > 50) fileName.take(45) + "..." else fileName
                val basicMedia = MediaDetector.detectFromUrl(url) ?: DetectedMedia(
                    url = url,
                    fileName = cleanFileName
                )

                // Probe resolution / quality from URL string first
                var resolution = ""
                val lowerUrl = url.lowercase(Locale.ROOT)
                val resolutions = listOf("2160p", "4k", "1080p", "720p", "480p", "360p", "240p", "1080", "720", "480")
                for (res in resolutions) {
                    if (lowerUrl.contains(res)) {
                        resolution = res
                        break
                    }
                }

                // If resolution empty, try parsing from common URL query or path patterns (e.g. size=1920x1080)
                if (resolution.isEmpty()) {
                    val regex = Regex("(\\d{3,4})[x_](\\d{3,4})")
                    val match = regex.find(url)
                    if (match != null) {
                        resolution = match.value
                    }
                }

                // Setup basic parameters
                val isM3u8 = url.contains(".m3u8") || basicMedia.isStream
                val isDash = url.contains(".mpd") || basicMedia.isStreamDash
                
                var size = basicMedia.fileSize
                var mimeType = basicMedia.mimeType

                if (mimeType == "application/octet-stream") {
                    mimeType = when {
                        isM3u8 -> "application/x-mpegURL"
                        isDash -> "application/dash+xml"
                        lowerUrl.contains(".mp4") -> "video/mp4"
                        lowerUrl.contains(".mp3") -> "audio/mpeg"
                        lowerUrl.contains(".webm") -> "video/webm"
                        lowerUrl.contains(".pdf") -> "application/pdf"
                        lowerUrl.contains(".zip") -> "application/zip"
                        lowerUrl.contains(".png") -> "image/png"
                        lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") -> "image/jpeg"
                        lowerUrl.contains(".gif") -> "image/gif"
                        lowerUrl.contains(".webp") -> "image/webp"
                        lowerUrl.contains(".svg") -> "image/svg+xml"
                        else -> "video/mp4" // Safely assume video/mp4 for unclassified video streaming endpoints
                    }
                }

                // Rapid HEAD probe for exact MimeType and File Size
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.instanceFollowRedirects = true
                    
                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        val length = conn.contentLengthLong
                        if (length > 0) size = length
                        val type = conn.contentType
                        if (!type.isNullOrBlank()) {
                            mimeType = type.substringBefore(";")
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    // Fail silently, fall back to guessed attributes
                }

                val finalMime = mimeType
                val finalSize = size
                val finalRes = if (resolution.isNotEmpty()) resolution else "Auto"

                val mediaItem = DetectedMedia(
                    url = url,
                    fileName = cleanFileName,
                    fileSize = finalSize,
                    mimeType = finalMime,
                    resolution = finalRes,
                    isStream = isM3u8,
                    isStreamDash = isDash,
                    quality = if (isM3u8) "HLS Adaptive" else if (isDash) "DASH Stream" else if (finalRes != "Auto") finalRes else "Original"
                )

                // Return back to Caller
                callback(mediaItem)

            } catch (e: Exception) {
                Log.e(TAG, "Error performing background media probing", e)
            }
        }
    }
}

object RequestInterceptorEngine {
    private const val TAG = "RequestInterceptorEngine"

    fun interceptAndSniff(request: WebResourceRequest) {
        val url = request.url?.toString() ?: return
        
        // Skip extension source and secure system requests
        if (url.startsWith("chrome-extension://") || url.startsWith("orion-extension://") || url.startsWith("orion://")) {
            return
        }

        val headers = request.requestHeaders
        if (MediaStreamDetector.isMediaRequest(url, headers)) {
            Log.d(TAG, "Media Stream Request Intercepted! URL: $url")
            
            MediaStreamDetector.detectMediaDetails(url) { detectedMedia ->
                NetworkSnifferEngine.notifyMediaDetected(detectedMedia)
            }
        }
    }
}
