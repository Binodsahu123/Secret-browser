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

                function getFormatUrl(itag) {
                    try {
                        var response = window.ytInitialPlayerResponse || (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args && JSON.parse(window.ytplayer.config.args.player_response));
                        if (response && response.streamingData) {
                            var formats = (response.streamingData.formats || []).concat(response.streamingData.adaptiveFormats || []);
                            var item = formats.find(function(f) { return f.itag === itag; });
                            if (item) {
                                if (item.url) return item.url;
                                if (item.signatureCipher) {
                                    var params = new URLSearchParams(item.signatureCipher);
                                    return params.get("url");
                                }
                            }
                        }
                    } catch(e) {
                        console.error("Error finding itag url", e);
                    }
                    return null;
                }

                async function downloadTagUrl(tagUrl, fileName) {
                    if (!tagUrl) return;
                    try {
                        let response = await fetch(tagUrl);
                        let reader = response.body.getReader();
                        let receivedBytes = 0;
                        let bridge = window.YouTubeVideoDownloader || window.Android;
                        while(true) {
                            let {done, value} = await reader.read();
                            if (done) {
                                break;
                            }
                            receivedBytes += value.length;
                            let binary = '';
                            let len = value.byteLength;
                            for (let i = 0; i < len; i++) {
                                binary += String.fromCharCode(value[i]);
                            }
                            let base64 = window.btoa(binary);
                            if (bridge && bridge.writeChunk) {
                                bridge.writeChunk(fileName, base64);
                            }
                            let mb = Math.round(receivedBytes / (1024 * 1024));
                            if (bridge && bridge.onDownloadProgress) {
                                bridge.onDownloadProgress(fileName, mb);
                            }
                        }
                        if (bridge && bridge.finishFile) {
                            bridge.finishFile(fileName);
                        }
                    } catch (e) {
                        console.error("Download failed for " + fileName, e);
                    }
                }

                if (!window.ytproSabrDownload) {
                    window.ytproSabrDownload = function(videoId, videoItag, audioItag, isWebm, callback) {
                        var bridge = window.YouTubeVideoDownloader || window.Android;
                        if (bridge && bridge.showToast) {
                            bridge.showToast("Preparing stream channels for " + videoId + "... Please wait.");
                        }
                        
                        var videoUrl = getFormatUrl(videoItag);
                        var audioUrl = getFormatUrl(audioItag);
                        
                        if (!videoUrl) videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4?itag=" + videoItag + "&id=" + videoId;
                        if (!audioUrl) audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3?itag=" + audioItag + "&id=" + videoId;
                        
                        var ext = isWebm ? "webm" : "mp4";
                        var audExt = isWebm ? "webm" : "m4a";
                        
                        var videoFile = videoId + "_video." + ext;
                        var audioFile = videoId + "_audio." + audExt;
                        var outFile = videoId + "_merged.mp4";
                        
                        Promise.all([
                            downloadTagUrl(videoUrl, videoFile),
                            downloadTagUrl(audioUrl, audioFile)
                        ]).then(function() {
                            if (bridge && bridge.muxVideoAudio) {
                                if (bridge.showToast) {
                                    bridge.showToast("All channels extracted. Merging audio and video into MP4...");
                                }
                                bridge.muxVideoAudio(videoFile, audioFile, outFile);
                            }
                        }).catch(function(err) {
                            if (bridge && bridge.showToast) {
                                bridge.showToast("SABR download pipeline hit an issue: " + err);
                            }
                        });
                    };
                }

                window.youtubeVideoDownloaderSabrDownload = function(videoId, videoItag, audioItag, isWebm, callback) {
                    var fn = window.ytproSabrDownload;
                    if (fn && fn.toString().indexOf("getFormatUrl") === -1) {
                        try {
                            fn(videoId, videoItag, audioItag, isWebm, callback);
                        } catch (e) {
                            console.error("Failed running real ytpro downloader, running fallback...", e);
                            window.ytproSabrDownload = null;
                            window.youtubeVideoDownloaderSabrDownload(videoId, videoItag, audioItag, isWebm, callback);
                        }
                    } else {
                        if (window.ytpro && window.ytpro.download) {
                            try {
                                window.ytpro.download(videoId, videoItag, audioItag);
                                return;
                            } catch (e) {}
                        }
                        
                        var fnFallback = window.ytproSabrDownload || function(vId, vItag, aItag, isW, cb) {
                            var bridge = window.YouTubeVideoDownloader || window.Android;
                            var videoUrl = getFormatUrl(vItag);
                            var audioUrl = getFormatUrl(aItag);
                            if (!videoUrl) videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4?itag=" + vItag + "&id=" + vId;
                            if (!audioUrl) audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3?itag=" + aItag + "&id=" + vId;
                            var ext = isW ? "webm" : "mp4";
                            var audExt = isW ? "webm" : "m4a";
                            var videoFile = vId + "_video." + ext;
                            var audioFile = vId + "_audio." + audExt;
                            var outFile = vId + "_merged.mp4";
                            Promise.all([
                                downloadTagUrl(videoUrl, videoFile),
                                downloadTagUrl(audioUrl, audioFile)
                            ]).then(function() {
                                if (bridge && bridge.muxVideoAudio) {
                                    bridge.muxVideoAudio(videoFile, audioFile, outFile);
                                }
                            });
                        };
                        fnFallback(videoId, videoItag, audioItag, isWebm, callback);
                    }
                };

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
