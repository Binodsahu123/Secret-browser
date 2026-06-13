package com.example.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WebContextMenuBottomSheet(
    state: ContextMenuState,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInNewTabGroup: (String) -> Unit,
    onOpenInIncognito: (String) -> Unit,
    onDownloadLink: (String) -> Unit,
    onAddToReadingList: (String, String) -> Unit
) {
    if (!state.show) return

    val context = LocalContext.current
    var isPreviewActive by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { /* Prevent click propagating to box background */ }
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                if (isPreviewActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                            .padding(16.dp)
                    ) {
                        // Title Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Preview Page",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        onOpenInNewTab(state.url)
                                        onDismiss()
                                    }
                                ) {
                                    Text("Open fully")
                                }
                                IconButton(onClick = { isPreviewActive = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Preview")
                                }
                            }
                        }

                        // WebView Preview Container
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewClient = WebViewClient()
                                        settings.javaScriptEnabled = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        loadUrl(state.url)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header / Info metadata
                        Text(
                            text = if (state.isImage) "Image Options" else "Link Options",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = state.url,
                            maxLines = 2,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Divider
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        if (!state.isImage) {
                            // Link Actions
                            ContextMenuItem(
                                icon = Icons.Default.OpenInNew,
                                text = "Open in new tab",
                                onClick = {
                                    onOpenInNewTab(state.url)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.GroupWork,
                                text = "Open in new tab in group",
                                onClick = {
                                    onOpenInNewTabGroup(state.url)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.VisibilityOff,
                                text = "Open in Incognito tab",
                                onClick = {
                                    onOpenInIncognito(state.url)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Launch,
                                text = "Open in new window",
                                onClick = {
                                    onOpenInNewTab(state.url)
                                    Toast.makeText(context, "Opened in new window/tab", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Pageview,
                                text = "Preview page",
                                onClick = {
                                    isPreviewActive = true
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.ContentCopy,
                                text = "Copy link address",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Copied Link", state.url))
                                    Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Download,
                                text = "Download link",
                                onClick = {
                                    onDownloadLink(state.url)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.BookmarkBorder,
                                text = "Add to reading list",
                                onClick = {
                                    onAddToReadingList(state.url, "Linked Page")
                                    Toast.makeText(context, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Share,
                                text = "Share link",
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, state.url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                                    onDismiss()
                                }
                            )
                        } else {
                            // Unified Contextual Image Actions
                            
                            // 1. If image is nested inside an anchor link, display its linked options
                            if (state.isImageLink) {
                                Text(
                                    text = "Link Actions (Anchor)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                ContextMenuItem(
                                    icon = Icons.Default.OpenInNew,
                                    text = "Open Link",
                                    onClick = {
                                        onOpenInNewTab(state.url)
                                        onDismiss()
                                    }
                                )
                                ContextMenuItem(
                                    icon = Icons.Default.GroupWork,
                                    text = "Open in new tab group",
                                    onClick = {
                                        onOpenInNewTabGroup(state.url)
                                        onDismiss()
                                    }
                                )
                                ContextMenuItem(
                                    icon = Icons.Default.ContentCopy,
                                    text = "Copy Link",
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Copied Link", state.url))
                                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                )
                                ContextMenuItem(
                                    icon = Icons.Default.Download,
                                    text = "Download Link",
                                    onClick = {
                                        onDownloadLink(state.url)
                                        onDismiss()
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }

                            // 2. Continuous Image-Specific Operations
                            val targetImageUrl = state.imageUrl.ifBlank { state.url }
                            
                            Text(
                                text = "Image Actions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Image,
                                text = "Open Image",
                                onClick = {
                                    onOpenInNewTab(targetImageUrl)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.OpenInBrowser,
                                text = "Open image in new tab",
                                onClick = {
                                    onOpenInNewTab(targetImageUrl)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Download,
                                text = "Download image",
                                onClick = {
                                    onDownloadLink(targetImageUrl)
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Share,
                                text = "Share image link",
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, targetImageUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Image link"))
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.ContentCopy,
                                text = "Copy image URL",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Image URL", targetImageUrl))
                                    Toast.makeText(context, "Image URL copied!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                            ContextMenuItem(
                                icon = Icons.Default.Search,
                                text = "Search Google for image",
                                onClick = {
                                    val searchUrl = "https://images.google.com/searchbyimage?image_url=${android.net.Uri.encode(targetImageUrl)}"
                                    onOpenInNewTab(searchUrl)
                                    onDismiss()
                                }
                            )
                            
                            var showImageInfoDialog by remember { mutableStateOf(false) }
                            ContextMenuItem(
                                icon = Icons.Default.Info,
                                text = "Image Information",
                                onClick = {
                                    showImageInfoDialog = true
                                }
                            )

                            if (showImageInfoDialog) {
                                val fileName = targetImageUrl.substringAfterLast("/", "image.jpg").substringBefore("?")
                                val extType = fileName.substringAfterLast(".", "jpg").uppercase()
                                AlertDialog(
                                    onDismissRequest = { showImageInfoDialog = false },
                                    confirmButton = {
                                        TextButton(onClick = { showImageInfoDialog = false; onDismiss() }) {
                                            Text("Close")
                                        }
                                    },
                                    title = { Text("Image Information") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Name: $fileName", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Format Type: $extType", fontSize = 12.sp)
                                            Text("Resource Address: $targetImageUrl", fontSize = 11.sp, color = Color.Gray, maxLines = 6)
                                        }
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
private fun ContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}
