package com.example.browser

import android.webkit.WebView
import org.json.JSONObject

object DesktopVideoStateManager {
    data class VideoState(
        val currentTime: Double,
        val isPlaying: Boolean,
        val isMuted: Boolean
    )

    fun getCaptureScript(): String {
        return """
            (function() {
                var v = document.querySelector('video');
                if (!v) return null;
                return JSON.stringify({
                    time: v.currentTime,
                    playing: !v.paused,
                    muted: v.muted
                });
            })();
        """.trimIndent()
    }

    fun getRestoreScript(state: VideoState): String {
        val playCommand = if (state.isPlaying) "v.play();" else "v.pause();"
        return """
            (function() {
                var v = document.querySelector('video');
                if (v) {
                    v.currentTime = ${state.currentTime};
                    v.muted = ${state.isMuted};
                    // Attempt playback restoration after short delay
                    setTimeout(function() {
                        try {
                            $playCommand
                        } catch(e) {
                            console.error("Video playback restore failed: " + e.message);
                        }
                    }, 500);
                }
            })();
        """.trimIndent()
    }

    fun parseState(jsonString: String?): VideoState? {
        if (jsonString.isNullOrEmpty() || jsonString == "null" || jsonString == "undefined") return null
        try {
            val cleanJson = if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                jsonString.replace("\\\"", "\"").removeSurrounding("\"")
            } else {
                jsonString
            }
            val obj = JSONObject(cleanJson)
            return VideoState(
                currentTime = obj.optDouble("time", 0.0),
                isPlaying = obj.optBoolean("playing", false),
                isMuted = obj.optBoolean("muted", false)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
