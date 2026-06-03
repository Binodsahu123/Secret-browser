package com.example.browser

import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
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
    onCategorySelected: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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

    // Interactive dialogs
    var showAddDialog by remember { mutableStateOf(false) }
    var siteToRemove by remember { mutableStateOf<TopSite?>(null) }
    var searchInput by remember { mutableStateOf("") }
    
    val activeTab = state.tabs.find { it.id == state.activeTabId }
    val isIncognito = activeTab?.isIncognito == true

    if (isIncognito) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF202124))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                
                // Beautiful Custom Hand-drawn Incognito Specatles & Hat Canvas
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        // Hat crown dome
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.32f, h * 0.36f)
                            quadraticTo(w * 0.32f, h * 0.12f, w * 0.5f, h * 0.12f)
                            quadraticTo(w * 0.68f, h * 0.12f, w * 0.68f, h * 0.36f)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFFC4C7C5))

                        // Hat brim oval
                        drawOval(
                            color = Color(0xFFC4C7C5),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.34f),
                            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.08f)
                        )

                        // Left Eye Circle
                        val rimRad = w * 0.13f
                        val centerY = h * 0.64f
                        drawCircle(
                            color = Color(0xFFC4C7C5),
                            radius = rimRad,
                            center = androidx.compose.ui.geometry.Offset(w * 0.34f, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )

                        // Right Eye Circle
                        drawCircle(
                            color = Color(0xFFC4C7C5),
                            radius = rimRad,
                            center = androidx.compose.ui.geometry.Offset(w * 0.66f, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )

                        // Bridge connector arc
                        drawArc(
                            color = Color(0xFFC4C7C5),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.44f, centerY - rimRad * 0.4f),
                            size = androidx.compose.ui.geometry.Size(w * 0.12f, rimRad * 0.3f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "You've gone Incognito",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Now you can browse privately, and other people who use this device won't see your activity. However, downloads, bookmarks and reading list items will still be saved.",
                    color = Color(0xFFC4C7C5),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Surface(
                    color = Color(0xFF2D2F31),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "What SwiftBrowser won't save:",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "Your browsing history",
                            "Cookies and site data",
                            "Information entered in forms"
                        ).forEach { item ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("•  ", color = Color(0xFFA8B2C1), fontSize = 14.sp)
                                Text(item, color = Color(0xFFC4C7C5), fontSize = 13.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Your activity might still be visible to:",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "Websites that you visit",
                            "Your employer or school",
                            "Your internet service provider"
                        ).forEach { item ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("•  ", color = Color(0xFFA8B2C1), fontSize = 14.sp)
                                Text(item, color = Color(0xFFC4C7C5), fontSize = 13.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF35363A),
                    border = BorderStroke(1.dp, Color(0xFF424346))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search private",
                            tint = Color(0xFFC4C7C5),
                            modifier = Modifier.size(20.dp)
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
                            placeholder = "Search privately",
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .frostedGlassBackground(state.newTabWallpaper)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

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
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = (-1).sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            // Date
            Text(
                text = currentDate,
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

            // Top Sites Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Sites",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = "Quick shortcuts",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x4 Grid Layout for Top Sites
            TopSitesGrid(
                topSites = topSites,
                onSiteClick = { onSearch(it.url) },
                onSiteLongClick = { siteToRemove = it },
                onAddClick = { showAddDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recently Visited",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentHistory.take(5)) { item ->
                        RecentHistoryCard(
                            item = item,
                            onClick = { onSearch(item.url) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ==========================================
            // DISCOVER NEWS RSS FEED (Chrome Identical)
            // ==========================================
            
            Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discover Feed",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                TextButton(onClick = onRefresh) {
                    Text("Refresh", color = Color(0xFFA5B4FC), fontSize = 12.sp)
                }
            }

            // Category scrolling capsule chips
            val categories = listOf("For You", "Top Stories", "India", "Technology", "Sports", "Entertainment", "Business", "Science")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                items(categories) { category ->
                    val isSelected = state.feedCategory == category
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.clickable { onCategorySelected(category) }
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (state.isFeedLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF818CF8))
                }
            } else if (state.articles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No feed stories cached. Try refreshing.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Refresh Feed")
                    }
                }
            } else {
                state.articles.forEachIndexed { index, article ->
                    if (index == 0) {
                        // First item: Large elegant card layout
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clickable { onSearch(article.sourceUrl) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                ) {
                                    AsyncImageOrPlaceholder(
                                        imageUrl = article.imageUrl,
                                        category = article.category
                                    )
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = article.sourceName.uppercase(Locale.ROOT),
                                            color = Color(0xFFA5B4FC),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )

                                        Surface(
                                            color = Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "OFFLINE READABLE",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = article.title,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = article.description,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = article.publishedAt,
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 12.sp
                                        )
                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nRead more at: ${article.sourceUrl}")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share News"))
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard row layouts
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { onSearch(article.sourceUrl) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = article.sourceName,
                                        color = Color(0xFFA5B4FC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = article.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = article.publishedAt,
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        )
                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nRead more at: ${article.sourceUrl}")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share News"))
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    AsyncImageOrPlaceholder(
                                        imageUrl = article.imageUrl,
                                        category = article.category,
                                        isThumbnail = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
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
fun AsyncImageOrPlaceholder(
    imageUrl: String,
    category: String,
    isThumbnail: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isError by remember { mutableStateOf(false) }

    if (imageUrl.isNotBlank() && !isError) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
            onError = {
                isError = true
            }
        )
    } else {
        PlaceholderBox(category, isThumbnail)
    }
}

@Composable
fun PlaceholderBox(category: String, isThumbnail: Boolean) {
    val colors = listOf(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFF064E3B), Color(0xFF581C87), Color(0xFF881337))
    val index = Math.abs(category.hashCode()) % colors.size
    val bg = Brush.linearGradient(listOf(colors[index], colors[(index + 1) % colors.size]))
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = category.take(1).uppercase(),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = if (isThumbnail) 18.sp else 36.sp,
            fontWeight = FontWeight.Bold
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
        val rows = (topSites.size + 1 + 3) / 4
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
            .width(140.dp)
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
        else -> Brush.linearGradient(colors = listOf(Color(0xFF15151A), Color(0xFF20202F), Color(0xFF111115)))
    }
}
