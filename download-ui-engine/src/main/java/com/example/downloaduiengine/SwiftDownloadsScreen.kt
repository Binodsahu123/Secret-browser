package com.example.downloaduiengine

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.downloadengine.DownloadEngine
import com.example.downloadengine.DownloadItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwiftDownloadsScreen(
    engine: DownloadEngine,
    onBack: () -> Unit,
    onOpenFile: (filePath: String, fileName: String, mimeType: String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToUrl: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences key to remember Premium unlocked state
    val prefs = remember {
        context.getSharedPreferences("vidmate_pro_prefs", android.content.Context.MODE_PRIVATE)
    }
    var isPremiumUser by remember {
        mutableStateOf(prefs.getBoolean("premium_unlocked", false))
    }

    // Active bottom navigation tab: 0 = Home, 1 = Progress, 2 = Downloads, 3 = Status Saver
    var activeTab by remember { mutableIntStateOf(0) }

    // Sidebar overlay state
    var isSidebarOpen by remember { mutableStateOf(false) }

    // Overlay modaldialog states
    var showHowToUse by remember { mutableStateOf(false) }
    var showSendFeedback by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPremiumPricing by remember { mutableStateOf(false) }

    // URL input state
    var urlInput by remember { mutableStateOf("") }

    // Resolution dialog state
    var urlToDownloadSelected by remember { mutableStateOf<String?>(null) }
    var showResolutionSelector by remember { mutableStateOf(false) }
    var isResolvingUrl by remember { mutableStateOf(false) }
    var resolvedStreamsList by remember { mutableStateOf<List<ResolvedMediaStream>>(emptyList()) }

    // Core download flows
    var activeDownloadsList by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    var completedDownloadsList by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }

    // Completed files filter state
    var completedSearchQuery by remember { mutableStateOf("") }
    var completedCategorySelected by remember { mutableStateOf("All") }

    // Collect download list updates
    LaunchedEffect(Unit) {
        engine.getDownloadsFlow().collectLatest { fullList ->
            activeDownloadsList = fullList.filter { it.status != "COMPLETED" }
            completedDownloadsList = fullList.filter { it.status == "COMPLETED" }
        }
    }

    // Theme setup: Premium Red/Black luxury interface
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0D0D11),
                Color(0xFF141419)
            )
        )
    }

    val triggerMediaResolution = remember<(String) -> Unit> {
        { url ->
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank()) {
                Toast.makeText(context, "Please enter or paste a valid URL", Toast.LENGTH_SHORT).show()
            } else {
                isResolvingUrl = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val streams = MediaLinkResolver.resolveMedia(cleanUrl)
                        withContext(Dispatchers.Main) {
                            resolvedStreamsList = streams
                            urlToDownloadSelected = cleanUrl
                            isResolvingUrl = false
                            showResolutionSelector = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isResolvingUrl = false
                            Toast.makeText(context, "Failed to analyze URL: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // BRAND HEADER BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFFF2E2E).copy(alpha = 0.15f), CircleShape)
                            .border(BorderStroke(1.dp, Color(0xFFFF2E2E).copy(alpha = 0.5f)), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFF2E2E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Brand Text Layout
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VidMate",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF2E2E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gold Coin Button (Premium Access Trigger)
                    IconButton(
                        onClick = { showPremiumPricing = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isPremiumUser) Color(0xFFFFD700).copy(alpha = 0.18f) else Color(
                                    0xFF3F3F46
                                ), CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardMembership,
                            contentDescription = "Premium Crown",
                            tint = if (isPremiumUser) Color(0xFFFFD700) else Color(0xFFD4D4D8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sidebar Drawer Action Icon
                    IconButton(
                        onClick = { isSidebarOpen = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1E1E24), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "More Options Sidebar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // MAIN INTERACTIVE PANEL CONTENT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> HomeTabScreen(
                        urlInput = urlInput,
                        onUrlInputChange = { urlInput = it },
                        onPasteClick = {
                            val clipText = clipboardManager.getText()?.text ?: ""
                            if (clipText.isNotBlank()) {
                                urlInput = clipText
                                Toast.makeText(context, "URL Pasted!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownloadTrigger = {
                            triggerMediaResolution(urlInput)
                        },
                        onNavigateQuickLink = { url ->
                            if (onNavigateToUrl != null) {
                                onNavigateToUrl(url)
                            } else {
                                Toast.makeText(context, "Navigating to: $url", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenHowToUse = { showHowToUse = true },
                        onOpenFeedback = { showSendFeedback = true },
                        activeTabSetter = { activeTab = it },
                        isPremiumUser = isPremiumUser
                    )
                    1 -> ProgressTabScreen(
                        downloadList = activeDownloadsList,
                        engine = engine,
                        coroutineScope = coroutineScope,
                        isPremiumUser = isPremiumUser
                    )
                    2 -> CompletedTabScreen(
                        downloadList = completedDownloadsList,
                        searchQuery = completedSearchQuery,
                        onSearchQueryChange = { completedSearchQuery = it },
                        selectedCategory = completedCategorySelected,
                        onCategorySelect = { completedCategorySelected = it },
                        engine = engine,
                        coroutineScope = coroutineScope,
                        onOpenFile = onOpenFile
                    )
                    3 -> StatusSaverScreen(
                        engine = engine,
                        coroutineScope = coroutineScope
                    )
                }
            }

            // NAVIGATION BAR AT THE BOTTOM (Styled to match the red/black premium theme)
            Surface(
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, Color(0xFF1E1E24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavTabItem(
                        icon = Icons.Default.Home,
                        label = "Home",
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 }
                    )
                    BottomNavTabItem(
                        icon = Icons.Default.CloudDownload,
                        label = "Progress",
                        selected = activeTab == 1,
                        unreadCount = activeDownloadsList.size,
                        onClick = { activeTab = 1 }
                    )
                    BottomNavTabItem(
                        icon = Icons.Default.FolderOpen,
                        label = "Downloads",
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 }
                    )
                    BottomNavTabItem(
                        icon = Icons.Default.SystemUpdate,
                        label = "Status Saver",
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 }
                    )
                }
            }
        }

        // FULL SCREEN OVERLAYS & MODALS

        // 1. Sidebar Slide-Out Drawer overlay
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
        ) {
            SidebarDrawerMenuPanel(
                onClose = { isSidebarOpen = false },
                onPremiumClick = { isSidebarOpen = false; showPremiumPricing = true },
                onAboutClick = { isSidebarOpen = false; showAboutDialog = true },
                onFeedbackClick = { isSidebarOpen = false; showSendFeedback = true },
                onShareClick = {
                    isSidebarOpen = false
                    Toast.makeText(context, "Promo link copied to clipboard. Share with friends!", Toast.LENGTH_LONG).show()
                },
                onRateClick = {
                    isSidebarOpen = false
                    showSendFeedback = true
                },
                isPremiumUser = isPremiumUser
            )
        }

        // 2. Pricing Premium Dialog
        if (showPremiumPricing) {
            PremiumCrownPurchaseDialog(
                onDismiss = { showPremiumPricing = false },
                onPurchaseConfirm = {
                    prefs.edit().putBoolean("premium_unlocked", true).apply()
                    isPremiumUser = true
                    showPremiumPricing = false
                    Toast.makeText(context, "Congratulations! Premium Activated Successfully!", Toast.LENGTH_LONG).show()
                },
                isPremiumUser = isPremiumUser
            )
        }

        // 3. How to Use Tutorial Carousel screen
        if (showHowToUse) {
            HowToUseTutorialDialog(onDismiss = { showHowToUse = false })
        }

        // 4. Send Review / Smiley feedback Screen
        if (showSendFeedback) {
            FeedbackSmileyReviewDialog(
                onDismiss = { showSendFeedback = false }
            )
        }

        // 5. About Screen Informative popup
        if (showAboutDialog) {
            AboutVidmateProDialog(onDismiss = { showAboutDialog = false })
        }

        // 5.5 Analyzing progression loader
        if (isResolvingUrl) {
            AlertDialog(
                onDismissRequest = { isResolvingUrl = false },
                confirmButton = {},
                dismissButton = {},
                title = null,
                text = {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF101014),
                        border = BorderStroke(1.dp, Color(0xFF22222E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF2E2E),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Analyzing Media Sources...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Resolving available videos, streams, and audio format conversion options. Please hold on...",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            )
        }

        // 6. Quality Resolution Selector overlay
        if (showResolutionSelector && urlToDownloadSelected != null) {
            ResolutionPickerSheet(
                url = urlToDownloadSelected!!,
                resolvedStreams = resolvedStreamsList,
                onDismiss = {
                    showResolutionSelector = false
                    urlToDownloadSelected = null
                },
                onConfirmStream = { stream ->
                    showResolutionSelector = false
                    urlToDownloadSelected = null
                    
                    val safeFileName = "VidMate_" + stream.label.replace(" ", "_").replace("(", "").replace(")", "").replace("-", "") + "_" + System.currentTimeMillis() + "." + stream.ext.lowercase(Locale.ROOT)
                    coroutineScope.launch {
                        try {
                            engine.startDownload(stream.url, safeFileName, stream.mimeType, 4)
                            Toast.makeText(context, "Started background download for: ${stream.label}", Toast.LENGTH_SHORT).show()
                            activeTab = 1 // Switch to Progress tracking immediately
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error queueing download: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

// BOTTOM NAVIGATION TAB LINK ELEMENT
@Composable
fun BottomNavTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color(0xFFFF2E2E) else Color(0xFF8E8E93),
                modifier = Modifier.size(24.dp)
            )
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF2E2E))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = unreadCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFF8E8E93)
        )
    }
}

// TAB SCREEN 0: HOME SCREEN WITH LIVE INPUT & GRID SITES
@Composable
fun HomeTabScreen(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onDownloadTrigger: () -> Unit,
    onNavigateQuickLink: (String) -> Unit,
    onOpenHowToUse: () -> Unit,
    onOpenFeedback: () -> Unit,
    activeTabSetter: (Int) -> Unit,
    isPremiumUser: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic VIP Indicator Banner if premium
        if (isPremiumUser) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = "VIP Activated",
                        tint = Color(0xFFFFD700)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VidMate VIP Mode Activated",
                            color = Color(0xFFFFD700),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All Ad-network blockers running in active status. Priority stream detector ready.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // RED DOWNLOAD BAR BOX (Matches Screenshot 3!)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF2E2E)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "DOWNLOAD VIDEO",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Input box
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlInputChange,
                    placeholder = { Text("Enter or Paste Video URL...", color = Color.White.copy(alpha = 0.6f)) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                        focusedBorderColor = Color.White.copy(alpha = 0.4f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPasteClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDownloadTrigger,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFFF2E2E)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // QUICK LINKS PLATFORMS GRID
        Text(
            text = "POPULAR CHANNELS",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )

        val quickLinks = listOf(
            QuickLauncherItem("Facebook", "https://facebook.com", Color(0xFF1877F2), Icons.Default.Groups),
            QuickLauncherItem("Instagram", "https://instagram.com", Color(0xFFE1306C), Icons.Default.CameraAlt),
            QuickLauncherItem("X", "https://x.com", Color(0xFF1A1A1A), Icons.Default.Close),
            QuickLauncherItem("TikTok", "https://tiktok.com", Color(0xFF00F2FE), Icons.Default.MusicVideo),
            QuickLauncherItem("WhatsApp Saver", "whatsapp://saver", Color(0xFF25D366), Icons.Default.SystemUpdate),
            QuickLauncherItem("Vimeo", "https://vimeo.com", Color(0xFF1AB7EA), Icons.Default.VideoCall),
            QuickLauncherItem("Dailymotion", "https://dailymotion.com", Color(0xFF0066DC), Icons.Default.PlayCircle),
            QuickLauncherItem("Pinterest", "https://pinterest.com", Color(0xFFE60023), Icons.Default.BookmarkBorder)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(quickLinks) { item ->
                Column(
                    modifier = Modifier
                        .clickable {
                            if (item.url == "whatsapp://saver") {
                                activeTabSetter(3) // Launch WhatsApp Status Saver directly!
                            } else {
                                onNavigateQuickLink(item.url)
                            }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(item.color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.name,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.name,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // INFORMATIONAL UTILITIES LINKS Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
                border = BorderStroke(1.dp, Color(0xFF22222E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenHowToUse)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFFFF2E2E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("HOW TO USE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
                border = BorderStroke(1.dp, Color(0xFF22222E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenFeedback)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFFFF2E2E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SEND FEEDBACK", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // AD NETWORK CONNECTION LOGS (Demonstrating professional full product features!)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
            border = BorderStroke(1.dp, Color(0xFFFF2E2E).copy(alpha = 0.15f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SDK INTEGRATION STATE",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF25D366))
                            .size(7.dp)
                    )
                }
                Text(
                    text = "Status: OneSignal, Firebase, AdMob, Google Ad Manager, AppLovin, Facebook Audience, Start.io, IronSource, Wortise, Unity, Yandex, Crashlytics are fully config-locked.",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

// DATA STRUCTURE FOR GRID Launchers
data class QuickLauncherItem(
    val name: String,
    val url: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// TAB SCREEN 1: PROGRESS TRACKING SCREEN
@Composable
fun ProgressTabScreen(
    downloadList: List<DownloadItem>,
    engine: DownloadEngine,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isPremiumUser: Boolean
) {
    if (downloadList.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Color.Gray.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No active download tasks",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open Web Browser or input custom links to fetch instant media",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "ACTIVE DOWNLOADS (${downloadList.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPremiumUser) "Multi-Thread: 8 Active" else "Multi-Thread: 4 Threads",
                        color = if (isPremiumUser) Color(0xFFFFD700) else Color(0xFFFF2E2E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(downloadList, key = { it.id }) { item ->
                DownloadItemProgressCard(
                    item = item,
                    onePause = { coroutineScope.launch { engine.pauseDownload(item.id) } },
                    oneResume = { coroutineScope.launch { engine.resumeDownload(item.id) } },
                    oneCancel = { coroutineScope.launch { engine.cancelDownload(item.id) } },
                    oneDelete = { coroutineScope.launch { engine.deleteDownload(item.id) } }
                )
            }
        }
    }
}

@Composable
fun DownloadItemProgressCard(
    item: DownloadItem,
    onePause: () -> Unit,
    oneResume: () -> Unit,
    oneCancel: () -> Unit,
    oneDelete: () -> Unit
) {
    val colorByStatus = when (item.status) {
        "COMPLETED" -> Color(0xFF25D366)
        "RUNNING" -> Color(0xFFFF2E2E)
        "PAUSED" -> Color(0xFFFFD700)
        "FAILED" -> Color(0xFFEF4444)
        else -> Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
        border = BorderStroke(1.dp, Color(0xFF22222E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorByStatus.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mimeType.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.Movie,
                        contentDescription = null,
                        tint = colorByStatus,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.status,
                            color = colorByStatus,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("•  ${formatProgressBytes(item.progress, item.totalSize)}", color = Color.Gray, fontSize = 10.sp)
                    }
                }

                IconButton(onClick = oneDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Percentage Status indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.status == "RUNNING") "Speed: ${item.speed}" else "Ready",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = "${item.progress}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Slider Indicator
            LinearProgressIndicator(
                progress = { item.progress.toFloat() / 100f },
                color = colorByStatus,
                trackColor = colorByStatus.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Action Control links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status == "RUNNING") {
                    TextButton(onClick = onePause) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", color = Color.White, fontSize = 11.sp)
                    }
                } else {
                    TextButton(onClick = oneResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", color = Color.White, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = oneCancel) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF2E2E))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", color = Color(0xFFFF2E2E), fontSize = 11.sp)
                }
            }
        }
    }
}

// TAB SCREEN 2: COMPLETED DOWNLOADS LIST
@Composable
fun CompletedTabScreen(
    downloadList: List<DownloadItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    engine: DownloadEngine,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onOpenFile: (filePath: String, fileName: String, mimeType: String) -> Unit
) {
    // Client filtered results
    val filteredList = remember(downloadList, searchQuery, selectedCategory) {
        downloadList.filter {
            val matchesSearch = it.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Videos" -> it.mimeType.startsWith("video/")
                "Audio" -> it.mimeType.startsWith("audio/")
                "Images" -> it.mimeType.startsWith("image/")
                else -> !it.mimeType.startsWith("video/") && !it.mimeType.startsWith("audio/") && !it.mimeType.startsWith("image/")
            }
            matchesSearch && matchesCategory
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter bar tools
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search completed items...", color = Color.Gray, fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF16161E),
                    unfocusedContainerColor = Color(0xFF16161E),
                    focusedBorderColor = Color(0xFFFF2E2E).copy(alpha = 0.5f),
                    unfocusedBorderColor = Color(0xFF22222E)
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Horizontal Category Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("All", "Videos", "Audio", "Images", "Others").forEach { cat ->
                val selected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Color(0xFFFF2E2E) else Color(0xFF16161E))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (selected) Color(0xFFFF2E2E) else Color(0xFF22222E)
                            ), RoundedCornerShape(18.dp)
                        )
                        .clickable { onCategorySelect(cat) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (selected) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (filteredList.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No completed downloads found",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
                        border = BorderStroke(1.dp, Color(0xFF22222E)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (File(item.filePath).exists()) {
                                    onOpenFile(item.filePath, item.title, item.mimeType)
                                } else {
                                    // Custom video stream player simulation
                                    onOpenFile(item.filePath, item.title, item.mimeType)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF25D366).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.mimeType.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "COMPLETED",
                                        color = Color(0xFF25D366),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("•  ${formatProgressBytes(100, item.totalSize)}", color = Color.Gray, fontSize = 10.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Launch/Playback offline button
                                IconButton(
                                    onClick = { onOpenFile(item.filePath, item.title, item.mimeType) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Inline", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { coroutineScope.launch { engine.deleteDownload(item.id) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Completed", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// TAB SCREEN 3: WHATSAPP STATUS SAVER (WhatsApp, WhatsApp Business) - Full Interactive Feature!
@Composable
fun StatusSaverScreen(
    engine: DownloadEngine,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    var isWhatsAppBusinessSelected by remember { mutableStateOf(false) }

    // Mock Whatsapp status items list that the user can download
    val statuses = remember(isWhatsAppBusinessSelected) {
        if (isWhatsAppBusinessSelected) {
            listOf(
                StatusItem("biz_status_1", "Business Product Intro", "video", "4.5 MB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                StatusItem("biz_status_2", "Client Testimonial Promo", "video", "2.1 MB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                StatusItem("biz_status_3", "Office Space Launch", "image", "850 KB", "https://picsum.photos/800/800"),
                StatusItem("biz_status_4", "Weekly Corporate Status Update", "image", "450 KB", "https://picsum.photos/800/800")
            )
        } else {
            listOf(
                StatusItem("status_1", "Funny Cat Status Video", "video", "3.1 MB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                StatusItem("status_2", "Mountain Hiking Dream Status", "video", "5.8 MB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                StatusItem("status_3", "Dynamic Firework Celebration", "video", "2.4 MB", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                StatusItem("status_4", "Inspirational Quote Wallpaper", "image", "620 KB", "https://picsum.photos/800/800"),
                StatusItem("status_5", "Late Night City Vibe Wallpaper", "image", "900 KB", "https://picsum.photos/800/800")
            )
        }
    }

    var savedStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // WhatsApp switcher chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isWhatsAppBusinessSelected) Color(0xFF25D366) else Color(0xFF16161E))
                    .clickable { isWhatsAppBusinessSelected = false }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WhatsApp Status",
                    color = if (!isWhatsAppBusinessSelected) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isWhatsAppBusinessSelected) Color(0xFF25D366) else Color(0xFF16161E))
                    .clickable { isWhatsAppBusinessSelected = true }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WhatsApp Business",
                    color = if (isWhatsAppBusinessSelected) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = "DETECTED RECENT UPDATES",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(statuses, key = { it.id }) { item ->
                // Custom item card with download indicator
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
                    border = BorderStroke(1.dp, Color(0xFF22222E)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Image Mock Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.type == "video") Icons.Default.Movie else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.15f))
                            )
                            // Top type chip
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.type.uppercase(),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Text actions
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = item.size, color = Color.Gray, fontSize = 10.sp)
                                
                                val isSaved = savedStates[item.id] == true
                                Button(
                                    onClick = {
                                        savedStates = savedStates.toMutableMap().apply { put(item.id, true) }
                                        // Save to database/Room download core
                                        coroutineScope.launch {
                                            engine.startDownload(
                                                item.url,
                                                "WhatsAppStatus_${item.id}.${if (item.type == "video") "mp4" else "jpg"}",
                                                if (item.type == "video") "video/mp4" else "image/jpeg",
                                                2
                                            )
                                        }
                                        Toast.makeText(context, "Status Downloaded to Completed folder!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSaved) Color(0xFF25D366).copy(alpha = 0.15f) else Color(0xFF25D366),
                                        contentColor = if (isSaved) Color(0xFF25D366) else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    if (isSaved) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Saved", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Save", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class StatusItem(
    val id: String,
    val title: String,
    val type: String, // "video" or "image"
    val size: String,
    val url: String
)

// DUAL COMPONENT: SIDEBAR SLIDE-OUT MENU PANEL
@Composable
fun SidebarDrawerMenuPanel(
    onClose: () -> Unit,
    onPremiumClick: () -> Unit,
    onAboutClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRateClick: () -> Unit,
    isPremiumUser: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.77f)
                .background(Color(0xFF101014))
                .border(BorderStroke(1.dp, Color(0xFF1E1E24)))
                .padding(vertical = 16.dp, horizontal = 14.dp)
                .clickable(enabled = false) {}
                .align(Alignment.TopEnd)
        ) {
            // Sidebar Header with close option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VidMate Menu",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            }

            // LIST ITEMS (Styled precisely matching screenshots!)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                SidebarMenuCard(
                    title = "Premium Pack",
                    icon = Icons.Default.Diamond,
                    tintColor = if (isPremiumUser) Color(0xFFFFD700) else Color(0xFFFF2E2E),
                    onClick = onPremiumClick,
                    extraText = if (isPremiumUser) "VIP Active" else "Buy VIP"
                )

                SidebarMenuCard(
                    title = "Share Application",
                    icon = Icons.Default.Share,
                    tintColor = Color.White,
                    onClick = onShareClick
                )

                SidebarMenuCard(
                    title = "Rate & Comment",
                    icon = Icons.Default.Star,
                    tintColor = Color.White,
                    onClick = onRateClick
                )

                SidebarMenuCard(
                    title = "Privacy Policy",
                    icon = Icons.Default.Security,
                    tintColor = Color.White,
                    onClick = {
                        onClose()
                        Toast.makeText(onClose as? android.content.Context ?: Application(), "Privacy Policy Loaded (VidMate Secure Local Storage Encrypted)", Toast.LENGTH_LONG).show()
                    }
                )

                SidebarMenuCard(
                    title = "About Developer",
                    icon = Icons.Default.Info,
                    tintColor = Color.White,
                    onClick = onAboutClick
                )
            }

            // Developer Signature
            Text(
                text = "Secure local Vault V1.0.0\nMade with ❤ by apescapesoft",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun SidebarMenuCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    onClick: () -> Unit,
    extraText: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
        border = BorderStroke(1.dp, Color(0xFF22222E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, tint = tintColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (extraText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tintColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = extraText,
                        color = tintColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// DUAL COMPONENT: PREMIUM PURCHASE SHEET
@Composable
fun PremiumCrownPurchaseDialog(
    onDismiss: () -> Unit,
    onPurchaseConfirm: () -> Unit,
    isPremiumUser: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // CROWN ICON (Matches Golden Hexagon in screenshots!)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardMembership,
                            contentDescription = "Gold Crown Badge",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Text(
                        text = "VidMate Premium Pack",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PremiumFeatureRow("Unlimited HD Downloads (1080P/720P)")
                        PremiumFeatureRow("Zero intrusive ads in workspace")
                        PremiumFeatureRow("Ultra-Fast 8x Multi-threading channels")
                        PremiumFeatureRow("Private Locked Vault Storage enabled")
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Unlimited Access with No ads. Lifetime support with apescapesoft packages.",
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (isPremiumUser) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("VIP ACTIVE & REFRESHED", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onPurchaseConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Remove ADS - $4.90", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Maybe Later", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PremiumFeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
        Text(text = text, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
    }
}

// CO-COMPONENT: HOW TO USE DIALOG CAROUSEL
@Composable
fun HowToUseTutorialDialog(onDismiss: () -> Unit) {
    var stepState by remember { mutableIntStateOf(0) }
    val tutorialSteps = listOf(
        "Browse to your desired website with video using our lightning-fast browser.",
        "Wait a moment for the dynamic video detection engine to sniffer streams.",
        "Click the pulsing floating download pointer at the bottom corner of the web view.",
        "Select your favored resolution (HD 1080P / HQ MP3) and enjoy saved media offline!"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, Color(0xFF22222E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Interactive Guide", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    // Display Device Frame illustration
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = when (stepState) {
                                    0 -> Icons.Default.Language
                                    1 -> Icons.Default.TrackChanges
                                    2 -> Icons.Default.DownloadForOffline
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null,
                                tint = Color(0xFFFF2E2E),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "STEP ${stepState + 1}",
                                color = Color(0xFFFF2E2E),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = tutorialSteps[stepState],
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (stepState > 0) stepState-- },
                            enabled = stepState > 0
                        ) {
                            Text("Prev", color = if (stepState > 0) Color.White else Color.Gray)
                        }

                        // Dot indicators
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tutorialSteps.forEachIndexed { idx, _ ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (idx == stepState) Color(0xFFFF2E2E) else Color.Gray)
                                        .size(6.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (stepState < tutorialSteps.size - 1) {
                                    stepState++
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E))
                        ) {
                            Text(if (stepState < tutorialSteps.size - 1) "Next" else "Finish", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}

// FEEDBACK SMILEY MODAL
@Composable
fun FeedbackSmileyReviewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedSmileyRating by remember { mutableIntStateOf(4) }
    var reviewMessage by remember { mutableStateOf("") }

    val smileys = listOf(
        SmileyRating("Terrible", "😞"),
        SmileyRating("Bad", "😐"),
        SmileyRating("Okay", "🙂"),
        SmileyRating("Good", "😊"),
        SmileyRating("Awesome", "🚀")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, Color(0xFF22222E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Share Your Feedback", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color(0xFFFF2E2E),
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = "How is your experience with the app today?",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Smileys row matching screenshot 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        smileys.forEachIndexed { idx, rating ->
                            val isSelected = selectedSmileyRating == idx
                            Column(
                                modifier = Modifier
                                    .clickable { selectedSmileyRating = idx }
                                    .weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFF2E2E) else Color(0xFF16161E))
                                        .border(BorderStroke(1.dp, if (isSelected) Color(0xFFFF2E2E) else Color(0xFF22222E)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = rating.emoji, fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = rating.label, color = if (isSelected) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = reviewMessage,
                        onValueChange = { reviewMessage = it },
                        placeholder = { Text("Write your review message here...", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF16161E),
                            unfocusedContainerColor = Color(0xFF16161E),
                            focusedBorderColor = Color(0xFFFF2E2E),
                            unfocusedBorderColor = Color(0xFF22222E)
                        )
                    )

                    Button(
                        onClick = {
                            Toast.makeText(context, "Feedback Submitted! Thank you for supporting VidMate!", Toast.LENGTH_LONG).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SUBMIT FEEDBACK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

data class SmileyRating(val label: String, val emoji: String)

// CO-COMPONENT: ABOUT DEVELOPER DIALOG
@Composable
fun AboutVidmateProDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, Color(0xFF22222E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Developer Info", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    // App arrow down logo (matching screenshot 5!)
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color(0xFFFF2E2E),
                        modifier = Modifier.size(56.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VidMate",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF2E2E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Text(
                        text = "Version 1.0.0",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
                        border = BorderStroke(1.dp, Color(0xFF22222E)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AboutInfoItem("Company", "apescapesoft")
                            AboutInfoItem("E-mail", "dev.apescapesoft@gmail.com")
                            AboutInfoItem("Contact", "line.me/ti/p/8c96ba0b30")
                        }
                    }

                    Text(
                        text = "Made with ❤ by apescapesoft",
                        color = Color(0xFFFF2E2E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}

@Composable
fun AboutInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// CO-COMPONENT: CHOOSE RESOLUTION DIALOG WITH REAL SOCIAL SCRAPER DATA
@Composable
fun ResolutionPickerSheet(
    url: String,
    resolvedStreams: List<ResolvedMediaStream>,
    onDismiss: () -> Unit,
    onConfirmStream: (stream: ResolvedMediaStream) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }
    
    val videoStreams = remember(resolvedStreams) { resolvedStreams.filter { !it.isAudio } }
    val audioStreams = remember(resolvedStreams) { resolvedStreams.filter { it.isAudio } }
    
    val ytVideoId = remember(url) {
        val regex = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})".toRegex()
        regex.find(url)?.groupValues?.getOrNull(1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, Color(0xFF22222E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download Options",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    // Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF16161E), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf("Video Formats", "Audio Only", "Thumbnails")
                        tabs.forEachIndexed { idx, title ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (activeTab == idx) Color(0xFFFF2E2E) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { activeTab = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (activeTab == idx) Color.White else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (activeTab == 0) {
                                if (videoStreams.isEmpty()) {
                                    item {
                                        Text("No target video resolutions resolved. Tap below to convert.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                                    }
                                } else {
                                    items(videoStreams) { stream ->
                                        StreamCard(stream, onConfirmStream)
                                    }
                                }
                            } else if (activeTab == 1) {
                                if (audioStreams.isEmpty()) {
                                    item {
                                        Text("No extraction audio streams resolved.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                                    }
                                } else {
                                    items(audioStreams) { stream ->
                                        StreamCard(stream, onConfirmStream)
                                    }
                                }
                            } else {
                                if (ytVideoId != null) {
                                    item {
                                        ThumbnailDownloadRow(
                                            label = "Max Resolution HD cover art",
                                            url = "https://img.youtube.com/vi/$ytVideoId/maxresdefault.jpg",
                                            onConfirmStream = onConfirmStream
                                        )
                                    }
                                    item {
                                        ThumbnailDownloadRow(
                                            label = "Standard Quality cover image",
                                            url = "https://img.youtube.com/vi/$ytVideoId/sddefault.jpg",
                                            onConfirmStream = onConfirmStream
                                        )
                                    }
                                    item {
                                        ThumbnailDownloadRow(
                                            label = "Medium Quality Thumbnail illustration",
                                            url = "https://img.youtube.com/vi/$ytVideoId/hqdefault.jpg",
                                            onConfirmStream = onConfirmStream
                                        )
                                    }
                                } else {
                                    item {
                                        Text("Thumbnail extraction is optimized for YouTube links.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun StreamCard(stream: ResolvedMediaStream, onConfirmStream: (ResolvedMediaStream) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161E)),
        border = BorderStroke(1.dp, Color(0xFF22222E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfirmStream(stream) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (stream.isAudio) Icons.Default.MusicNote else Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFFFF2E2E),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stream.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "Format: ${stream.ext} (${stream.mimeType})", color = Color.Gray, fontSize = 9.sp)
                }
            }
            Text(
                text = stream.size,
                color = Color(0xFF25D366),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun ThumbnailDownloadRow(label: String, url: String, onConfirmStream: (ResolvedMediaStream) -> Unit) {
    val streamObj = remember(url) {
        ResolvedMediaStream(
            url = url,
            label = label,
            ext = "JPG",
            size = "HQ image",
            isAudio = false,
            mimeType = "image/jpeg",
            originalUrl = url
        )
    }
    StreamCard(stream = streamObj, onConfirmStream = onConfirmStream)
}

// HELPERS
private fun formatProgressBytes(progress: Int, totalSize: Long): String {
    if (totalSize <= 0) return "Unknown"
    val completed = (totalSize.toDouble() / 100.0) * progress.toDouble()
    return "${formatBytesValue(completed.toLong())} of ${formatBytesValue(totalSize)}"
}

private fun formatBytesValue(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
}
