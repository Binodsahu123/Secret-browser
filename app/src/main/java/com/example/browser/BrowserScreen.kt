package com.example.browser

import android.app.Activity
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.Bookmark
import com.example.data.HistoryItem
import com.example.data.TopSite
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val topSites by viewModel.topSites.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val activeTab = remember(uiState.tabs, uiState.activeTabId) {
        uiState.tabs.find { it.id == uiState.activeTabId }
    }

    val isBookmarked = remember(bookmarks, activeTab?.url) {
        val currentUrl = activeTab?.url ?: ""
        bookmarks.any { it.url == currentUrl }
    }

    val isGlass = remember(uiState.newTabWallpaper) {
        uiState.newTabWallpaper == "Frosted Glass"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Omnibox / Top Address Bar (Only when Tab Switcher & Reader Mode are inactive)
            if (!uiState.isTabSwitcherOpen && !uiState.readerModeActive) {
                Surface(
                    color = if (isGlass) Color(0xD90A0E17) else MaterialTheme.colorScheme.surface,
                    contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
                    border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
                    tonalElevation = if (isGlass) 0.dp else 4.dp,
                    shadowElevation = if (isGlass) 0.dp else 2.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Navigation Button
                            IconButton(
                                onClick = { viewModel.goBack() },
                                enabled = activeTab?.canGoBack == true,
                                modifier = Modifier.testTag("omnibox_back")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (isGlass) {
                                        if (activeTab?.canGoBack == true) Color.White else Color.White.copy(alpha = 0.3f)
                                    } else {
                                        LocalContentColor.current
                                    }
                                )
                            }

                            // URL input Bar
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .padding(horizontal = 6.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = if (isGlass) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isGlass) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Security Lock / Home Icon indicator
                                    Icon(
                                        imageVector = if (activeTab?.url?.startsWith("https://") == true) Icons.Default.Lock else Icons.Default.Search,
                                        contentDescription = "Security Status",
                                        tint = if (activeTab?.url?.startsWith("https://") == true) {
                                            Color(0xFF4CAF50)
                                        } else if (isGlass) {
                                            Color.White.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // basic editable text field
                                    BasicTextFieldWithoutLabel(
                                        value = uiState.currentInputUrl,
                                        onValueChange = { viewModel.setInputUrlAndQuery(it) },
                                        onDone = {
                                            focusManager.clearFocus()
                                            viewModel.navigateTo(uiState.currentInputUrl)
                                        },
                                        placeholder = "Search or enter address",
                                        textStyle = LocalTextStyle.current.copy(
                                            color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .onFocusChanged { viewModel.setSearchFocused(it.isFocused) }
                                            .testTag("url_input_bar")
                                    )

                                    // Reader Mode indicator icon (Only if available on load)
                                    if (activeTab?.readerModeAvailable == true) {
                                        IconButton(
                                            onClick = { viewModel.triggerReaderMode() },
                                            modifier = Modifier.size(28.dp).testTag("reader_mode_trigger")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = "Reader Mode",
                                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Bookmark star toggle button
                                    if (activeTab?.url != "orion://newtab") {
                                        IconButton(
                                            onClick = { viewModel.toggleBookmarkActive() },
                                            modifier = Modifier.size(28.dp).testTag("bookmark_star_toggle")
                                        ) {
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Bookmark Page",
                                                tint = if (isBookmarked) Color(0xFFFFD700) else if (isGlass) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Stop / Reload button
                                    IconButton(
                                        onClick = {
                                            if (activeTab?.isLoading == true) {
                                                viewModel.getOrCreateWebView(activeTab.id, context).stopLoading()
                                            } else {
                                                viewModel.reload()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                                            contentDescription = "Refresh",
                                            tint = if (isGlass) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Web Loader linear progress indicator
                        if (activeTab?.isLoading == true && activeTab.progress < 100) {
                            LinearProgressIndicator(
                                progress = activeTab.progress / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }

            // 2. Active Web Contents / New Tab Home Screen Frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.isTabSwitcherOpen) {
                    TabSwitcherLayout(
                        state = uiState,
                        onTabSelect = { viewModel.selectTab(it) },
                        onTabClose = { viewModel.closeTab(it) },
                        onNewTab = { viewModel.addNewTab() },
                        onCloseSwitcher = { viewModel.setTabSwitcherOpen(false) }
                    )
                } else if (uiState.readerModeActive) {
                    ReaderModeScreen(
                        state = uiState,
                        onClose = { viewModel.closeReaderMode() },
                        onUpdateFontSize = { viewModel.updateReaderFontSize(it) }
                    )
                } else if (activeTab != null) {
                    if (activeTab.url == "orion://newtab") {
                        NewTabScreen(
                            state = uiState,
                            topSites = topSites,
                            recentHistory = history,
                            onSearch = { viewModel.navigateTo(it) },
                            onAddShortcut = { name, url -> viewModel.addCustomShortcut(url, name) },
                            onRemoveTopSite = { viewModel.removeTopSite(it) }
                        )
                    } else if (activeTab.isWebViewDestroyed) {
                        // Recreate WebView lazily on layout entry
                        val view = viewModel.getOrCreateWebView(activeTab.id, context)
                        view.loadUrl(activeTab.url)
                        AndroidView(
                            factory = { view },
                            modifier = Modifier.fillMaxSize().testTag("webview")
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                viewModel.getOrCreateWebView(activeTab.id, ctx)
                            },
                            update = { /* handled internally */ },
                            modifier = Modifier.fillMaxSize().testTag("webview")
                        )
                    }
                }
            }

            // 3. Find In Page Slider Controls (Bottom Floating panel slide-up)
            AnimatedVisibility(
                visible = uiState.findInPageActive,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut()
            ) {
                FindInPageLayout(
                    state = uiState,
                    onSearchChange = { viewModel.findInPageSearch(it) },
                    onPrev = { viewModel.findInPagePrev() },
                    onNext = { viewModel.findInPageNext() },
                    onClose = { viewModel.toggleFindInPage(false) }
                )
            }

            // 4. Default Bottom Nav bar Toolbar (Hidden during tab switcher & reader mode)
            if (!uiState.isTabSwitcherOpen && !uiState.readerModeActive) {
                Surface(
                    color = if (isGlass) Color(0xD90A0E17) else MaterialTheme.colorScheme.surface,
                    contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
                    border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
                    tonalElevation = if (isGlass) 0.dp else 8.dp,
                    shadowElevation = if (isGlass) 0.dp else 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.goBack() }, enabled = activeTab?.canGoBack == true) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Go Back",
                                tint = if (isGlass) {
                                    if (activeTab?.canGoBack == true) Color.White else Color.White.copy(alpha = 0.3f)
                                } else {
                                    LocalContentColor.current
                                }
                            )
                        }

                        IconButton(onClick = { viewModel.goForward() }, enabled = activeTab?.canGoForward == true) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Go Forward",
                                tint = if (isGlass) {
                                    if (activeTab?.canGoForward == true) Color.White else Color.White.copy(alpha = 0.3f)
                                } else {
                                    LocalContentColor.current
                                }
                            )
                        }

                        IconButton(onClick = { viewModel.navigateTo("orion://newtab") }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Go Home",
                                tint = if (isGlass) Color.White else LocalContentColor.current
                            )
                        }

                        // Tab Switcher with tab count badge
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.clickable { viewModel.setTabSwitcherOpen(true) }
                        ) {
                            IconButton(onClick = { viewModel.setTabSwitcherOpen(true) }, modifier = Modifier.testTag("tab_switcher_btn")) {
                                Icon(
                                    imageVector = Icons.Default.Tab,
                                    contentDescription = "Tabs List",
                                    tint = if (isGlass) Color.White else LocalContentColor.current
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .padding(bottom = 6.dp, end = 6.dp)
                                    .size(18.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.tabs.size.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // General System Tool Menu
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("menu_nav_btn")) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options Menu",
                                    tint = if (isGlass) Color.White else LocalContentColor.current
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Find In Page") },
                                    leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleFindInPage(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setBookmarksOpen(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setHistoryOpen(true)
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setSettingsOpen(true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- dialog overlays ---

        // A. Settings panel overlay screen
        if (uiState.isSettingsOpen) {
            SettingsOverlay(
                state = uiState,
                onDismiss = { viewModel.setSettingsOpen(false) },
                onToggleJS = { viewModel.toggleJavaScript(it) },
                onToggleHW = { viewModel.toggleHardwareAcceleration(it) },
                onWallpaperChange = { viewModel.setNewTabWallpaper(it) },
                onClearCache = {
                    viewModel.clearWebViewCache(context)
                    Toast.makeText(context, "Cache successfully cleared", Toast.LENGTH_SHORT).show()
                },
                onExportBookmarks = {
                    val list = bookmarks
                    if (list.isEmpty()) {
                        Toast.makeText(context, "No bookmarks to export", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val array = JSONArray()
                            list.forEach {
                                val obj = JSONObject()
                                obj.put("title", it.title)
                                obj.put("url", it.url)
                                array.put(obj)
                            }
                            val jsonString = array.toString()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Orion Browser Bookmarks")
                                putExtra(Intent.EXTRA_TEXT, jsonString)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Bookmarks"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onImportBookmarks = { jsonText ->
                    try {
                        val array = JSONArray(jsonText)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val title = obj.getString("title")
                            val url = obj.getString("url")
                            coroutineScope.launch {
                                viewModel.addCustomShortcut(url, title)
                            }
                        }
                        Toast.makeText(context, "Successfully imported bookmarks!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Invalid JSON bookmarks format", Toast.LENGTH_SHORT).show()
                    }
                },
                cacheSize = viewModel.getMemoryCacheSizeInBytes(context)
            )
        }

        // B. Bookmarks list overlay dialog
        if (uiState.isBookmarksOpen) {
            BookmarksOverlay(
                bookmarks = bookmarks,
                onDismiss = { viewModel.setBookmarksOpen(false) },
                onNavigate = {
                    viewModel.navigateTo(it)
                    viewModel.setBookmarksOpen(false)
                },
                onDelete = {
                    coroutineScope.launch {
                        viewModel.toggleBookmarkActive() // removes or toggles
                    }
                },
                isGlass = isGlass
            )
        }

        // C. History list overlay dialog
        if (uiState.isHistoryOpen) {
            HistoryOverlay(
                history = history,
                onDismiss = { viewModel.setHistoryOpen(false) },
                onNavigate = {
                    viewModel.navigateTo(it)
                    viewModel.setHistoryOpen(false)
                },
                onDelete = { viewModel.deleteHistoryItem(it) },
                onClearAll = { viewModel.clearAllHistory() },
                isGlass = isGlass
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherLayout(
    state: BrowserUiState,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseSwitcher: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Open Tabs (${state.tabs.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCloseSwitcher) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Grid Switcher")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_tab_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Tab")
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.tabs) { tab ->
                val isActive = tab.id == state.activeTabId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clickable { onTabSelect(tab.id) }
                        .testTag("tab_card_${tab.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isActive) 2.dp else 0.5.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header of each single Tab item Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (tab.favicon != null) {
                                        Image(
                                            bitmap = tab.favicon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Terminate single active tab button
                                IconButton(
                                    onClick = { onTabClose(tab.id) },
                                    modifier = Modifier.size(24.dp).testTag("close_tab_btn_${tab.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            // Mini webpage JPEG screenshot container
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tab.url == "orion://newtab") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .frostedGlassBackground(state.newTabWallpaper),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "New Tab",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else if (tab.screenshot != null) {
                                    Image(
                                        bitmap = tab.screenshot.asImageBitmap(),
                                        contentDescription = tab.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Web,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No Preview",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
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

@Composable
fun FindInPageLayout(
    state: BrowserUiState,
    onSearchChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("find_in_page_panel")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search page match",
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                BasicTextFieldWithoutLabel(
                    value = state.findInPageQuery,
                    onValueChange = onSearchChange,
                    onDone = {},
                    placeholder = "Find in page...",
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("find_in_page_input")
                )

                if (state.findInPageTotalMatches > 0) {
                    Text(
                        text = "${state.findInPageCurrentMatch} of ${state.findInPageTotalMatches}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else if (state.findInPageQuery.isNotEmpty()) {
                    Text(
                        text = "No matches",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                IconButton(
                    onClick = onPrev,
                    enabled = state.findInPageTotalMatches > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                }

                IconButton(
                    onClick = onNext,
                    enabled = state.findInPageTotalMatches > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).testTag("find_in_page_close")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close search bar")
                }
            }
        }
    }
}

@Composable
fun SettingsOverlay(
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onToggleJS: (Boolean) -> Unit,
    onToggleHW: (Boolean) -> Unit,
    onWallpaperChange: (String) -> Unit,
    onClearCache: () -> Unit,
    onExportBookmarks: () -> Unit,
    onImportBookmarks: (String) -> Unit,
    cacheSize: Long
) {
    var finalCacheText by remember(cacheSize) {
        mutableStateOf(Formatter.formatFileSize(null, cacheSize))
    }
    var showImportDialog by remember { mutableStateOf(false) }

    val isGlass = state.newTabWallpaper == "Frosted Glass"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isGlass) Color(0xD90B1220) else MaterialTheme.colorScheme.surface,
            contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
            border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browser Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Advanced",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SwitchPreference: JavaScript
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable JavaScript", fontWeight = FontWeight.Medium)
                            Text("Allows webpages to execute JS scripts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isJavaScriptEnabled,
                            onCheckedChange = onToggleJS,
                            modifier = Modifier.testTag("js_enable_switch")
                        )
                    }

                    // SwitchPreference: Hardware acceleration
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Hardware Acceleration", fontWeight = FontWeight.Medium)
                            Text("Uses hardware GPU layer to load pages faster", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isHardwareAccelerationEnabled,
                            onCheckedChange = onToggleHW,
                            modifier = Modifier.testTag("hw_enable_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Content & Style",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Homepage Wallpapers config
                    Text("Select Home Wallpaper", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    val wallpapers = listOf("Frosted Glass", "Minimal Slate", "Cosmic Twilight", "Purple Dusk", "Deep Ocean", "Warm Sunrise")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 8.dp
                    ) {
                        wallpapers.forEach { wp ->
                            val isSelected = state.newTabWallpaper == wp
                            Button(
                                onClick = { onWallpaperChange(wp) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(wp, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Data Management",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Clear Cache preference
                    ListItem(
                        headlineContent = { Text("Clear local cache", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Frees up local storage space ($finalCacheText)", fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onClearCache()
                            finalCacheText = "0.00 B"
                        }.testTag("clear_cache_btn")
                    )

                    // Export bookmarks preference
                    ListItem(
                        headlineContent = { Text("Export bookmarks", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Share or back up bookmarks list", fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Default.Upload, contentDescription = null) },
                        modifier = Modifier.clickable { onExportBookmarks() }
                    )

                    // Import bookmarks preference
                    ListItem(
                        headlineContent = { Text("Import bookmarks", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Import from previously exported JSON format", fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                        modifier = Modifier.clickable { showImportDialog = true }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "About",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // About section
                    ListItem(
                        headlineContent = { Text("Version", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Orion Browser v1.0.0 (API 36)", fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )

                    ListItem(
                        headlineContent = { Text("Open Source Licenses", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("View license agreements of integrated components", fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Default.Article, contentDescription = null) },
                        modifier = Modifier.clickable {
                            // Show standard simple license popup
                        }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        var importInputText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Import JSON Bookmarks", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { Text("[{\"title\":\"Google\",\"url\":\"https://google.com\"}]") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (importInputText.isNotBlank()) {
                                onImportBookmarks(importInputText)
                            }
                            showImportDialog = false
                        }) { Text("Import") }
                    }
                }
            }
        }
    }
}

// FlowRow layout helper
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        val lines = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentLine = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentLineWidth = 0

        placeables.forEach { placeable ->
            if (currentLineWidth + placeable.width + mainAxisSpacing.roundToPx() > layoutWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentLineWidth = 0
            }
            currentLine.add(placeable)
            currentLineWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        var totalHeight = 0
        lines.forEachIndexed { index, line ->
            val maxLineHeight = line.maxOf { it.height }
            totalHeight += maxLineHeight
            if (index < lines.size - 1) {
                totalHeight += crossAxisSpacing.roundToPx()
            }
        }

        layout(layoutWidth, totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            lines.forEach { line ->
                var x = 0
                val maxLineHeight = line.maxOf { it.height }
                line.forEach { placeable ->
                    placeable.placeRelative(x, y + (maxLineHeight - placeable.height) / 2)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += maxLineHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}

@Composable
fun BookmarksOverlay(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    isGlass: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isGlass) Color(0xD90B1220) else MaterialTheme.colorScheme.surface,
            contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
            border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Saved Bookmarks", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (bookmarks.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No saved bookmarks yet.",
                            color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookmarks) { bm ->
                            BookmarkItemRow(
                                bookmark = bm,
                                onClick = { onNavigate(bm.url) },
                                onDelete = { onDelete(bm) },
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkItemRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isGlass: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isGlass) Color.White else Color.Unspecified
                )
                Text(
                    text = bookmark.url,
                    fontSize = 11.sp,
                    color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Bookmark",
                    tint = if (isGlass) Color.White.copy(alpha = 0.8f) else LocalContentColor.current
                )
            }
        }
    }
}

@Composable
fun HistoryOverlay(
    history: List<HistoryItem>,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onClearAll: () -> Unit,
    isGlass: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isGlass) Color(0xD90B1220) else MaterialTheme.colorScheme.surface,
            contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
            border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Viewing History", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row {
                        if (history.isNotEmpty()) {
                            TextButton(onClick = onClearAll) {
                                Text("Clear", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isGlass) Color.White else LocalContentColor.current
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (history.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No history recorded yet.",
                            color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { hm ->
                            HistoryItemRow(
                                historyItem = hm,
                                onClick = { onNavigate(hm.url) },
                                onDelete = { onDelete(hm.id) },
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    historyItem: HistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isGlass: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = historyItem.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isGlass) Color.White else Color.Unspecified
                )
                Text(
                    text = historyItem.url,
                    fontSize = 11.sp,
                    color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete history",
                    tint = if (isGlass) Color.White.copy(alpha = 0.8f) else LocalContentColor.current
                )
            }
        }
    }
}
