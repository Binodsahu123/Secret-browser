package com.example.audioengine

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class AudioTrack(
    val title: String,
    val filePath: String,
    val artist: String = "Unknown Artist"
)

class AudioSessionManager(private val context: Context) {
    fun requestAudioFocus(): Boolean = true
    fun abandonAudioFocus() {}
}

class MediaNotificationManager(private val context: Context) {
    fun showNotification(trackName: String, isPlaying: Boolean) {
        // Publishes custom notification controls to media panel
    }
    fun dismissNotification() {}
}

class AudioPlayerEngine(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val sessionManager = AudioSessionManager(context)
    private val notificationManager = MediaNotificationManager(context)

    private val _playlist = MutableStateFlow<List<AudioTrack>>(emptyList())
    val playlist: StateFlow<List<AudioTrack>> = _playlist.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun setPlaylist(tracks: List<AudioTrack>, startIndex: Int = 0) {
        _playlist.value = tracks
        _currentTrackIndex.value = startIndex
        playCurrentTrack()
    }

    fun playCurrentTrack() {
        val index = _currentTrackIndex.value
        val tracks = _playlist.value
        if (index in tracks.indices) {
            val track = tracks[index]
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(track.filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        nextTrack()
                    }
                }
                _isPlaying.value = true
                sessionManager.requestAudioFocus()
                notificationManager.showNotification(track.title, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                notificationManager.showNotification(getCurrentTrackTitle(), false)
            } else {
                player.start()
                _isPlaying.value = true
                notificationManager.showNotification(getCurrentTrackTitle(), true)
            }
        }
    }

    fun nextTrack() {
        val tracks = _playlist.value
        if (tracks.isNotEmpty()) {
            _currentTrackIndex.value = (_currentTrackIndex.value + 1) % tracks.size
            playCurrentTrack()
        }
    }

    fun previousTrack() {
        val tracks = _playlist.value
        if (tracks.isNotEmpty()) {
            _currentTrackIndex.value = if (_currentTrackIndex.value - 1 < 0) tracks.size - 1 else _currentTrackIndex.value - 1
            playCurrentTrack()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        sessionManager.abandonAudioFocus()
        notificationManager.dismissNotification()
    }

    private fun getCurrentTrackTitle(): String {
        val idx = _currentTrackIndex.value
        val tracks = _playlist.value
        return if (idx in tracks.indices) tracks[idx].title else "Local Audio"
    }
}
