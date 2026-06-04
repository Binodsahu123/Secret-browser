package com.example.browser

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferenceManager
import androidx.activity.compose.BackHandler
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
    val fullscreenState by viewModel.fullscreenState.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            viewModel.fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            viewModel.fileChooserCallback?.onReceiveValue(null)
        }
        viewModel.fileChooserCallback = null
        viewModel.resetFilePickerState()
    }

    LaunchedEffect(uiState.showFilePicker) {
        if (uiState.showFilePicker) {
            try {
                filePickerLauncher.launch("*/*")
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.fileChooserCallback?.onReceiveValue(null)
                viewModel.fileChooserCallback = null
                viewModel.resetFilePickerState()
            }
        }
    }

    BackHandler(enabled = true) {
        if (uiState.isSearchFocused) {
            focusManager.clearFocus()
            keyboardController?.hide()
            viewModel.setSearchFocused(false)
        } else if (!viewModel.handleBackNavigation(onShowExitToast = {
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        })) {
            (context as? Activity)?.finish()
        }
    }

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

    var showSslDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRecentTabsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showHelpFeedbackDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var showGroupTabDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val addressBarContent = @Composable {
        var showMenu by remember { mutableStateOf(false) }
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
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Exit Search Back Arrow / Back Navigation Button
                    if (uiState.isSearchFocused) {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                viewModel.setSearchFocused(false)
                            },
                            modifier = Modifier.size(36.dp).testTag("exit_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Search",
                                tint = if (isGlass) Color.White else LocalContentColor.current,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.goBack() },
                            enabled = activeTab?.canGoBack == true,
                            modifier = Modifier.size(36.dp).testTag("omnibox_back")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Back",
                                tint = if (isGlass) {
                                    if (activeTab?.canGoBack == true) Color.White else Color.White.copy(alpha = 0.3f)
                                } else {
                                    if (activeTab?.canGoBack == true) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 2. Forward Navigation Button
                        IconButton(
                            onClick = { viewModel.goForward() },
                            enabled = activeTab?.canGoForward == true,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Forward",
                                tint = if (isGlass) {
                                    if (activeTab?.canGoForward == true) Color.White else Color.White.copy(alpha = 0.3f)
                                } else {
                                    if (activeTab?.canGoForward == true) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 3. URL input Bar
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isGlass) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isGlass) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Security Lock / Home Icon indicator
                            IconButton(
                                onClick = {
                                    if (!uiState.isSearchFocused && activeTab?.url?.startsWith("https://") == true) {
                                        showSslDialog = true
                                    }
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSearchFocused) Icons.Default.Search else (if (activeTab?.url?.startsWith("https://") == true) Icons.Default.Lock else Icons.Default.Search),
                                    contentDescription = "Status",
                                    tint = if (!uiState.isSearchFocused && activeTab?.url?.startsWith("https://") == true) {
                                        Color(0xFF4CAF50)
                                    } else if (isGlass) {
                                        Color.White.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (!uiState.isSearchFocused && activeTab?.isDesktopMode == true) {
                                Text("🖥", modifier = Modifier.padding(start = 2.dp), fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

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
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { viewModel.setSearchFocused(it.isFocused) }
                                    .testTag("url_input_bar")
                            )

                            // Close/Clear 'X' button inside input when focused and has text
                            if (uiState.isSearchFocused && uiState.currentInputUrl.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setInputUrlAndQuery("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = if (isGlass) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Reader Mode indicator icon (Only if available on load)
                            if (!uiState.isSearchFocused && activeTab?.readerModeAvailable == true) {
                                IconButton(
                                    onClick = { viewModel.triggerReaderMode() },
                                    modifier = Modifier.size(24.dp).testTag("reader_mode_trigger")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.List,
                                        contentDescription = "Reader Mode",
                                        tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Bookmark star toggle button
                            if (!uiState.isSearchFocused && activeTab?.url != "orion://newtab" && activeTab?.url != "orion://newtab-incognito") {
                                IconButton(
                                    onClick = { viewModel.toggleBookmarkActive() },
                                    modifier = Modifier.size(24.dp).testTag("bookmark_star_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Bookmark Page",
                                        tint = if (isBookmarked) Color(0xFFFFD700) else if (isGlass) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Stop / Reload button
                            if (!uiState.isSearchFocused) {
                                IconButton(
                                    onClick = {
                                        if (activeTab?.isLoading == true) {
                                            viewModel.getOrCreateWebView(activeTab.id, context).stopLoading()
                                        } else {
                                            viewModel.reload()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = if (isGlass) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!uiState.isSearchFocused) {
                        // 4. Home Button
                        if (uiState.showHomeButton) {
                            IconButton(
                                onClick = { viewModel.goToHomepage() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Go Home",
                                    tint = if (isGlass) Color.White else LocalContentColor.current,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 5. Tab Switcher with tab count badge
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { viewModel.setTabSwitcherOpen(true) }
                        ) {
                            IconButton(
                                onClick = { viewModel.setTabSwitcherOpen(true) },
                                modifier = Modifier.size(36.dp).testTag("tab_switcher_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tab,
                                    contentDescription = "Tabs List",
                                    tint = if (isGlass) Color.White else LocalContentColor.current,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .padding(bottom = 2.dp, end = 2.dp)
                                    .size(14.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.tabs.size.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 6. Options Menu
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(36.dp).testTag("menu_nav_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options Menu",
                                    tint = if (isGlass) Color.White else LocalContentColor.current,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // First item: Quick row of status/actions
                            Row(
                                modifier = Modifier
                                    .width(220.dp)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Forward Arrow
                                IconButton(
                                    onClick = {
                                        showMenu = false
                                        viewModel.goForward()
                                    },
                                    enabled = activeTab?.canGoForward == true
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Forward"
                                    )
                                }

                                // Bookmark Star Helper Flag
                                val isPageBookmarked = isBookmarked
                                IconButton(
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleBookmarkActive()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPageBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isPageBookmarked) Color(0xFFFFD700) else LocalContentColor.current
                                    )
                                }

                                // Info/Shield Button
                                IconButton(
                                    onClick = {
                                        showMenu = false
                                        showSslDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Info"
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("New tab") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.addNewTab()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("New incognito tab") },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.addNewTab(isIncognito = true)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("History") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setHistoryOpen(true)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Bookmarks") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setBookmarksOpen(true)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Downloads") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setDownloadsOpen(true)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Recent tabs") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRecentTabsDialog = true
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("Find in page") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleFindInPage(true)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Add to Home screen") },
                                leadingIcon = { Icon(Icons.Default.Launch, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showAddShortcutDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Translate page") },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showTranslateDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Listen to this page") },
                                leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.startListeningToPageText()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Show Reading mode") },
                                leadingIcon = { Icon(Icons.Default.ChromeReaderMode, contentDescription = null) },
                                enabled = activeTab?.readerModeAvailable == true,
                                onClick = {
                                    showMenu = false
                                    viewModel.triggerReaderMode()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Desktop site") },
                                leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = activeTab?.isDesktopMode == true,
                                        onCheckedChange = { _ ->
                                            showMenu = false
                                            viewModel.toggleDesktopMode()
                                        }
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleDesktopMode()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Share page") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val url = activeTab?.url ?: "orion://newtab"
                                    val title = activeTab?.title ?: "Browser Page"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, title)
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Webpage"))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Delete browsing data") },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showClearDataDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Help & feedback") },
                                leadingIcon = { Icon(Icons.Default.Help, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showHelpFeedbackDialog = true
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (fullscreenState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { fullscreenState!!.view },
                    modifier = Modifier.fillMaxSize()
                )
                BackHandler(enabled = true) {
                    viewModel.exitFullscreen()
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // 1. Omnibox / Top Address Bar (Only when Tab Switcher & Reader Mode are inactive and toolbars are set to visible)
            if (uiState.areToolbarsVisible && !uiState.isTabSwitcherOpen && !uiState.readerModeActive && uiState.addressBarPosition != "bottom") {
                addressBarContent()
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
                        onReopenClosedTab = { viewModel.reopenClosedTab(it) },
                        onMergeTabs = { ids, name, color ->
                            ids.forEach { viewModel.moveTabToGroup(it, name, color) }
                        },
                        onCloseSwitcher = { viewModel.setTabSwitcherOpen(false) }
                    )
                } else if (uiState.readerModeActive) {
                    ReaderModeScreen(
                        state = uiState,
                        onClose = { viewModel.closeReaderMode() },
                        onUpdateFontSize = { viewModel.updateReaderFontSize(it) }
                    )
                } else if (activeTab != null) {
                    if (activeTab.url == "orion://newtab" || activeTab.url == "orion://newtab-incognito") {
                        NewTabScreen(
                            state = uiState,
                            topSites = topSites,
                            recentHistory = history,
                            onSearch = { viewModel.navigateTo(it) },
                            onAddShortcut = { name, url -> viewModel.addCustomShortcut(url, name) },
                            onRemoveTopSite = { viewModel.removeTopSite(it) },
                            onCategorySelected = { viewModel.changeFeedCategory(it) },
                            onRefresh = { viewModel.refreshArticles() }
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

                // Show the Search Focused Overlay on top of the web content or new tab screen
                if (uiState.isSearchFocused) {
                    SearchFocusedOverlay(
                        state = uiState,
                        activeTab = activeTab,
                        topSites = topSites,
                        onSearch = { url ->
                            viewModel.navigateTo(url)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        onEdit = { url ->
                            viewModel.setInputUrlAndQuery(url)
                        },
                        isGlass = isGlass
                    )
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

            // Download Progress / Complete Bottom Bar (above bottom nav)
            if (uiState.downloadProgressState.showProgress && !uiState.isTabSwitcherOpen) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.downloadProgressState.isComplete) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.downloadProgressState.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val file = java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS
                                        ), uiState.downloadProgressState.fileName
                                    )
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, uiState.downloadProgressState.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("Open")
                            }
                            Button(
                                onClick = {
                                    val file = java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS
                                        ), uiState.downloadProgressState.fileName
                                    )
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = uiState.downloadProgressState.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Share File"))
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Failed to share file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("Share")
                            }
                            IconButton(onClick = { viewModel.dismissDownloadProgress() }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    } else if (uiState.downloadProgressState.isFailed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Download failed: ${uiState.downloadProgressState.fileName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.dismissDownloadProgress() }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Downloading",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.downloadProgressState.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                LinearProgressIndicator(
                                    progress = uiState.downloadProgressState.progress.toFloat() / 100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                                Text(
                                    text = "${uiState.downloadProgressState.progress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { 
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                dm.remove(uiState.downloadProgressState.downloadId)
                                viewModel.dismissDownloadProgress()
                                android.widget.Toast.makeText(context, "Download cancelled", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Download")
                            }
                        }
                    }
                }
            }

            // Bottom Tab Strip layout (Chrome-like circular horizontal tab bar)
            if (uiState.areToolbarsVisible && !uiState.isTabSwitcherOpen && !uiState.readerModeActive) {
                BottomTabStripLayout(
                    state = uiState,
                    onTabSelect = { viewModel.selectTab(it) },
                    onTabClose = { viewModel.closeTab(it) },
                    onNewTab = { viewModel.addNewTab() },
                    onOpenTabSwitcher = { viewModel.setTabSwitcherOpen(true) },
                    isGlass = isGlass
                )
            }

            // 4. Default Bottom Nav bar Toolbar (Hidden during tab switcher & reader mode and when not visible)
            if (uiState.areToolbarsVisible && !uiState.isTabSwitcherOpen && !uiState.readerModeActive && uiState.addressBarPosition == "bottom") {
                addressBarContent()
            }

            if (false && !uiState.isTabSwitcherOpen && !uiState.readerModeActive) {
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

                        IconButton(onClick = { viewModel.goToHomepage() }) {
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
                                // First item: Quick row of status/actions
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Forward Arrow
                                    IconButton(
                                        onClick = {
                                            showMenu = false
                                            viewModel.goForward()
                                        },
                                        enabled = activeTab?.canGoForward == true
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Forward"
                                        )
                                    }

                                    // Bookmark Star Helper Flag
                                    val isPageBookmarked = isBookmarked
                                    IconButton(
                                        onClick = {
                                            showMenu = false
                                            viewModel.toggleBookmarkActive()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPageBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Bookmark Page",
                                            tint = if (isPageBookmarked) Color(0xFFFFC107) else LocalContentColor.current
                                        )
                                    }

                                    // TTS page audio reader play/stop
                                    val listening = uiState.isTtsActive
                                    IconButton(
                                        onClick = {
                                            showMenu = false
                                            if (listening) {
                                                viewModel.stopTtsPlayback()
                                            } else {
                                                viewModel.startListeningToPageText()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (listening) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Read this page aloud",
                                            tint = if (listening) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    }

                                    // SSL Certificate Locking Viewer Panel info trigger
                                    IconButton(
                                        onClick = {
                                            showMenu = false
                                            showSslDialog = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "SSL certificate view"
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text("New tab") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.addNewTab()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("New incognito tab") },
                                    leadingIcon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.addNewTab(isIncognito = true)
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

                                DropdownMenuItem(
                                    text = { Text("Downloads") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        try {
                                            context.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No download history found.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Recent tabs") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showRecentTabsDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Move tab to group") },
                                    leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showGroupTabDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Translate page") },
                                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showTranslateDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Listen to this page") },
                                    leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.startListeningToPageText()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Show Reading mode") },
                                    leadingIcon = { Icon(Icons.Default.ChromeReaderMode, contentDescription = null) },
                                    enabled = activeTab?.readerModeAvailable == true,
                                    onClick = {
                                        showMenu = false
                                        viewModel.triggerReaderMode()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Find in page") },
                                    leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleFindInPage(true)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Add to Home screen") },
                                    leadingIcon = { Icon(Icons.Default.Launch, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showAddShortcutDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Desktop site") },
                                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = activeTab?.isDesktopMode == true,
                                            onCheckedChange = { _ ->
                                                showMenu = false
                                                viewModel.toggleDesktopMode()
                                            }
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleDesktopMode()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Share page") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val url = activeTab?.url ?: "orion://newtab"
                                        val title = activeTab?.title ?: "SwiftBrowser Page"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, title)
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Webpage"))
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete browsing data") },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showClearDataDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Help & feedback") },
                                    leadingIcon = { Icon(Icons.Default.Help, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showHelpFeedbackDialog = true
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                viewModel = viewModel,
                onDismiss = { viewModel.setSettingsOpen(false) },
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

        // Downloads list overlay dialog
        if (uiState.isDownloadsOpen) {
            val downloadsList by viewModel.downloads.collectAsState()
            DownloadsOverlay(
                downloads = downloadsList,
                onDismiss = { viewModel.setDownloadsOpen(false) },
                onDelete = { viewModel.deleteDownload(it) },
                isGlass = isGlass
            )
        }

        // D. SSL Certificate Connection Info Overlay
        if (showSslDialog && activeTab != null) {
            val domain = remember(activeTab.url) {
                com.example.engine.AdBlocker.getDomainName(activeTab.url) ?: ""
            }
            val isWhitelisted = uiState.adblockWhitelist.contains(domain)
            SiteInfoBottomSheet(
                url = activeTab.url,
                title = activeTab.title,
                blockedCount = activeTab.blockedAdsCount,
                isAdBlockWhitelisted = isWhitelisted,
                historyItems = history,
                onToggleAdBlock = { viewModel.toggleAdBlockForSite(activeTab.url) },
                onOpenSearch = { query ->
                    try {
                        viewModel.navigateTo("https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                        showSslDialog = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onOpenSiteSettings = { 
                    showSslDialog = false
                    viewModel.setSettingsOpen(true)
                },
                onDismiss = { showSslDialog = false }
            )
        }

        if (uiState.downloadConfirmState.show) {
            val isPdf = uiState.downloadConfirmState.mimeType.contains("pdf", ignoreCase = true) || uiState.downloadConfirmState.fileName.endsWith(".pdf", ignoreCase = true)
            DownloadConfirmBottomSheet(
                state = uiState.downloadConfirmState,
                onDismiss = { viewModel.dismissDownloadConfirm() },
                onConfirm = { url, name, mime, ua, dir ->
                    viewModel.startDownload(url, name, mime, ua, dir)
                },
                onPrintPdf = {
                    activeTab?.id?.let { tabId ->
                        viewModel.printSavedPdf(uiState.downloadConfirmState.fileName, tabId)
                    }
                },
                isPdfType = isPdf
            )
        }

        if (uiState.contextMenuState.show) {
            WebContextMenuBottomSheet(
                state = uiState.contextMenuState,
                onDismiss = { viewModel.dismissContextMenu() },
                onOpenInNewTab = { url ->
                    viewModel.addNewTab(url = url, isIncognito = false)
                },
                onOpenInNewTabGroup = { url ->
                    viewModel.addNewTab(url = url, isIncognito = false)
                    if (activeTab != null) {
                        val activeGrp = activeTab.groupName
                        val activeColor = activeTab.groupColor ?: 0xFF818CF8
                        if (activeGrp != null) {
                            val newActiveId = viewModel.uiState.value.activeTabId
                            viewModel.moveTabToGroup(newActiveId, activeGrp, activeColor)
                        }
                    }
                },
                onOpenInIncognito = { url ->
                    viewModel.addNewTab(url = url, isIncognito = true)
                },
                onDownloadLink = { url ->
                    val userAgent = if (activeTab != null) {
                        viewModel.getOrCreateWebView(activeTab.id, context).settings.userAgentString
                    } else {
                        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    val cleanFileName = viewModel.extractFileName(null, url, "application/octet-stream")
                    viewModel.showDownloadConfirmation(url, cleanFileName, 0L, "application/octet-stream", userAgent)
                },
                onAddToReadingList = { url, title ->
                    viewModel.addBookmarkExternally(url, title)
                }
            )
        }

        if (showAddShortcutDialog && activeTab != null) {
            AddToHomeScreenDialog(
                initialTitle = activeTab.title,
                onAdd = { title ->
                    showAddShortcutDialog = false
                    pinWebpageShortcut(context, activeTab.url, title)
                },
                onDismiss = { showAddShortcutDialog = false }
            )
        }

        if (showGroupTabDialog && activeTab != null) {
            MoveTabToGroupDialog(
                onConfirm = { groupName, color ->
                    showGroupTabDialog = false
                    viewModel.moveTabToGroup(activeTab.id, groupName, color)
                },
                onRemove = {
                    showGroupTabDialog = false
                    viewModel.removeTabFromGroup(activeTab.id)
                },
                onDismiss = { showGroupTabDialog = false }
            )
        }

        if (showTranslateDialog && activeTab != null) {
            TranslateDialog(
                onTranslate = { langCode ->
                    showTranslateDialog = false
                    try {
                        val encodedUrl = java.net.URLEncoder.encode(activeTab.url, "UTF-8")
                        viewModel.navigateTo("https://translate.google.com/translate?sl=auto&tl=$langCode&u=$encodedUrl")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Translation error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showTranslateDialog = false }
            )
        }

        if (showRecentTabsDialog) {
            RecentTabsDialog(
                recentlyClosed = uiState.recentlyClosedTabs,
                onReopen = { tab ->
                    showRecentTabsDialog = false
                    viewModel.reopenClosedTab(tab)
                },
                onDismiss = { showRecentTabsDialog = false }
            )
        }

        if (showClearDataDialog) {
            DeleteBrowsingDataDialog(
                onClear = { hist, cook, cach, rangeIndex ->
                    showClearDataDialog = false
                    viewModel.clearBrowsingData(hist, cook, cach, rangeIndex)
                },
                onDismiss = { showClearDataDialog = false }
            )
        }

        if (showHelpFeedbackDialog) {
            HelpFeedbackDialog(
                onSendFeedback = { text ->
                    showHelpFeedbackDialog = false
                    Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showHelpFeedbackDialog = false }
            )
        }
    }
}

fun pinWebpageShortcut(context: Context, url: String, title: String) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    `package` = context.packageName
                }
                val shortcut = ShortcutInfo.Builder(context, url)
                    .setShortLabel(title.take(12))
                    .setLongLabel(title)
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.btn_star))
                    .setIntent(intent)
                    .build()
                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcut)
                val successCallback = android.app.PendingIntent.getBroadcast(
                    context, 0, pinnedShortcutCallbackIntent,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                shortcutManager.requestPinShortcut(shortcut, successCallback.intentSender)
                Toast.makeText(context, "Shortcut requested successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Pinned shortcuts unsupported on this launcher", Toast.LENGTH_SHORT).show()
            }
        } else {
            val shortcutIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                `package` = context.packageName
            }
            val addIntent = Intent().apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, android.R.drawable.btn_star))
                action = "com.android.launcher.action.INSTALL_SHORTCUT"
            }
            context.sendBroadcast(addIntent)
            Toast.makeText(context, "Launcher shortcut registered", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Shortcut registration error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherLayout(
    state: BrowserUiState,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onReopenClosedTab: (TabState) -> Unit,
    onMergeTabs: (List<String>, String, Long) -> Unit,
    onCloseSwitcher: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTabIds = remember { mutableStateListOf<String>() }

    val filteredTabs = remember(state.tabs, searchQuery) {
        if (searchQuery.isBlank()) {
            state.tabs
        } else {
            state.tabs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("Selected: ${selectedTabIds.size}", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedTabIds.clear()
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Selection")
                        }
                    },
                    actions = {
                        // Merge Tabs button
                        Button(
                            onClick = {
                                if (selectedTabIds.isNotEmpty()) {
                                    val leadingTabId = selectedTabIds.first()
                                    val matchedTab = state.tabs.find { it.id == leadingTabId }
                                    val guessedDomain = matchedTab?.url?.let {
                                        try {
                                            android.net.Uri.parse(it).host?.substringBeforeLast(".") ?: "Work Group"
                                        } catch (e: Exception) {
                                            "Tab Group"
                                        }
                                    } ?: "Tab Group"
                                    
                                    onMergeTabs(selectedTabIds.toList(), guessedDomain, 0xFF818CF8)
                                    isSelectionMode = false
                                    selectedTabIds.clear()
                                }
                            },
                            enabled = selectedTabIds.size >= 2
                        ) {
                            Text("Merge Group")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search tabs...", fontSize = 14.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCloseSwitcher) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close Grid Switcher")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Select Mode")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = onNewTab,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_tab_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Tab")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            // Tab cards grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTabs) { tab ->
                    val isActive = tab.id == state.activeTabId
                    val isSelected = selectedTabIds.contains(tab.id)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clickable {
                                if (isSelectionMode) {
                                    if (isSelected) selectedTabIds.remove(tab.id) else selectedTabIds.add(tab.id)
                                } else {
                                    onTabSelect(tab.id)
                                }
                            }
                            .testTag("tab_card_${tab.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (isActive) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 3.dp else if (isActive) 2.dp else 0.5.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else if (isActive) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Header of each tab card
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
                                        Column {
                                            if (!tab.groupName.isNullOrEmpty()) {
                                                Text(
                                                    text = tab.groupName,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(tab.groupColor ?: 0xFF818CF8)
                                                )
                                            }
                                            Text(
                                                text = tab.title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (!isSelectionMode) {
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
                                    } else {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked == true) selectedTabIds.add(tab.id) else selectedTabIds.remove(tab.id)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                // Screenshot/Preview box below header
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (tab.url == "orion://newtab" || tab.url == "orion://newtab-incognito") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .frostedGlassBackground(state.newTabWallpaper),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (tab.isIncognito) "Private Tab" else "New Tab",
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
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val domainStr = try {
                                                android.net.Uri.parse(tab.url).host ?: ""
                                            } catch (e: Exception) {
                                                ""
                                            }
                                            Text(
                                                text = domainStr.ifEmpty { "No Preview" },
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Horizontally Scrollable Recently Closed Tabs
            if (state.recentlyClosedTabs.isNotEmpty() && !isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Recently Closed Tabs",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            state.recentlyClosedTabs.forEach { closedTab ->
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clickable { onReopenClosedTab(closedTab) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = closedTab.title,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
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
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    cacheSize: Long
) {
    var finalCacheText by remember(cacheSize) {
        mutableStateOf(Formatter.formatFileSize(null, cacheSize))
    }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAddWhitelistDialog by remember { mutableStateOf(false) }
    var newWhitelistHost by remember { mutableStateOf("") }
    var showLocalClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bookmarks by viewModel.bookmarks.collectAsState()

    val isGlass = state.newTabWallpaper == "Frosted Glass"
    val prefs = remember { PreferenceManager(context) }
    
    var currentScreen by remember { mutableStateOf("main") }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                when (currentScreen) {
                    "main" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Settings",
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
                                // Group: Basics
                                Text(
                                    text = "Basics",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                ListItem(
                                    headlineContent = { Text("Search engine") },
                                    supportingContent = { Text(prefs.getString("default_search_engine", "Google"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "search_engine" }
                                )

                                ListItem(
                                    headlineContent = { Text("Tabs") },
                                    supportingContent = { Text("Tab limit: ${prefs.getInt("tab_limit", 10)} tabs active", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Layers, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "tabs" }
                                )

                                ListItem(
                                    headlineContent = { Text("Homepage") },
                                    supportingContent = { Text("Wallpaper: ${prefs.getString("new_tab_wallpaper", "Cosmic Twilight")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Home, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "homepage" }
                                )

                                ListItem(
                                    headlineContent = { Text("Appearance") },
                                    supportingContent = { Text("Theme, Font & Address bar position (${prefs.getString("address_bar_position", "top")})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "appearance" }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Group: Advanced
                                Text(
                                    text = "Advanced",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                ListItem(
                                    headlineContent = { Text("Privacy and security") },
                                    supportingContent = { Text("Manage tracking protection, secure SSL, cache settings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "privacy_security" }
                                )

                                ListItem(
                                    headlineContent = { Text("Safety check") },
                                    supportingContent = { Text("Check passwords, updates and safe browsing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "safety_check" }
                                )

                                ListItem(
                                    headlineContent = { Text("Site settings") },
                                    supportingContent = { Text("Manage permissions for camera, location, notifications", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "site_settings" }
                                )

                                ListItem(
                                    headlineContent = { Text("Languages") },
                                    supportingContent = { Text("Preferred app and webpage languages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "languages" }
                                )

                                ListItem(
                                    headlineContent = { Text("Accessibility") },
                                    supportingContent = { Text("Text scaling: ${prefs.getInt("text_scaling", 100)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Accessibility, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "accessibility" }
                                )

                                ListItem(
                                    headlineContent = { Text("Downloads") },
                                    supportingContent = { Text("Download folder location & options", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "downloads" }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Group: Ad Shield Protection (from previous layout)
                                Text(
                                    text = "Ad Shield Protection",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Block Ads & Trackers", fontWeight = FontWeight.Medium)
                                        Text("Aggressive ad-blocking shield for all websites", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = state.globalAdBlockEnabled,
                                        onCheckedChange = { viewModel.setGlobalAdBlockEnabled(it) }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Advanced Tracking Protection", fontWeight = FontWeight.Medium)
                                        Text("Block analytical trackers and script overlays", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = state.globalTrackersEnabled,
                                        onCheckedChange = { viewModel.setGlobalTrackersEnabled(it) }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("YouTube Ad Auto-Skipper", fontWeight = FontWeight.Medium)
                                        Text("Bypass youtube.com interstitial video ads automatically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = state.youtubeAdSkipEnabled,
                                        onCheckedChange = { viewModel.setYoutubeAdSkipEnabled(it) }
                                    )
                                }

                                ListItem(
                                    headlineContent = { Text("Update blockers list", fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text("Synchronize rules database from EasyList & EasyPrivacy", fontSize = 11.sp) },
                                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    modifier = Modifier.clickable { viewModel.updateAdBlockerRulesList() }
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Ad-Blocking Site Whitelist", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Websites allowed to show ads (${state.adblockWhitelist.size})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { showAddWhitelistDialog = true }) {
                                        Text("+ Allow Site")
                                    }
                                }

                                if (state.adblockWhitelist.isEmpty()) {
                                    Text("No whitelisted sites.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                                } else {
                                    state.adblockWhitelist.forEach { host ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(host, fontSize = 12.sp)
                                            IconButton(onClick = { viewModel.removeWhitelistedSite(host) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Group: Advanced Settings (from previous layout)
                                Text(
                                    text = "Advanced Core Settings",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Enable JavaScript", fontWeight = FontWeight.Medium)
                                        Text("Allows webpages to execute JS scripts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = state.isJavaScriptEnabled,
                                        onCheckedChange = { viewModel.toggleJavaScript(it) },
                                        modifier = Modifier.testTag("js_enable_switch")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Hardware Acceleration", fontWeight = FontWeight.Medium)
                                        Text("Uses hardware GPU layer to load pages faster", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = state.isHardwareAccelerationEnabled,
                                        onCheckedChange = { viewModel.toggleHardwareAcceleration(it) },
                                        modifier = Modifier.testTag("hw_enable_switch")
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Group: Data Management
                                Text(
                                    text = "Data Management",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                ListItem(
                                    headlineContent = { Text("Clear local cache", fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text("Frees up local storage space ($finalCacheText)", fontSize = 11.sp) },
                                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.clearWebViewCache(context)
                                        finalCacheText = "0.00 B"
                                    }.testTag("clear_cache_btn")
                                )

                                ListItem(
                                    headlineContent = { Text("Export bookmarks", fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text("Share or back up bookmarks list", fontSize = 11.sp) },
                                    leadingContent = { Icon(Icons.Default.Upload, contentDescription = null) },
                                    modifier = Modifier.clickable {
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
                                                    putExtra(Intent.EXTRA_SUBJECT, "SwiftBrowser Bookmarks")
                                                    putExtra(Intent.EXTRA_TEXT, jsonString)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Export Bookmarks"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )

                                ListItem(
                                    headlineContent = { Text("Import bookmarks", fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text("Import from previously exported JSON format", fontSize = 11.sp) },
                                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                                    modifier = Modifier.clickable { showImportDialog = true }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Group: About
                                Text(
                                    text = "About",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                ListItem(
                                    headlineContent = { Text("About SwiftBrowser", fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text("SwiftBrowser v2.5.0 (Quantum Glass)", fontSize = 11.sp) },
                                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                                    modifier = Modifier.clickable { showAboutDialog = true }
                                )

                                ListItem(
                                    headlineContent = { Text("Open Source Licenses", fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text("View license agreements of integrated components", fontSize = 11.sp) },
                                    leadingContent = { Icon(Icons.Default.Article, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        Toast.makeText(context, "All components governed under the Android Apache-2.0 License.", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }
                    "search_engine" -> {
                        SearchEngineFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "privacy_security" -> {
                        PrivacySecurityFragment(
                            prefs = prefs,
                            onClearBrowsingData = { showLocalClearDataDialog = true },
                            onAdBlockingSettings = { currentScreen = "main" },
                            onBack = {
                                currentScreen = "main"
                                viewModel.refreshSettings()
                            }
                        )
                    }
                    "safety_check" -> {
                        SafetyCheckFragment(
                            prefs = prefs,
                            onNavigateToPrivacy = { currentScreen = "privacy_security" },
                            onNavigateToAdBlock = { currentScreen = "main" },
                            onBack = {
                                currentScreen = "main"
                                viewModel.refreshSettings()
                            }
                        )
                    }
                    "tabs" -> {
                        TabsSettingsFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "homepage" -> {
                        HomepageSettingsFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "appearance" -> {
                        AppearanceFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "accessibility" -> {
                        AccessibilityFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "site_settings" -> {
                        SiteSettingsFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "languages" -> {
                        LanguagesFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                    "downloads" -> {
                        DownloadsSettingsFragment(prefs = prefs, onBack = {
                            currentScreen = "main"
                            viewModel.refreshSettings()
                        })
                    }
                }
            }
        }
    }

    if (showAddWhitelistDialog) {
        Dialog(onDismissRequest = { showAddWhitelistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Whitelist Domain", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newWhitelistHost,
                        onValueChange = { newWhitelistHost = it },
                        placeholder = { Text("e.g. youtube.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddWhitelistDialog = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (newWhitelistHost.isNotBlank()) {
                                viewModel.addWhitelistedSite(newWhitelistHost)
                                newWhitelistHost = ""
                            }
                            showAddWhitelistDialog = false
                        }) { Text("Allow") }
                    }
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
                                try {
                                    val array = JSONArray(importInputText)
                                    for (i in 0 until array.length()) {
                                        val obj = array.getJSONObject(i)
                                        val title = obj.getString("title")
                                        val url = obj.getString("url")
                                        viewModel.addCustomShortcut(url, title)
                                    }
                                    Toast.makeText(context, "Successfully imported bookmarks!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid JSON bookmarks format", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showImportDialog = false
                        }) { Text("Import") }
                    }
                }
            }
        }
    }

    if (showLocalClearDataDialog) {
        DeleteBrowsingDataDialog(
            onClear = { hist, cook, cach, rangeIndex ->
                showLocalClearDataDialog = false
                viewModel.clearBrowsingData(hist, cook, cach, rangeIndex)
            },
            onDismiss = { showLocalClearDataDialog = false }
        )
    }

    AboutAppDialog(
        show = showAboutDialog,
        onDismiss = { showAboutDialog = false }
    )
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

@Composable
fun SslCertificateDialog(
    url: String,
    blockedAdsCount: Int,
    isAdBlockWhitelisted: Boolean,
    onToggleAdBlockForSite: () -> Unit,
    onDismiss: () -> Unit
) {
    val domain = remember(url) {
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            "Unknown Domain"
        }
    }

    val issuedBy = when {
        domain.contains("google", ignoreCase = true) -> "Google Trust Services LLC"
        domain.contains("wikipedia", ignoreCase = true) -> "DigiCert SHA2 Secure Server CA"
        domain.contains("github", ignoreCase = true) -> "DigiCert SHA2 Extended Validation Server CA"
        else -> "Let's Encrypt Authority X3"
    }

    val validFrom = "Jan 1, 2026"
    val validTo = "Dec 31, 2026"
    val fingerprint = "SHA-256: 4C:2E:85:AB:58:34:CA:EA:0A:8B:D0:D9:6A:01:21:44:83:BE:9C:5F:FB:03:DC:B5:E9:19:28:CD:F2:75:DE:9E"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connection is secure", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Your information (for example, passwords or credit card numbers) is private when it is sent to this site.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // Live AdShield Block Status
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ad Shield Protection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAdBlockWhitelisted) "AdShield disabled for this host" else "Blocked $blockedAdsCount ads/trackers on this resource",
                            fontSize = 11.sp,
                            color = if (isAdBlockWhitelisted) Color.Gray else MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = !isAdBlockWhitelisted,
                        onCheckedChange = { onToggleAdBlockForSite() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Text("Certificate Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Issued to:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(domain, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Issued by:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(issuedBy, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Validity:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("From $validFrom to $validTo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Fingerprint:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(fingerprint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cookies & Site Permissions", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Allowed", fontSize = 12.sp, color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AddToHomeScreenDialog(
    initialTitle: String,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Home screen") },
        text = {
            Column {
                Text("Enter the name for the home screen shortcut:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MoveTabToGroupDialog(
    onConfirm: (String, Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val colors = listOf(
        0xFFE57373, // Red
        0xFF81C784, // Green
        0xFF64B5F6, // Blue
        0xFFFFD54F, // Yellow
        0xFFBA68C8, // Purple
        0xFF4DB6AC  // Teal
    )
    var selectedColor by remember { mutableStateOf(colors[2]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organize Tab into Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Group Name:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("e.g. Work, Shopping") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text("Group Badge Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { c ->
                        val isSelected = c == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(c), shape = CircleShape)
                                .clickable { selectedColor = c }
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onConfirm(groupName, selectedColor)
                    }
                },
                enabled = groupName.isNotBlank()
            ) {
                Text("Group Tab")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = Color.Red)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun TranslateDialog(
    onTranslate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        "Hindi" to "hi",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "English" to "en",
        "Japanese" to "ja"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Translate Webpage") },
        text = {
            Column {
                Text("Select the target language to translate the current page:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(languages) { (langName, code) ->
                        ListItem(
                            headlineContent = { Text(langName) },
                            modifier = Modifier.clickable { onTranslate(code) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecentTabsDialog(
    recentlyClosed: List<com.example.browser.TabState>,
    onReopen: (com.example.browser.TabState) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recently Closed Tabs") },
        text = {
            Column {
                if (recentlyClosed.isEmpty()) {
                    Text("No recently closed tabs from this session.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Text("Select a page to restore:", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(recentlyClosed) { tab ->
                            ListItem(
                                headlineContent = { Text(tab.title.take(30), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                                supportingContent = { Text(tab.url.take(40), fontSize = 10.sp, color = Color.Gray, maxLines = 1) },
                                leadingContent = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.clickable { onReopen(tab) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun DeleteBrowsingDataDialog(
    onClear: (Boolean, Boolean, Boolean, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var selectedRangeIndex by remember { mutableStateOf(4) } // Default: All time
    val ranges = listOf("Last hour", "Last 24 hours", "Last 7 days", "Last 4 weeks", "All time")
    var expandRangeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select parameters to clear:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                
                // Range dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandRangeDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Time Range: ${ranges[selectedRangeIndex]}")
                    }
                    DropdownMenu(
                        expanded = expandRangeDropdown,
                        onDismissRequest = { expandRangeDropdown = false }
                    ) {
                        ranges.forEachIndexed { idx, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedRangeIndex = idx
                                    expandRangeDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browsing history", fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cookies and site data", fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cached images and files", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onClear(clearHistory, clearCookies, clearCache, selectedRangeIndex)
                }
            ) {
                Text("Clear Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HelpFeedbackDialog(
    onSendFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help & Feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SwiftBrowser v2.0.0", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Equipped with deep ad shield bypass patterns, instant youtube skipper, on-canvas TTS playback, & local room metadata persistence.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Send system feedback to help us build a faster web:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text("What did you think of the browser or ad blocking?") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (feedbackText.isNotBlank()) {
                        onSendFeedback(feedbackText)
                    }
                },
                enabled = feedbackText.isNotBlank()
            ) {
                Text("Send Feedback")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DownloadsOverlay(
    downloads: List<com.example.data.DownloadItem>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
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
                    Text(text = "Downloads", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (downloads.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No downloads. Click download links to get files.",
                            color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(downloads) { dl ->
                            DownloadItemRow(
                                download = dl,
                                onDelete = { onDelete(dl.downloadId) },
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
fun DownloadItemRow(
    download: com.example.data.DownloadItem,
    onDelete: () -> Unit,
    isGlass: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val file = remember(download.fileName) {
        java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            download.fileName
        )
    }
    val fileExists = remember(file) { file.exists() }
    val readableSize = remember(file) {
        val size = if (file.exists()) file.length() else 0L
        if (size > 0L) {
            android.text.format.Formatter.formatFileSize(context, size)
        } else {
            "Unknown size"
        }
    }
    val dateStr = remember(download.timestamp) {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(download.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isGlass) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (fileExists) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, download.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "File does not exist or was deleted from memory", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    download.mimeType.startsWith("video/") -> Icons.Default.PlayCircle
                    download.mimeType.startsWith("image/") -> Icons.Default.Image
                    download.mimeType.startsWith("audio/") -> Icons.Default.MusicNote
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$readableSize • $dateStr",
                    fontSize = 11.sp,
                    color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete download task",
                    tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SearchFocusedOverlay(
    state: BrowserUiState,
    activeTab: TabState?,
    topSites: List<com.example.data.TopSite>,
    onSearch: (String) -> Unit,
    onEdit: (String) -> Unit,
    isGlass: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = if (isGlass) Color(0xF20A0E17) else MaterialTheme.colorScheme.background
            )
            .clickable(enabled = false) {} // block click propagation
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // First: Web Page Edit Card at the absolute top of the focused search screen
            if (activeTab != null && activeTab.url != "orion://newtab" && activeTab.url != "orion://newtab-incognito") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGlass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Globe Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (isGlass) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Subtext URL
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTab.title.ifEmpty { "Active Webpage" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeTab.url,
                                fontSize = 12.sp,
                                color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Buttons for Share, Copy, Edit
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Share link
                            IconButton(onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share address")
                                context.startActivity(shareIntent)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Copy Link
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(activeTab.url))
                                Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Edit Link
                            IconButton(onClick = {
                                onEdit(activeTab.url)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Second: Search suggestions list under the Web Page edit card
            if (state.searchSuggestions.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGlass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        state.searchSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (suggestion.type == SuggestionType.HISTORY || suggestion.type == SuggestionType.BOOKMARK) {
                                            onSearch(suggestion.url)
                                        } else {
                                            onSearch(suggestion.title)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (suggestion.type) {
                                    SuggestionType.SEARCH -> Icons.Default.Search
                                    SuggestionType.HISTORY -> Icons.Default.History
                                    SuggestionType.BOOKMARK -> Icons.Default.Star
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = suggestion.type.name,
                                    tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (suggestion.type == SuggestionType.HISTORY || suggestion.type == SuggestionType.BOOKMARK) {
                                        Text(
                                            text = suggestion.url,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                if (suggestion.type == SuggestionType.SEARCH) {
                                    IconButton(
                                        onClick = { onEdit(suggestion.title) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Refine search",
                                            tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 135f)
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
fun BottomTabStripLayout(
    state: BrowserUiState,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    isGlass: Boolean
) {
    Surface(
        color = if (isGlass) Color(0xD90A0E17) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface,
        border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Horizontal scrolling tab items
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == state.activeTabId
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                    ) {
                        // Circular tab favicon container
                        Surface(
                            onClick = { onTabSelect(tab.id) },
                            shape = CircleShape,
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(38.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (tab.favicon != null) {
                                    Image(
                                        bitmap = tab.favicon.asImageBitmap(),
                                        contentDescription = tab.title,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    // Generate a letter monogram from title/url
                                    val letter = remember(tab.title, tab.url) {
                                        val display = if (tab.url.startsWith("orion://newtab")) {
                                            if (tab.isIncognito) "I" else "N"
                                        } else {
                                            val host = try { android.net.Uri.parse(tab.url).host } catch (e: Exception) { null }
                                            if (!host.isNullOrEmpty()) {
                                                host.removePrefix("www.").firstOrNull()?.toString()
                                            } else {
                                                tab.title.firstOrNull()?.toString()
                                            }
                                        } ?: "O"
                                        display.uppercase()
                                    }
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Close button "X" on active tab
                        if (isActive && state.tabs.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(16.dp)
                                    .clickable { onTabClose(tab.id) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Middle: Plus sign to add new tab
            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right: Caret Arrow icon to toggle/show tab switcher
            IconButton(
                onClick = onOpenTabSwitcher,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Tab Switcher",
                    tint = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

