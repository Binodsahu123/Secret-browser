package com.example.browser

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Locale
import java.io.File

@Composable
fun LocalViewerOverlay(
    activeFile: ActiveViewerFile,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)) // Slate dark aesthetic
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val icon = when {
                            activeFile.mimeType.startsWith("video/") -> Icons.Default.Movie
                            activeFile.mimeType.startsWith("audio/") -> Icons.Default.MusicNote
                            activeFile.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                            else -> Icons.Default.InsertDriveFile
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activeFile.fileName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeFile.mimeType.uppercase(),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val lowerName = activeFile.fileName.lowercase()
                    val lowerMime = activeFile.mimeType.lowercase()
                    val isTextOrCode = lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                            lowerName.endsWith(".xml") || lowerName.endsWith(".json") ||
                            lowerName.endsWith(".txt") || lowerName.endsWith(".html") ||
                            lowerName.endsWith(".css") || lowerName.endsWith(".js") ||
                            lowerName.endsWith(".md") || lowerName.endsWith(".properties") ||
                            lowerName.endsWith(".gradle") || lowerName.endsWith(".kts") ||
                            lowerMime.startsWith("text/") || lowerMime.contains("javascript") ||
                            lowerMime.contains("json") || lowerMime.contains("xml")

                    val context = LocalContext.current
                    if (isTextOrCode) {
                        IconButton(
                            onClick = {
                                try {
                                    val content = File(activeFile.filePath).readText(Charsets.UTF_8)
                                    PdfUtility.printTextToPdf(context, activeFile.fileName, content)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot read file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Save to PDF",
                                tint = Color(0xFFF43F5E)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Viewer",
                            tint = Color.White
                        )
                    }
                }

                // Viewer Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val lowerName = activeFile.fileName.lowercase()
                    val lowerMime = activeFile.mimeType.lowercase()
                    val isTextOrCode = lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                            lowerName.endsWith(".xml") || lowerName.endsWith(".json") ||
                            lowerName.endsWith(".txt") || lowerName.endsWith(".html") ||
                            lowerName.endsWith(".css") || lowerName.endsWith(".js") ||
                            lowerName.endsWith(".md") || lowerName.endsWith(".properties") ||
                            lowerName.endsWith(".gradle") || lowerName.endsWith(".kts") ||
                            lowerMime.startsWith("text/") || lowerMime.contains("javascript") ||
                            lowerMime.contains("json") || lowerMime.contains("xml")

                    when {
                        activeFile.mimeType.startsWith("video/") -> {
                            VideoPlayerView(filePath = activeFile.filePath)
                        }
                        activeFile.mimeType.startsWith("audio/") -> {
                            AudioPlayerView(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                viewModel = viewModel
                            )
                        }
                        activeFile.mimeType.contains("pdf") -> {
                            PdfRendererView(filePath = activeFile.filePath)
                        }
                        activeFile.mimeType.startsWith("image/") || isImageFile(activeFile.fileName) -> {
                            ImageOfflineViewer(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                mimeType = activeFile.mimeType
                            )
                        }
                        isTextOrCode -> {
                            TextCodeRendererView(
                                filePath = activeFile.filePath,
                                fileName = activeFile.fileName,
                                mimeType = activeFile.mimeType
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Unsupported offline file type:\n${activeFile.fileName}",
                                    textAlign = TextAlign.Center,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerView(filePath: String) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var isControlsLocked by remember { mutableStateOf(false) }

    // MX Player style Gesture feedback values
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var currentBrightnessValue by remember { mutableFloatStateOf(0.5f) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var currentVolumeValue by remember { mutableIntStateOf(0) }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Init values
    LaunchedEffect(Unit) {
        currentVolumeValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val activity = context as? Activity
        val lp = activity?.window?.attributes
        currentBrightnessValue = lp?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
    }

    // Ticker for seekbar
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
            .pointerInput(isControlsLocked) {
                if (isControlsLocked) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        // Determine left (brightness) or right (volume)
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
                            // Adjust Brightness
                            val delta = -dragAmount.y / 800f
                            val newBright = (currentBrightnessValue + delta).coerceIn(0.01f, 1.0f)
                            currentBrightnessValue = newBright
                            val activity = context as? Activity
                            activity?.let { act ->
                                act.runOnUiThread {
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBright
                                    act.window.attributes = lp
                                }
                            }
                        } else {
                            // Adjust volume
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
        // Native Player engine
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
                        android.net.Uri.parse(filePath)
                    } else {
                        android.net.Uri.fromFile(java.io.File(filePath))
                    }
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        mp.isLooping = true
                        start()
                        isPlaying = true
                    }
                    videoViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Native overlay feedback for gesture controllers
        if (showBrightnessFeedback) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.Yellow)
                    Text("Brightness: ${(currentBrightnessValue * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showVolumeFeedback) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF818CF8))
                    Text("Volume: $currentVolumeValue / $maxVolume", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // MX Player Controls (Bottom & Sidebars)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Lock Option (on Left Side)
            IconButton(
                onClick = { isControlsLocked = !isControlsLocked },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock controls",
                    tint = if (isControlsLocked) Color.Red else Color.White
                )
            }

            if (!isControlsLocked) {
                // Bottom control panel bar
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Slider and tracker text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatDuration(position),
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Slider(
                                value = position.toFloat(),
                                onValueChange = {
                                    videoViewInstance?.seekTo(it.toInt())
                                    position = it.toInt()
                                },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF818CF8),
                                    activeTrackColor = Color(0xFF818CF8)
                                    ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatDuration(duration),
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }

                        // Playback Control Buttons
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = {
                                val target = (position - 10000).coerceAtLeast(0)
                                videoViewInstance?.seekTo(target)
                                position = target
                            }) {
                                Icon(Icons.Default.Replay10, contentDescription = "Back 10s", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = {
                                    videoViewInstance?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.background(Color(0xFF818CF8), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play or Pause",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = {
                                val target = (position + 10000).coerceAtMost(duration)
                                videoViewInstance?.seekTo(target)
                                position = target
                            }) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerView(
    filePath: String,
    fileName: String,
    viewModel: BrowserViewModel
) {
    var isPlaying by remember { viewModel.isMediaPlaying }
    var duration by remember { viewModel.mediaDuration }
    var position by remember { viewModel.mediaPosition }
    var trackName by remember { viewModel.mediaTrackName }
    val isShuffle = viewModel.isShuffleEnabled.value
    val isRepeat = viewModel.isRepeatEnabled.value

    // Auto trigger play once entered
    LaunchedEffect(filePath) {
        viewModel.initAudioPlayer(filePath, fileName)
    }

    // Vinyl spinning rotation animation helper
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    val animatedRotation = if (isPlaying) angle else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Large Spinning CD/Vinyl UI
        Box(
            modifier = Modifier
                .size(220.dp)
                .rotate(animatedRotation)
                .background(
                    Brush.sweepGradient(
                        listOf(Color(0xFF1E1E2F), Color(0xFF0F0F1A), Color(0xFF3B3B4F), Color(0xFF1E1E2F))
                    ),
                    CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl grooves
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), CircleShape)
            )
            // Center label art
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF818CF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Text title & artist info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = trackName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Orion Music Player • Background Continuous Playback",
                color = Color(0xFF818CF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Track SeekBar controls
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatDuration(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text(text = formatDuration(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Slider(
                value = position.toFloat(),
                onValueChange = {
                    viewModel.seekAudioTo(it.toInt())
                },
                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF818CF8),
                    activeTrackColor = Color(0xFF818CF8),
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Action controls toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) Color(0xFF818CF8) else Color.White.copy(alpha = 0.4f)
                )
            }

            IconButton(onClick = { viewModel.seekAudioTo((position - 10000).coerceAtLeast(0)) }) {
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
            }

            IconButton(
                onClick = { viewModel.toggleAudioPlayback() },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF818CF8), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play Pause",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { viewModel.seekAudioTo((position + 10000).coerceAtMost(duration)) }) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
            }

            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeat) Color(0xFF818CF8) else Color.White.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun PdfRendererView(filePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmapList = remember(filePath) {
        val bitmaps = mutableStateListOf<Bitmap>()
        try {
            val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
                android.net.Uri.parse(filePath)
            } else {
                android.net.Uri.fromFile(File(filePath))
            }
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val scale = 2.0f
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bitmaps
    }

    if (bitmapList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF818CF8))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            itemsIndexed(bitmapList) { idx, bitmap ->
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${idx + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

// Format duration helper
private fun formatDuration(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun ImageOfflineViewer(
    filePath: String,
    fileName: String,
    mimeType: String
) {
    val context = LocalContext.current
    val bitmap = remember(filePath) {
        try {
            android.graphics.BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var rotationState by remember { mutableFloatStateOf(0f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val file = remember(filePath) { File(filePath) }
    val info = remember(filePath) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(filePath, options)
        val resolution = if (options.outWidth > 0 && options.outHeight > 0) "${options.outWidth} x ${options.outHeight}" else "Unknown"
        val format = options.outMimeType ?: mimeType
        val sizeText = formatFileSize(file.length())
        ImageInfo(resolution, sizeText, format)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Unable to load image", color = Color.White)
            }
        } else {
            // Main zooming image display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .wrapContentSize(Alignment.Center)
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = fileName,
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotationState
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            // Quick Actions Overlay (Bottom bar)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { rotationState = (rotationState + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate Clockwise", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Image Details", tint = Color.White)
                    }
                    IconButton(onClick = {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Image"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Sharing failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Image Path", file.absolutePath)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied file path to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = Color.White)
                    }
                }
            }
        }

        // Info popup
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                confirmButton = {
                    Button(onClick = { showInfoDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Image Information") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File Name: $fileName", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Resolution: ${info.resolution}", color = MaterialTheme.colorScheme.onSurface)
                        Text("File Size: ${info.size}", color = MaterialTheme.colorScheme.onSurface)
                        Text("Format: ${info.format}", color = MaterialTheme.colorScheme.onSurface)
                        Text("Stored At: ${file.parent}", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    }
}

data class ImageInfo(val resolution: String, val size: String, val format: String)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIdx = 0
    while (value >= 1024 && unitIdx < units.size - 1) {
        value /= 1024
        unitIdx++
    }
    return String.format("%.2f %s", value, units[unitIdx])
}

private fun isImageFile(fileName: String): Boolean {
    val extensions = listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico", "avif", "heic", "heif", "tiff")
    val ext = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)
    return extensions.contains(ext)
}

@Composable
fun TextCodeRendererView(filePath: String, fileName: String, mimeType: String) {
    val fileContent = remember(filePath) {
        try {
            File(filePath).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            "Failed to read file contents: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B0F19), // Dark developer canvas
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val lines = remember(fileContent) { fileContent.split("\n") }
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(4, ' ') + "  ",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.width(38.dp)
                            )
                            Text(
                                text = line,
                                color = Color(0xFF38BDF8), // Tech cyan syntax
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
