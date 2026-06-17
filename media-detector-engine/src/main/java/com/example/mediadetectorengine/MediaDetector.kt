package com.example.mediadetectorengine

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

data class DetectedMedia(
    val url: String,
    val fileName: String,
    val fileSize: Long = -1L,
    val mimeType: String = "application/octet-stream",
    val resolution: String = "",
    val bitrate: Int = 0,
    val duration: Long = 0L,
    val isStream: Boolean = false,
    val isStreamDash: Boolean = false,
    val quality: String = "Medium"
)

object MediaDetector {
    
    // JS to extract media elements on page load or on demand
    const val MEDIA_EXTRACTION_JS = """
        (function() {
            var mediaItems = [];
            
            // 1. Scan Video Elements
            var videos = document.getElementsByTagName('video');
            for (var i = 0; i < videos.length; i++) {
                var v = videos[i];
                if (v.src) {
                    mediaItems.push({
                        type: 'video',
                        url: v.src,
                        title: document.title || 'Video',
                        duration: v.duration || 0,
                        width: v.videoWidth || 0,
                        height: v.videoHeight || 0
                    });
                }
                var sources = v.getElementsByTagName('source');
                for (var j = 0; j < sources.length; j++) {
                    if (sources[j].src) {
                        mediaItems.push({
                            type: 'video',
                            url: sources[j].src,
                            title: document.title || 'VideoSource',
                            mime: sources[j].type
                        });
                    }
                }
            }
            
            // 2. Scan Audio Elements
            var audios = document.getElementsByTagName('audio');
            for (var i = 0; i < audios.length; i++) {
                var a = audios[i];
                if (a.src) {
                    mediaItems.push({
                        type: 'audio',
                        url: a.src,
                        title: document.title || 'Audio',
                        duration: a.duration || 0
                    });
                }
            }
            
            // 3. Scan Image Elements (Filter small icons, only capture prominent images)
            var imgs = document.getElementsByTagName('img');
            for (var i = 0; i < imgs.length; i++) {
                var img = imgs[i];
                if (img.src && img.src.substring(0, 4) === 'http') {
                    var w = img.naturalWidth || img.width || 0;
                    var h = img.naturalHeight || img.height || 0;
                    if (w > 150 && h > 150) { // prominent image
                        mediaItems.push({
                            type: 'image',
                            url: img.src,
                            title: img.alt || 'Web Image',
                            width: w,
                            height: h
                        });
                    }
                }
            }
            
            return JSON.stringify(mediaItems);
        })()
    """

    fun isSocialMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        // Only trigger YouTube if we are on an actual video watch, short, embed, or video URL path
        val isYoutubeVideo = (lower.contains("youtube.com") || lower.contains("youtu.be")) && 
                (lower.contains("watch?") || lower.contains("embed/") || lower.contains("/shorts/") || lower.contains("v=") || lower.contains("youtu.be/"))
                
        // Only trigger Instagram if we are on a reel or a specific post
        val isInstagramMedia = lower.contains("instagram.com") && (lower.contains("/reels/") || lower.contains("/reel/") || lower.contains("/p/"))
        
        // Only trigger Facebook if it's an actual video watch, reel or post with media
        val isFacebookVideo = lower.contains("facebook.com") && (lower.contains("/videos/") || lower.contains("/watch/") || lower.contains("/reel/") || lower.contains("/posts/"))
        
        // Only trigger TikTok if it's an actual video page
        val isTiktokVideo = lower.contains("tiktok.com") && (lower.contains("/video/") || lower.contains("/v/"))
        
        // Other video sharing platforms
        val isOtherVideo = lower.contains("vimeo.com/") || lower.contains("dailymotion.com/video/")
        
        return isYoutubeVideo || isInstagramMedia || isFacebookVideo || isTiktokVideo || isOtherVideo
    }

    fun detectFromUrl(url: String): DetectedMedia? {
        if (isSocialMediaUrl(url)) {
            val title = if (url.contains("youtube")) "YouTube_Video" 
                        else if (url.contains("instagram")) "Instagram_Media" 
                        else if (url.contains("facebook")) "Facebook_Video"
                        else if (url.contains("tiktok")) "TikTok_Video"
                        else "Social_Video"
            return DetectedMedia(
                url = url,
                fileName = "${title}_${System.currentTimeMillis()}.mp4",
                mimeType = "video/mp4",
                isStream = false,
                isStreamDash = false,
                quality = "Resolvables"
            )
        }

        val uri = Uri.parse(url)
        val path = uri.path ?: ""
        val fileName = getFileNameFromUrl(url)
        val ext = getExtension(fileName).lowercase(Locale.ROOT)
        
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }

        val isM3u8 = ext == "m3u8" || mimeType.contains("mpegurl", ignoreCase = true)
        val isDash = ext == "mpd" || mimeType.contains("dash+xml", ignoreCase = true)
        
        if (!isDownloadable(mimeType, ext)) return null

        val label = when {
            mimeType.startsWith("video/") -> "Video"
            mimeType.startsWith("audio/") -> "Audio"
            mimeType.startsWith("image/") -> "Image"
            mimeType.contains("pdf") -> "Document"
            isM3u8 || isDash -> "Streaming Playlist"
            else -> "Resource"
        }

        return DetectedMedia(
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            isStream = isM3u8,
            isStreamDash = isDash,
            quality = if (isM3u8) "HLS Adaptive" else if (isDash) "DASH Stream" else "Original"
        )
    }

    private fun isDownloadable(mimeType: String, ext: String): Boolean {
        if (mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType.startsWith("image/")) return true
        val checkExts = listOf("mp4", "mkv", "webm", "mp3", "wav", "m4a", "jpg", "jpeg", "png", "gif", "webp", "pdf", "zip", "rar", "tar", "gz", "apk", "doc", "docx", "xls", "xlsx", "m3u8", "mpd")
        return checkExts.contains(ext) || mimeType.contains("pdf") || mimeType.contains("zip") || mimeType.contains("octet-stream")
    }

    fun getFileNameFromUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val lastSegment = File(path).name
                if (lastSegment.isNotBlank()) {
                    return lastSegment
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        val cleanUrl = url.substringBefore("?").substringBefore("#")
        val lastIdx = cleanUrl.lastIndexOf('/')
        return if (lastIdx >= 0 && lastIdx < cleanUrl.length - 1) {
            cleanUrl.substring(lastIdx + 1)
        } else {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1)
        } else ""
    }
}
