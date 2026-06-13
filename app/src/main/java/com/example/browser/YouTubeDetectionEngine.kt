package com.example.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val duration: Double,
    val currentUrl: String
)

class YouTubeDetectionEngine {
    private val _detectedVideo = MutableStateFlow<YouTubeVideoInfo?>(null)
    val detectedVideo = _detectedVideo.asStateFlow()

    fun updateDetectedVideo(video: YouTubeVideoInfo?) {
        _detectedVideo.value = video
    }

    fun isYouTubeUrl(url: String?): Boolean {
        if (url == null) return false
        return url.contains("youtube.com/watch") ||
                url.contains("m.youtube.com/watch") ||
                url.contains("www.youtube.com/watch") ||
                url.contains("youtube.com/shorts/") ||
                url.contains("youtu.be/")
    }

    fun getInjectionJs(): String {
        return """
            (function() {
                function getYouTubeVideoId(url) {
                    if (!url) return '';
                    var match = url.match(/(?:watch\?v=|shorts\/|youtu\.be\/)([a-zA-Z0-9_-]{11})/);
                    return match ? match[1] : '';
                }

                function checkVideo() {
                    var currentUrl = window.location.href;
                    var videoId = getYouTubeVideoId(currentUrl);
                    if (!videoId) {
                        return;
                    }

                    var videoEl = document.querySelector('video');
                    var titleEl = document.querySelector('h1.media-item-metadata-title, yt-formatted-string.ytd-video-primary-info-renderer, h1.ytd-watch-metadata, .slim-video-metadata-title, title');
                    var title = titleEl ? (titleEl.innerText || titleEl.textContent) : document.title;
                    if (title && title.endsWith(' - YouTube')) {
                        title = title.substring(0, title.length - 10);
                    }
                    var duration = videoEl ? videoEl.duration : 0;
                    var thumbnail = 'https://img.youtube.com/vi/' + videoId + '/0.jpg';

                    if (window.lastReportedVideoId !== videoId || window.lastReportedUrl !== currentUrl || (videoEl && Math.abs((window.lastReportedDuration || 0) - duration) > 1)) {
                        window.lastReportedVideoId = videoId;
                        window.lastReportedUrl = currentUrl;
                        window.lastReportedDuration = duration;
                        if (window.YouTubeDetectionBridge) {
                            window.YouTubeDetectionBridge.onVideoDetected(videoId, title, thumbnail, duration, currentUrl);
                        }
                    }
                }

                if (!window.ytObserverAdded) {
                    window.ytObserverAdded = true;
                    setInterval(checkVideo, 1000);
                    window.addEventListener('yt-navigate-finish', checkVideo);
                    window.addEventListener('popstate', checkVideo);
                    window.addEventListener('hashchange', checkVideo);
                    var observer = new MutationObserver(function() {
                        checkVideo();
                    });
                    observer.observe(document.body, { childList: true, subtree: true });
                }
                checkVideo();
            })();
        """.trimIndent()
    }

    inner class YouTubeDetectionBridge(private val onDetected: (YouTubeVideoInfo) -> Unit) {
        @JavascriptInterface
        fun onVideoDetected(videoId: String, title: String, thumbnail: String, duration: Double, currentUrl: String) {
            val videoInfo = YouTubeVideoInfo(
                videoId = videoId,
                title = title,
                thumbnail = thumbnail,
                duration = duration,
                currentUrl = currentUrl
            )
            onDetected(videoInfo)
        }
    }
}
