package com.example.browser

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeDownloadBottomSheet(
    videoInfo: YouTubeVideoInfo,
    onDismiss: () -> Unit,
    viewModel: BrowserViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var metadata by remember { mutableStateOf<YouTubeMediaMetadata?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(true) }

    LaunchedEffect(videoInfo.currentUrl) {
        isLoadingMetadata = true
        try {
            val extracted = YouTubeMediaExtractor.extractMetadata(videoInfo.currentUrl)
            metadata = extracted.copy(
                title = if (videoInfo.title.isNotEmpty()) videoInfo.title else extracted.title,
                thumbnail = if (videoInfo.thumbnail.isNotEmpty()) videoInfo.thumbnail else extracted.thumbnail
            )
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingMetadata = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE50914).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFFE50914)
                            )
                        }
                        Column {
                            Text(
                                text = "YouTube Downloads",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Extract video, audio, thumbnails or subtitles",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (isLoadingMetadata) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE50914))
                            Text("Identifying active video streams...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    metadata?.let { meta ->
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Active Video Summary Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Image(
                                            painter = rememberAsyncImagePainter(meta.thumbnail),
                                            contentDescription = "Thumbnail",
                                            modifier = Modifier.size(90.dp, 60.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = meta.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Video ID: ${meta.videoId}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE50914).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "PRO EXTRACTOR",
                                                    color = Color(0xFFE50914),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 1. Video Formats
                            item {
                                Text("Video Downloads", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            items(meta.videoFormats.filter { it.quality in listOf("2160p", "1440p", "1080p", "720p", "480p", "360p") }) { format ->
                                FormatDownloadRow(
                                    label = "${format.quality} MP4",
                                    detail = "Muxed HD Video & Audio • ${(format.estimatedSize / (1024 * 1024))} MB",
                                    icon = Icons.Default.Movie,
                                    onClick = {
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Muxing streams for ${format.quality}... Please check background notification.", Toast.LENGTH_LONG).show()
                                            
                                            // Extract best audio format
                                            val bestAudio = meta.audioFormats.firstOrNull { it.quality.contains("AAC") } ?: meta.audioFormats.first()
                                            
                                            val cleanedTitle = meta.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                                            val outName = "${cleanedTitle}_${format.quality}.mp4"

                                            MediaMuxerEngine.downloadAndMux(
                                                context = context,
                                                videoUrl = format.url,
                                                audioUrl = bestAudio.url,
                                                outputFileName = outName,
                                                callback = object : MediaMuxerEngine.MuxingCallback {
                                                    override fun onProgress(percentage: Int) {
                                                        // Notify in background or console
                                                    }
                                                    override fun onSuccess(outputFile: File) {
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            Toast.makeText(context, "Muxing Finished! Saved as ${outputFile.name}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                    override fun onError(error: String) {
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            Toast.makeText(context, "Muxing Failed: $error", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        onDismiss()
                                    }
                                )
                            }

                            // 2. Audio Formats
                            item {
                                Text("Audio Downloads", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            items(meta.audioFormats) { format ->
                                val cleanContainer = format.container.uppercase()
                                FormatDownloadRow(
                                    label = "${format.quality} $cleanContainer",
                                    detail = "Stereo Track • ${(format.estimatedSize / (1024 * 1024))} MB",
                                    icon = Icons.Default.MusicNote,
                                    onClick = {
                                        coroutineScope.launch {
                                            val cleanedTitle = meta.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                                            
                                            if (format.quality.contains("MP3")) {
                                                Toast.makeText(context, "Converting download stream to MP3 with Coverart...", Toast.LENGTH_LONG).show()
                                                val outName = "${cleanedTitle}_Audio.mp3"
                                                AudioConversionEngine.downloadAndConvertToMp3(
                                                    context = context,
                                                    audioUrl = format.url,
                                                    title = meta.title,
                                                    artist = "YouTube",
                                                    thumbnailUrl = meta.thumbnail,
                                                    outputFileName = outName,
                                                    callback = object : AudioConversionEngine.ConversionCallback {
                                                        override fun onProgress(percentage: Int) {}
                                                        override fun onSuccess(outputFile: File) {
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                Toast.makeText(context, "MP3 Download Complete! Saved as ${outputFile.name}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        override fun onError(error: String) {
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                Toast.makeText(context, "Conversion Failed: $error", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                )
                                            } else {
                                                Toast.makeText(context, "Downloading audio track...", Toast.LENGTH_LONG).show()
                                                // Call fast standard download manager for AAC or M4A
                                                val outName = "${cleanedTitle}_Audio.${format.container}"
                                                viewModel.customDownloadEngine.startDownload(
                                                    url = format.url,
                                                    fileName = outName,
                                                    mimeType = format.mimeType
                                                )
                                            }
                                        }
                                        onDismiss()
                                    }
                                )
                            }

                            // 3. Other Downloads
                            item {
                                Text("Other Downloads", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            item {
                                FormatDownloadRow(
                                    label = "Thumbnail (HQ JPEG)",
                                    detail = "Active coverart preview image",
                                    icon = Icons.Default.Image,
                                    onClick = {
                                        coroutineScope.launch {
                                            val cleanedTitle = meta.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                                            val outName = "${cleanedTitle}_Thumbnail.jpg"
                                            viewModel.customDownloadEngine.startDownload(
                                                url = meta.thumbnail,
                                                fileName = outName,
                                                mimeType = "image/jpeg"
                                            )
                                            Toast.makeText(context, "Downloading Video Thumbnail preview...", Toast.LENGTH_SHORT).show()
                                        }
                                        onDismiss()
                                    }
                                )
                            }

                            item {
                                FormatDownloadRow(
                                    label = "Captions (Captions / English Subtitles)",
                                    detail = "Standard WebVTT Subtitle tract",
                                    icon = Icons.Default.Subtitles,
                                    onClick = {
                                        coroutineScope.launch {
                                            val englishSub = meta.subtitles.find { it.language == "en" } ?: meta.subtitles.first()
                                            val cleanedTitle = meta.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                                            val outName = "${cleanedTitle}_Subtitles_EN.vtt"
                                            viewModel.customDownloadEngine.startDownload(
                                                url = englishSub.url,
                                                fileName = outName,
                                                mimeType = "text/vtt"
                                            )
                                            Toast.makeText(context, "Downloading English video captions...", Toast.LENGTH_SHORT).show()
                                        }
                                        onDismiss()
                                    }
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
fun FormatDownloadRow(
    label: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Default.DownloadForOffline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
