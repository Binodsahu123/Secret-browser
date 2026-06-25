package com.example.downloaduiengine

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }

    // Waveform animation state
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 1500 },
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    LaunchedEffect(filePath) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val player = MediaPlayer.create(context, Uri.fromFile(file))
                if (player != null) {
                    mediaPlayer = player
                    duration = player.duration
                    
                    // Periodic polling
                    while (true) {
                        if (player.isPlaying) {
                            currentPos = player.currentPosition
                        }
                        delay(250)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerScreen", "Error playing audio", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val formattedCurrentPos = remember(currentPos) { formatTime(currentPos) }
    val formattedDuration = remember(duration) { formatTime(duration) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F14))
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visualizer Card with clean animated wave shapes
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val midY = size.height / 2f
                        val width = size.width
                        val barsCount = 30
                        val barWidth = 6.dp.toPx()
                        val spacing = 4.dp.toPx()
                        val totalSpacing = (barsCount - 1) * spacing
                        val startX = (width - (barsCount * barWidth + totalSpacing)) / 2f

                        for (i in 0 until barsCount) {
                            val x = startX + i * (barWidth + spacing)
                            val multiplier = if (isPlaying) {
                                Math.sin((i.toDouble() / 4.0) + waveOffset).toFloat().coerceIn(0.1f, 1.0f)
                            } else {
                                0.15f
                            }
                            val height = (size.height * 0.4f) * multiplier
                            drawRoundRect(
                                color = Color(0xFFFF2E2E).copy(alpha = if (isPlaying) 0.8f else 0.4f),
                                topLeft = Offset(x, midY - height / 2f),
                                size = androidx.compose.ui.geometry.Size(barWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Metadata text
            Text(
                text = fileName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Local Audio File",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Time Slider Scrubbing
            Slider(
                value = currentPos.toFloat(),
                onValueChange = { pos ->
                    currentPos = pos.toInt()
                    mediaPlayer?.seekTo(pos.toInt())
                },
                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF2E2E), activeTrackColor = Color(0xFFFF2E2E)),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formattedCurrentPos, color = Color.Gray, fontSize = 11.sp)
                Text(formattedDuration, color = Color.Gray, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val prev = (currentPos - 10000).coerceAtLeast(0)
                        mediaPlayer?.seekTo(prev)
                        currentPos = prev
                    },
                    modifier = Modifier.background(Color(0xFF1E1E28), CircleShape)
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        val player = mediaPlayer ?: return@IconButton
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFF2E2E), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val next = (currentPos + 10000).coerceAtMost(duration)
                        mediaPlayer?.seekTo(next)
                        currentPos = next
                    },
                    modifier = Modifier.background(Color(0xFF1E1E28), CircleShape)
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val secTotal = ms / 1000
    val min = secTotal / 60
    val sec = secTotal % 60
    return String.format("%02d:%02d", min, sec)
}
