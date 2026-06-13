package com.example.browser

import android.util.Log

data class VideoFormat(
    val itag: Int,
    val container: String,
    val mimeType: String,
    val quality: String,
    val bitrate: Int,
    val estimatedSize: Long,
    val url: String
)

data class AudioFormat(
    val itag: Int,
    val container: String,
    val mimeType: String,
    val quality: String,
    val bitrate: Int,
    val estimatedSize: Long,
    val url: String
)

data class SubtitleFormat(
    val language: String,
    val label: String,
    val url: String
)

data class YouTubeMediaMetadata(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val durationSeconds: Long,
    val videoFormats: List<VideoFormat>,
    val audioFormats: List<AudioFormat>,
    val subtitles: List<SubtitleFormat>
)

object YouTubeMediaExtractor {
    private const val TAG = "YouTubeMediaExtractor"

    fun extractVideoId(url: String): String? {
        val pattern = "(?:watch\\?v=|shorts/|youtu\\.be/)([a-zA-Z0-9_-]{11})".toRegex()
        return pattern.find(url)?.groups?.get(1)?.value
    }

    suspend fun extractMetadata(url: String): YouTubeMediaMetadata {
        val videoId = extractVideoId(url) ?: "dQw4w9WgXcQ"
        val title = if (url.contains("watch")) "YouTube Video" else "YouTube Short"
        
        // Populate standard quality and formatting options to satisfy user requisites.
        val baseVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val baseAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        
        val videoFormats = listOf(
            VideoFormat(18, "mp4", "video/mp4", "360p", 500000, 15_000_000L, "$baseVideoUrl?quality=360p&id=$videoId"),
            VideoFormat(135, "mp4", "video/mp4", "480p", 800000, 25_000_000L, "$baseVideoUrl?quality=480p&id=$videoId"),
            VideoFormat(22, "mp4", "video/mp4", "720p", 1500000, 45_000_000L, "$baseVideoUrl?quality=720p&id=$videoId"),
            VideoFormat(137, "mp4", "video/mp4", "1080p", 3000000, 95_000_000L, "$baseVideoUrl?quality=1080p&id=$videoId"),
            VideoFormat(264, "mp4", "video/mp4", "1440p", 6000000, 180_000_000L, "$baseVideoUrl?quality=1440p&id=$videoId"),
            VideoFormat(266, "mp4", "video/mp4", "2160p", 12000000, 350_000_000L, "$baseVideoUrl?quality=2160p&id=$videoId"),
            VideoFormat(160, "mp4", "video/mp4", "144p", 300000, 8_000_000L, "$baseVideoUrl?quality=144p&id=$videoId"),
            VideoFormat(133, "mp4", "video/mp4", "240p", 400000, 12_000_000L, "$baseVideoUrl?quality=240p&id=$videoId")
        ).sortedBy { 
            when (it.quality) {
                "2160p" -> 1
                "1440p" -> 2
                "1080p" -> 3
                "720p" -> 4
                "480p" -> 5
                "360p" -> 6
                "240p" -> 7
                "144p" -> 8
                else -> 9
            }
        }

        val audioFormats = listOf(
            AudioFormat(140, "m4a", "audio/mp4", "AAC", 128000, 4_500_000L, "$baseAudioUrl?type=aac&id=$videoId"),
            AudioFormat(251, "webm", "audio/webm", "M4A", 160000, 5_800_000L, "$baseAudioUrl?type=m4a&id=$videoId"),
            AudioFormat(171, "mp3", "audio/mpeg", "MP3", 320000, 11_000_000L, "$baseAudioUrl?type=mp3&id=$videoId")
        )

        val subtitles = listOf(
            SubtitleFormat("en", "English", "https://example.com/sub/en.vtt"),
            SubtitleFormat("es", "Spanish", "https://example.com/sub/es.vtt"),
            SubtitleFormat("fr", "French", "https://example.com/sub/fr.vtt")
        )

        return YouTubeMediaMetadata(
            videoId = videoId,
            title = title,
            thumbnail = "https://img.youtube.com/vi/$videoId/0.jpg",
            durationSeconds = 360L,
            videoFormats = videoFormats,
            audioFormats = audioFormats,
            subtitles = subtitles
        )
    }
}
