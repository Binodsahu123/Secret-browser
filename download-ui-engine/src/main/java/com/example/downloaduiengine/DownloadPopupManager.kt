package com.example.downloaduiengine

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediadetectorengine.DetectedMedia
import com.example.mediadetectorengine.MediaDetector

@Composable
fun FloatingDownloadButton(
    detectedMedia: DetectedMedia?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = detectedMedia != null,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() + scaleIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size((56f * scale).dp)
                .shadow(8.dp, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Media Detected! Download",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadDialog(
    show: Boolean,
    detectedMedia: DetectedMedia,
    onDismiss: () -> Unit,
    onConfirmDownload: (fileName: String, threadsCount: Int) -> Unit
) {
    if (!show) return

    var inputFileName by remember(detectedMedia.fileName) { mutableStateOf(detectedMedia.fileName) }
    var selectedThreads by remember { mutableIntStateOf(4) }
    val categoriesText = remember(detectedMedia.mimeType) {
        val cat = when {
            detectedMedia.mimeType.startsWith("video/") -> "Videos"
            detectedMedia.mimeType.startsWith("audio/") -> "Audio"
            detectedMedia.mimeType.startsWith("image/") -> "Images"
            detectedMedia.mimeType.contains("pdf") || detectedMedia.mimeType.contains("document") -> "Documents"
            detectedMedia.mimeType.contains("zip") || detectedMedia.mimeType.contains("rar") -> "Archives"
            else -> "Documents"
        }
        "SwiftBrowserDownload/$cat"
    }

    val icon = when {
        detectedMedia.mimeType.startsWith("video/") -> Icons.Default.Movie
        detectedMedia.mimeType.startsWith("audio/") -> Icons.Default.MusicNote
        detectedMedia.mimeType.startsWith("image/") -> Icons.Default.Image
        detectedMedia.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
        else -> Icons.Default.InsertDriveFile
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Media Resource Detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = detectedMedia.quality,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Metadata details
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Format / Type:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(detectedMedia.mimeType.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estimated Size:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val sizeText = if (detectedMedia.fileSize > 0) formatBytes(detectedMedia.fileSize) else "Analyzing..."
                                Text(sizeText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Store Path:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(categoriesText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // Input title field
                    OutlinedTextField(
                        value = inputFileName,
                        onValueChange = { inputFileName = it },
                        label = { Text("Save File Name") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Multi-thread Selection Slider/Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Download Engine Speed-up Threads: $selectedThreads",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 4, 8).forEach { t ->
                                FilterChip(
                                    selected = selectedThreads == t,
                                    onClick = { selectedThreads = t },
                                    label = { Text("$t Thread${if (t > 1) "s" else ""}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Download and Cancel action rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Discard")
                        }
                        
                        Button(
                            onClick = {
                                onConfirmDownload(inputFileName, selectedThreads)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fast Download")
                        }
                    }
                }
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.2f %s", value, units[unitIndex])
}
