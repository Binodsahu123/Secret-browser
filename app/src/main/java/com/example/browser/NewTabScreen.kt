package com.example.browser

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.HistoryItem
import com.example.data.TopSite
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabScreen(
    state: BrowserUiState,
    topSites: List<TopSite>,
    recentHistory: List<HistoryItem>,
    onSearch: (String) -> Unit,
    onAddShortcut: (String, String) -> Unit,
    onRemoveTopSite: (TopSite) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current Time/Date Tick
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            greeting = when (hour) {
                in 0..11 -> "Good Morning"
                in 12..16 -> "Good Afternoon"
                else -> "Good Evening"
            }

            currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            currentDate = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(cal.time)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Wallpaper Gradient Brush based on Preferences
    val gradientBrush = remember(state.newTabWallpaper) {
        getBackgroundBrush(state.newTabWallpaper)
    }

    // Interactive dialogs
    var showAddDialog by remember { mutableStateOf(false) }
    var siteToRemove by remember { mutableStateOf<TopSite?>(null) }
    var searchInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .frostedGlassBackground(state.newTabWallpaper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Greeting Section
            Text(
                text = greeting.uppercase(Locale.getDefault()),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFA5B4FC),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Large Digital Clock
            Text(
                text = currentTime,
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = (-1).sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            // Date
            Text(
                text = currentDate,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Beautiful Homepage Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextFieldWithoutLabel(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        onDone = {
                            if (searchInput.isNotBlank()) {
                                onSearch(searchInput)
                            }
                        },
                        placeholder = "Search Google or type URL",
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Top Sites Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Sites",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = "2x4 Grid",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2x4 Grid Layout for Top Sites
            TopSitesGrid(
                topSites = topSites,
                onSiteClick = { onSearch(it.url) },
                onSiteLongClick = { siteToRemove = it },
                onAddClick = { showAddDialog = true }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Recent horizontal history list
            if (recentHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History icon",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recently Visited",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentHistory.take(5)) { item ->
                        RecentHistoryCard(
                            item = item,
                            onClick = { onSearch(item.url) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // Add Shortcut Dialog
    if (showAddDialog) {
        AddShortcutDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url ->
                onAddShortcut(url, title)
                showAddDialog = false
            }
        )
    }

    // Remove Confirmation Dialog
    if (siteToRemove != null) {
        AlertDialog(
            onDismissRequest = { siteToRemove = null },
            title = { Text("Remove Shortcut") },
            text = { Text("Are you sure you want to remove ${siteToRemove?.title} from your top sites?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        siteToRemove?.let { onRemoveTopSite(it) }
                        siteToRemove = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { siteToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TopSitesGrid(
    topSites: List<TopSite>,
    onSiteClick: (TopSite) -> Unit,
    onSiteLongClick: (TopSite) -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val rows = (topSites.size + 1 + 3) / 4 // Rows to fit up to 8 items + a "+" button
        val items = topSites.take(8)

        for (r in 0 until 2.coerceAtMost(rows)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (c in 0 until 4) {
                    val index = r * 4 + c
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        if (index < items.size) {
                            val site = items[index]
                            TopSiteItem(
                                site = site,
                                onClick = { onSiteClick(site) },
                                onLongClick = { onSiteLongClick(site) }
                            )
                        } else if (index == items.size && items.size < 8) {
                            AddShortcutItem(onClick = onAddClick)
                        } else {
                            // Empty filler space to preserve symmetry
                            Spacer(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopSiteItem(
    site: TopSite,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initial = site.title.trim().take(1).uppercase(Locale.getDefault())
                val colors = listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800))
                val colorIndex = Math.abs(site.url.hashCode()) % colors.size
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors[colorIndex]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial.ifBlank { "?" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = site.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AddShortcutItem(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add custom shortcut",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Add",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RecentHistoryCard(
    item: HistoryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            val domain = remember(item.url) {
                try {
                    val host = URI(item.url).host ?: ""
                    if (host.startsWith("www.")) host.substring(4) else host
                } catch (e: Exception) {
                    item.url
                }
            }

            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = domain,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Add Shortcut",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (e.g. google.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                onConfirm(title.ifBlank { url }, url)
                            }
                        },
                        enabled = url.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

// Custom Basic Text Field to avoid importing oversized material fields
@Composable
fun BasicTextFieldWithoutLabel(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onDone() }
            ),
            cursorBrush = Brush.linearGradient(listOf(Color.White, Color.White)),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun Modifier.frostedGlassBackground(styleName: String): Modifier {
    return if (styleName == "Frosted Glass") {
        this.drawBehind {
            // Draw slate-950 base background Slate-950 Color(0xFF020617)
            drawRect(color = Color(0xFF020617))

            // Top-right Indigo spotlight (indigo-500/30) with radial blur effect
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x4D6366F1), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.05f),
                    radius = size.width * 1.0f
                )
            )
            // Bottom-left Rose spotlight (rose-500/20) with radial blur effect
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33F43F5E), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 0.95f),
                    radius = size.width * 1.0f
                )
            )
        }
    } else {
        this.background(getBackgroundBrush(styleName))
    }
}

fun getBackgroundBrush(styleName: String): Brush {
    return when (styleName) {
        "Cosmic Twilight" -> Brush.linearGradient(colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
        "Purple Dusk" -> Brush.linearGradient(colors = listOf(Color(0xFF2C1A4D), Color(0xFF1F1135), Color(0xFF0C0615)))
        "Deep Ocean" -> Brush.linearGradient(colors = listOf(Color(0xFF000428), Color(0xFF004E92)))
        "Warm Sunrise" -> Brush.linearGradient(colors = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)))
        "Frosted Glass" -> Brush.linearGradient(colors = listOf(Color(0xFF020617), Color(0xFF0B1220)))
        else -> Brush.linearGradient(colors = listOf(Color(0xFF15151A), Color(0xFF20202F), Color(0xFF111115))) // Minimalist Slate
    }
}
