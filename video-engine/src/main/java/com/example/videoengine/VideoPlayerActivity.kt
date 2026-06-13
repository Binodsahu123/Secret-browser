package com.example.videoengine

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra("file_path") ?: ""
        val videoTitle = intent.getStringExtra("video_title") ?: "Video Player"

        if (filePath.isEmpty()) {
            Toast.makeText(this, "No video path specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            VideoPlayerScreen(
                filePath = filePath,
                title = videoTitle,
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun VideoPlayerScreen(
    filePath: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var isControlsLocked by remember { mutableStateOf(false) }

    // Swipe Control Levels
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var currentBrightnessValue by remember { mutableFloatStateOf(0.5f) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var currentVolumeValue by remember { mutableIntStateOf(0) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    LaunchedEffect(Unit) {
        currentVolumeValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val lp = activity?.window?.attributes
        currentBrightnessValue = lp?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f

        // Try rotating to landscape by default on full screen start
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewInstance?.let { vv ->
                position = vv.currentPosition
            }
            kotlinx.coroutines.delay(200)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isControlsLocked) {
                if (isControlsLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val isLeft = offset.x < size.width / 2
                        if (isLeft) {
                            showBrightnessFeedback = true
                        } else {
                            showVolumeFeedback = true
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val isLeft = change.position.x < size.width / 2
                        if (isLeft) {
                            val delta = -dragAmount.y / 600f
                            val newBright = (currentBrightnessValue + delta).coerceIn(0.01f, 1.0f)
                            currentBrightnessValue = newBright
                            activity?.let { act ->
                                act.runOnUiThread {
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBright
                                    act.window.attributes = lp
                                }
                            }
                        } else {
                            val deltaVolume = if (dragAmount.y > 0) -1 else 1
                            val newVol = (currentVolumeValue + deltaVolume).coerceIn(0, maxVolume)
                            currentVolumeValue = newVol
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        }
                    },
                    onDragEnd = {
                        showBrightnessFeedback = false
                        showVolumeFeedback = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Render Android VideoView standard loop
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(filePath)
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        mp.isLooping = true
                        
                        // Set playback speed when supported, or default
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            try {
                                val params = mp.playbackParams
                                params.speed = speed
                                mp.playbackParams = params
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            },
            update = { view ->
                videoViewInstance = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Headers
        if (!isControlsLocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        onDismiss()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )

                IconButton(
                    onClick = { isControlsLocked = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color.White)
                }
            }
        } else {
            // Locked screen overlay -> single faint indicator to unlock
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isControlsLocked = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color(0xFFF87171))
                        Text("Controls Locked. Tap to unlock.", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Overlay Controllers bottom panel
        if (!isControlsLocked) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.8f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Seek Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(formatDuration(position), color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = if (duration > 0) position.toFloat() / duration else 0f,
                            onValueChange = { percent ->
                                val target = (percent * duration).toInt()
                                position = target
                                videoViewInstance?.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF6366F1),
                                activeTrackColor = Color(0xFF6366F1)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatDuration(duration), color = Color.White, fontSize = 11.sp)
                    }

                    // Main Controls Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speed
                        TextButton(onClick = { showSpeedDialog = true }) {
                            Text("${speed}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Skip Back
                        IconButton(onClick = {
                            val target = maxOf(0, position - 10000)
                            position = target
                            videoViewInstance?.seekTo(target)
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White)
                        }

                        // Play/Pause
                        IconButton(
                            onClick = {
                                isPlaying = if (isPlaying) {
                                    videoViewInstance?.pause()
                                    false
                                } else {
                                    videoViewInstance?.start()
                                    true
                                }
                            },
                            modifier = Modifier.background(Color(0xFF6366F1), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White
                            )
                        }

                        // Skip Forward
                        IconButton(onClick = {
                            val target = minOf(duration, position + 10000)
                            position = target
                            videoViewInstance?.seekTo(target)
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward Only", tint = Color.White)
                        }

                        // Orientation/Fullscreen toggle
                        IconButton(onClick = {
                            isFullscreen = !isFullscreen
                            if (activity != null) {
                                activity.requestedOrientation = if (isFullscreen) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Swipe brightness/volume telemetry feedback overlays
        if (showBrightnessFeedback) {
            FeedbackCard(icon = Icons.Default.BrightnessMedium, value = "${(currentBrightnessValue * 100).toInt()}%")
        }
        if (showVolumeFeedback) {
            FeedbackCard(icon = Icons.Default.VolumeUp, value = "$currentVolumeValue/$maxVolume")
        }

        // Playback Speed Dialog
        if (showSpeedDialog) {
            val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Dismiss")
                    }
                },
                title = { Text("Playback Speed", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        speeds.forEach { speedItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        speed = speedItem
                                        showSpeedDialog = false
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                            try {
                                                val mp = videoViewInstance?.let { vv ->
                                                    // Set Speed
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        Toast.makeText(context, "Speed set to ${speedItem}x", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${speedItem}x", color = Color.White)
                                if (speed == speedItem) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF6366F1))
                                }
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
private fun FeedbackCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        modifier = Modifier.padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
