package com.example.browser

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

object VideoStateManager {
    private const val TAG = "VideoStateManager"
    private val videoPositions = ConcurrentHashMap<String, Double>() // tabId -> position (seconds)
    private val videoPlayingState = ConcurrentHashMap<String, Boolean>() // tabId -> playing

    fun captureState(tabId: String, webView: WebView?) {
        if (webView == null) return
        webView.evaluateJavascript(
            """
            (function() {
                var v = document.querySelector('video');
                if (v) {
                    return JSON.stringify({
                        currentTime: v.currentTime,
                        paused: v.paused
                    });
                }
                return null;
            })();
            """.trimIndent()
        ) { result ->
            if (result != null && result != "null") {
                try {
                    // Result is a JSON string, let's parse it simply
                    val clean = result.removeSurrounding("\"").replace("\\\"", "\"").trim()
                    if (clean.startsWith("{") && clean.endsWith("}")) {
                        val items = clean.removeSurrounding("{", "}").split(",")
                        var time = 0.0
                        var paused = false
                        for (item in items) {
                            val parts = item.split(":")
                            if (parts.size >= 2) {
                                val key = parts[0].trim().removeSurrounding("\"")
                                val value = parts.subList(1, parts.size).joinToString(":").trim().removeSurrounding("\"")
                                if (key == "currentTime") {
                                    time = value.toDoubleOrNull() ?: 0.0
                                } else if (key == "paused") {
                                    paused = value.trim().equals("true", ignoreCase = true)
                                }
                            }
                        }
                        videoPositions[tabId] = time
                        videoPlayingState[tabId] = !paused
                        Log.d(TAG, "Captured video state for tab $tabId: position=$time, playing=${!paused}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing captured video state", e)
                }
            }
        }
    }

    fun restoreState(tabId: String, webView: WebView?) {
        if (webView == null) return
        val pos = videoPositions[tabId] ?: return
        val isPlaying = videoPlayingState[tabId] ?: true
        Log.i(TAG, "Restoring video state for tab $tabId to position $pos, isPlaying $isPlaying")
        webView.postDelayed({
            webView.evaluateJavascript(
                """
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        v.currentTime = $pos;
                        if ($isPlaying) {
                            v.play();
                        } else {
                            v.pause();
                        }
                        return true;
                    }
                    return false;
                })();
                """.trimIndent()
            , null)
        }, 800) // Small delay to let the page load/buffer first
    }

    private data class VideoStateData(val currentTime: Double, val paused: Boolean)
}

object MediaSessionManager {
    private const val TAG = "MediaSessionManager"
    private var mediaSession: MediaSession? = null

    fun initialize(context: Context) {
        if (mediaSession != null) return
        try {
            mediaSession = MediaSession(context, "OrionBrowserMediaSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                
                val state = PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_STOPPED, 0, 1.0f)
                    .build()
                setPlaybackState(state)
                
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        Log.i(TAG, "MediaSession: onPlay")
                        // Resume current active webview video play
                    }
                    override fun onPause() {
                        Log.i(TAG, "MediaSession: onPause")
                        // Pause current active webview video play
                    }
                })
                isActive = true
            }
            Log.i(TAG, "MediaSession initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaSession", e)
        }
    }

    fun updateState(isPlaying: Boolean, positionMs: Long) {
        mediaSession?.let { session ->
            val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val state = PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(stateCode, positionMs, 1.0f)
                .build()
            session.setPlaybackState(state)
        }
    }

    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        Log.i(TAG, "MediaSession released")
    }
}

class FullscreenVideoController(
    private val mainContainer: ViewGroup,
    private val fullscreenContainer: ViewGroup
) {
    private var customView: View? = null
    private var customViewCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    fun onShowCustomView(view: View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        
        mainContainer.visibility = View.GONE
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenContainer.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun onHideCustomView() {
        val view = customView ?: return
        customView = null
        
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        mainContainer.visibility = View.VISIBLE
        
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    fun isFullscreen(): Boolean = customView != null
}

object OrientationStatePreserver {
    private const val TAG = "OrientationState"
    private var lockedOrientation: Int? = null

    fun lockOrientation(activity: android.app.Activity, orientation: Int) {
        activity.requestedOrientation = orientation
        lockedOrientation = orientation
        Log.d(TAG, "Locked activity orientation to $orientation")
    }

    fun unlockOrientation(activity: android.app.Activity) {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        lockedOrientation = null
        Log.d(TAG, "Unlocked activity orientation")
    }
}
