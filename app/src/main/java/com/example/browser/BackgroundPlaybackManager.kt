package com.example.browser

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log

class BackgroundPlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentUrl: String = ""
    private var currentTitle: String = "YouTube Browser Audio"
    private var isPrepared = false

    companion object {
        const val CHANNEL_ID = "com.example.browser.playback"
        const val NOTIFICATION_ID = 4040
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_STOP = "action_stop"
        const val ACTION_SEEK_TO = "action_seek_to"
        
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        setupMediaSession()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SwiftBrowser Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background audio of browser media"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = MediaSession(this, "BackgroundPlaybackSession").apply {
                isActive = true
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        play()
                    }

                    override fun onPause() {
                        pause()
                    }

                    override fun onStop() {
                        stop()
                    }

                    override fun onSeekTo(pos: Long) {
                        seekTo(pos)
                    }
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_STOP -> stop()
                "action_play_url" -> {
                    val url = intent.getStringExtra("url") ?: ""
                    val title = intent.getStringExtra("title") ?: "YouTube Audio"
                    playNewUrl(url, title)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun playNewUrl(url: String, title: String) {
        if (url.isEmpty()) return
        
        currentUrl = url
        currentTitle = title
        isPrepared = false

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(url)
                setOnPreparedListener {
                    isPrepared = true
                    play()
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e("BackgroundPlayback", "Error playing audio", e)
            }
        }
        
        showNotification(true)
    }

    fun play() {
        if (isPrepared && mediaPlayer != null && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            showNotification(true)
        }
    }

    fun pause() {
        if (isPrepared && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            updatePlaybackState(PlaybackState.STATE_PAUSED)
            showNotification(false)
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        updatePlaybackState(PlaybackState.STATE_STOPPED)
        stopForeground(true)
        stopSelf()
    }

    fun seekTo(position: Long) {
        if (isPrepared && mediaPlayer != null) {
            mediaPlayer?.seekTo(position.toInt())
            updatePlaybackState(if (mediaPlayer?.isPlaying == true) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        }
    }

    private fun updatePlaybackState(state: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val stateBuilder = PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO)
                .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
            mediaSession?.setPlaybackState(stateBuilder.build())
        }
    }

    private fun showNotification(isPlaying: Boolean) {
        val playPauseIntent = if (isPlaying) {
            Intent(this, BackgroundPlaybackService::class.java).apply { action = ACTION_PAUSE }
        } else {
            Intent(this, BackgroundPlaybackService::class.java).apply { action = ACTION_PLAY }
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"
        
        val stopIntent = Intent(this, BackgroundPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playPauseAction = Notification.Action.Builder(
                playPauseIcon, playPauseLabel, playPausePendingIntent
            ).build()
            
            val stopAction = Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent
            ).build()

            val notificationBuilder = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(currentTitle)
                .setContentText("Playing in background")
                .setOngoing(isPlaying)
                .addAction(playPauseAction)
                .addAction(stopAction)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                notificationBuilder.setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(mediaSession?.sessionToken)
                        .setShowActionsInCompactView(0, 1)
                )
            }

            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        } else {
            // Older versions fallback
            val notificationBuilder = Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(currentTitle)
                .setContentText("Playing in background")
                .setOngoing(isPlaying)

            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        mediaPlayer?.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession?.release()
        }
        super.onDestroy()
    }

    inner class PlaybackBinder : Binder() {
        fun getService(): BackgroundPlaybackService = this@BackgroundPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return PlaybackBinder()
    }
}

object BackgroundPlaybackManager {

    fun playAudio(context: Context, audioUrl: String, title: String) {
        val intent = Intent(context, BackgroundPlaybackService::class.java).apply {
            action = "action_play_url"
            putExtra("url", audioUrl)
            putExtra("title", title)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopAudio(context: Context) {
        val intent = Intent(context, BackgroundPlaybackService::class.java).apply {
            action = BackgroundPlaybackService.ACTION_STOP
        }
        context.startService(intent)
    }
}
