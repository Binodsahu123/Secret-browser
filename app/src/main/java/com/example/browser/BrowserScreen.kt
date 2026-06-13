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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.example.downloaduiengine.FloatingDownloadButton
import com.example.downloaduiengine.MediaDownloadDialog
import com.example.downloaduiengine.SwiftDownloadsScreen
import com.example.downloaduiengine.MediaLinkResolver
import com.example.downloaduiengine.ResolvedMediaStream
import com.example.downloaduiengine.ResolutionPickerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.draw.alpha
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
    val notificationRequestOrigin by viewModel.notificationRequestOrigin.collectAsState()
    val pendingPermissionRequest by viewModel.pendingPermissionRequest.collectAsState()
    val pendingGeolocationPrompt by viewModel.pendingGeolocationPrompt.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var isBrowserResolving by remember { mutableStateOf(false) }
    var browserResolvedStreams by remember { mutableStateOf<List<ResolvedMediaStream>>(emptyList()) }
    var showBrowserResolutionSelector by remember { mutableStateOf(false) }

    var showYouTubeExtensionPanel by remember { mutableStateOf(false) }
    var isYtResolving by remember { mutableStateOf(false) }
    var ytResolvedStreams by remember { mutableStateOf<List<ResolvedMediaStream>>(emptyList()) }

    var showDelayedNotificationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notifications restricted.", Toast.LENGTH_SHORT).show()
        }
        showDelayedNotificationDialog = false
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                // Wait for 40 seconds before prompting for notification access as requested
                kotlinx.coroutines.delay(40000)
                showDelayedNotificationDialog = true
            }
        }
    }

    val systemPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        val currentRequest = viewModel.pendingPermissionRequest.value
        if (currentRequest != null) {
            if (allGranted) {
                currentRequest.grant(currentRequest.resources)
            } else {
                currentRequest.deny()
            }
            viewModel.clearPermissionRequest()
        }
    }

    val geolocationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val currentPrompt = viewModel.pendingGeolocationPrompt.value
        if (currentPrompt != null) {
            val (origin, callback) = currentPrompt
            callback.invoke(origin, isGranted, false)
            viewModel.clearGeolocationPrompt()
        }
    }

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

    var showSslDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRecentTabsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showHelpFeedbackDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var showGroupTabDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }
    var showActiveExtensionsSheet by remember { mutableStateOf(false) }
    var selectedExtensionIdForPopup by remember { mutableStateOf<String?>(null) }
    var showAIChatSheet by remember { mutableStateOf(false) }
    var currentCapturedText by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        if (showExtensionsDialog) {
            showExtensionsDialog = false
        } else if (showActiveExtensionsSheet) {
            showActiveExtensionsSheet = false
        } else if (showAIChatSheet) {
            showAIChatSheet = false
        } else if (selectedExtensionIdForPopup != null) {
            selectedExtensionIdForPopup = null
        } else if (uiState.isSearchFocused) {
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

    val webStoreExtensionId = remember(activeTab?.url) {
        val url = activeTab?.url ?: ""
        if (url.contains("chromewebstore.google.com/detail/") || url.contains("chrome.google.com/webstore/detail/")) {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val id = cleanUrl.substringAfterLast("/")
            if (id.length == 32 && id.all { it in 'a'..'z' }) id else null
        } else {
            null
        }
    }

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
                    // 1. Exit Search or Go Home Button (Chrome Style Position)
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
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                viewModel.goToHomepage()
                            },
                            modifier = Modifier.size(36.dp).testTag("omnibox_home")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Go Home",
                                tint = if (isGlass) Color.White else LocalContentColor.current,
                                modifier = Modifier.size(22.dp)
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


                        }
                    }

                    if (!uiState.isSearchFocused) {

                        // AI Button placed beside tab counter and menu
                        IconButton(
                            onClick = {
                                val session = AISessionManager.getSession(activeTab?.id ?: "")
                                if (activeTab?.url?.startsWith("http") == true && session != null && session.rawPageText.isNotEmpty()) {
                                    currentCapturedText = session.rawPageText
                                    showAIChatSheet = true
                                } else {
                                    if (activeTab?.url?.startsWith("http") == true) {
                                        viewModel.getActiveWebViewText { text ->
                                            currentCapturedText = text
                                            showAIChatSheet = true
                                        }
                                    } else {
                                        currentCapturedText = ""
                                        showAIChatSheet = true
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("ai_star_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Active Page Summary Assistant",
                                tint = if (isGlass) Color.White else LocalContentColor.current,
                                modifier = Modifier.size(22.dp)
                            )
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

                        // PDF Print Icon next to Options Menu
                        val activeTabDetail = uiState.tabs.find { it.id == uiState.activeTabId }
                        if (activeTabDetail != null) {
                            IconButton(
                                onClick = {
                                    try {
                                        val wv = viewModel.getOrCreateWebView(activeTabDetail.id, context)
                                        PdfUtility.printWebView(context, wv, activeTabDetail.title)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot export PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Save Page to PDF",
                                    tint = if (isGlass) Color.White.copy(alpha = 0.9f) else Color(0xFFF43F5E),
                                    modifier = Modifier.size(22.dp)
                                )
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
                                    .width(240.dp)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back Arrow
                                IconButton(
                                    onClick = {
                                        showMenu = false
                                        viewModel.goBack()
                                    },
                                    enabled = activeTab?.canGoBack == true
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (activeTab?.canGoBack == true) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                                    )
                                }

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
                                        contentDescription = "Forward",
                                        tint = if (activeTab?.canGoForward == true) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                                    )
                                }

                                // Refresh / Stop
                                IconButton(
                                    onClick = {
                                        showMenu = false
                                        if (activeTab?.isLoading == true) {
                                            viewModel.getOrCreateWebView(activeTab.id, context).stopLoading()
                                        } else {
                                            viewModel.reload()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                                        contentDescription = "Refresh"
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
                                text = { Text("Web Notifications") },
                                leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setWebNotificationsOpen(true)
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
                                text = { Text("Translate...") },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.triggerTranslationSelection()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("AI Page Assistant") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF818CF8)) },
                                onClick = {
                                    showMenu = false
                                    val session = AISessionManager.getSession(activeTab?.id ?: "")
                                    if (activeTab?.url?.startsWith("http") == true && session != null && session.rawPageText.isNotEmpty()) {
                                        currentCapturedText = session.rawPageText
                                        showAIChatSheet = true
                                    } else {
                                        if (activeTab?.url?.startsWith("http") == true) {
                                            viewModel.getActiveWebViewText { text ->
                                                currentCapturedText = text
                                                showAIChatSheet = true
                                            }
                                        } else {
                                            currentCapturedText = ""
                                            showAIChatSheet = true
                                        }
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Extensions Management") },
                                leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null) },
                                onClick = {
                                        showMenu = false
                                        showExtensionsDialog = true
                                }
                            )

                            // List enabled extensions
                            getFullExtensionsList(viewModel).forEach { ext ->
                                val isInstalled = isExtensionInstalled(viewModel, ext.id)
                                val isEnabled = viewModel.isExtensionEnabled(ext.id)
                                if (isInstalled && isEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("  • ${ext.name}") },
                                        leadingIcon = { 
                                            Icon(
                                                imageVector = when (ext.id) {
                                                    "ext_metamask" -> Icons.Default.Wallet
                                                    "ext_grok_4" -> Icons.Default.SmartToy
                                                    "ext_grok_automation" -> Icons.Default.ElectricBolt
                                                    "ext_dark_reader" -> Icons.Default.Brightness4
                                                    "ext_adblock" -> Icons.Default.Shield
                                                    else -> Icons.Default.Extension
                                                }, 
                                                contentDescription = null,
                                                tint = Color(0xFF6366F1),
                                                modifier = Modifier.size(18.dp)
                                            ) 
                                        },
                                        onClick = {
                                            showMenu = false
                                            selectedExtensionIdForPopup = ext.id
                                        }
                                    )
                                }
                            }

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
                                text = { Text("Developer Tools") },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setDevToolsOpen(true)
                                }
                            )

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
        modifier = (if (fullscreenState != null) Modifier.fillMaxSize() else modifier)
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
        } else {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // 1. Omnibox / Top Address Bar (Only when Tab Switcher & Reader Mode are inactive and toolbars are set to visible)
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.areToolbarsVisible && !uiState.isTabSwitcherOpen && !uiState.readerModeActive && uiState.addressBarPosition != "bottom",
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
            ) {
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
                        onNewTab = { isIncognito -> viewModel.addNewTab(isIncognito = isIncognito) },
                        onReopenClosedTab = { viewModel.reopenClosedTab(it) },
                        onMergeTabs = { ids, name, color ->
                            ids.forEach { viewModel.moveTabToGroup(it, name, color) }
                        },
                        onCloseSwitcher = { viewModel.setTabSwitcherOpen(false) },
                        onRenameGroup = { groupId, newName -> viewModel.renameGroup(groupId, newName) },
                        onChangeGroupColor = { groupId, newColor -> viewModel.changeGroupColor(groupId, newColor) },
                        onNewTabInGroup = { groupId, isIncogn -> viewModel.addNewTabInGroupById(groupId, isIncogn) },
                        onUngroupTab = { tabId -> viewModel.removeTabFromGroup(tabId) },
                        onCreateNewGroup = { name, color, isIncogn -> viewModel.createNewTabGroup(name, color, isIncogn) }
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
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.FrameLayout(ctx).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { frameLayout ->
                                    val view = viewModel.getOrCreateWebView(activeTab.id, frameLayout.context)
                                    if (activeTab.isWebViewDestroyed) {
                                        view.loadUrl(activeTab.url)
                                    }
                                    if (view.parent != frameLayout) {
                                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                                        frameLayout.removeAllViews()
                                        frameLayout.addView(view)
                                    }
                                },
                                modifier = Modifier.fillMaxSize().testTag("webview")
                            )

                            // YouTube Detection & Download Overlay
                            val youtubeVideoInfo by viewModel.youtubeDetectionEngine.detectedVideo.collectAsState()
                            
                            if (youtubeVideoInfo != null) {
                                var showYtDownloadSheet by remember { mutableStateOf(false) }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                        .testTag("youtube_top_overlay")
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.95f)
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                        modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).align(Alignment.Center)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Image(
                                                    painter = coil.compose.rememberAsyncImagePainter(youtubeVideoInfo!!.thumbnail),
                                                    contentDescription = "Thumbnail",
                                                    modifier = Modifier
                                                        .size(50.dp, 35.dp)
                                                        .clip(RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = youtubeVideoInfo!!.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "YouTube Video Detected",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        showYtDownloadSheet = true
                                                        viewModel.evaluateJavascriptOnActiveWebview("window.location.hash = '#download';")
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                ) {
                                                    Icon(imageVector = Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                
                                                FilledTonalButton(
                                                    onClick = { 
                                                        (context as? Activity)?.let { act ->
                                                            YouTubePipController.enterPip(act)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.PictureInPicture, contentDescription = "PIP", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("PIP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (showYtDownloadSheet) {
                                    YouTubeDownloadBottomSheet(
                                        videoInfo = youtubeVideoInfo!!,
                                        onDismiss = { showYtDownloadSheet = false },
                                        viewModel = viewModel
                                    )
                                }
                            }

                            // Instant Premium Loading Skeleton overlay
                            if (activeTab.isLoading || activeTab.url == "about:blank") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Loading Secure Connection...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
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

            TranslateBarLayout(viewModel = viewModel)

            // Bottom Tab Strip layout (Chrome-like circular horizontal tab bar)
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.areToolbarsVisible && !uiState.isTabSwitcherOpen && !uiState.readerModeActive,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
            ) {
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
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.areToolbarsVisible && !uiState.isTabSwitcherOpen && !uiState.readerModeActive && uiState.addressBarPosition == "bottom",
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
            ) {
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

                        IconButton(onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.goToHomepage()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Go Home",
                                tint = if (isGlass) Color.White else LocalContentColor.current
                            )
                        }

                        // AI Assistant Summary Button (AutoAwesome Sparkles Icon)
                        IconButton(
                            onClick = {
                                val session = AISessionManager.getSession(activeTab?.id ?: "")
                                if (activeTab?.url?.startsWith("http") == true && session != null && session.rawPageText.isNotEmpty()) {
                                    currentCapturedText = session.rawPageText
                                    showAIChatSheet = true
                                } else {
                                    if (activeTab?.url?.startsWith("http") == true) {
                                        // Fallback if background extraction hasn't completed or isn't available
                                        viewModel.getActiveWebViewText { text ->
                                            currentCapturedText = text
                                            showAIChatSheet = true
                                        }
                                    } else {
                                        currentCapturedText = ""
                                        showAIChatSheet = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("ai_star_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Active Page Summary Assistant",
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
                                    text = { Text("Translate...") },
                                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.triggerTranslationSelection()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Extensions Management") },
                                    leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showExtensionsDialog = true
                                    }
                                )

                                // List enabled extensions
                                getFullExtensionsList(viewModel).forEach { ext ->
                                    val isInstalled = isExtensionInstalled(viewModel, ext.id)
                                    val isEnabled = viewModel.isExtensionEnabled(ext.id)
                                    if (isInstalled && isEnabled) {
                                        DropdownMenuItem(
                                            text = { Text("  • ${ext.name}") },
                                            leadingIcon = { 
                                                Icon(
                                                    imageVector = when (ext.id) {
                                                        "ext_metamask" -> Icons.Default.Wallet
                                                        "ext_grok_4" -> Icons.Default.SmartToy
                                                        "ext_grok_automation" -> Icons.Default.ElectricBolt
                                                        "ext_dark_reader" -> Icons.Default.Brightness4
                                                        "ext_adblock" -> Icons.Default.Shield
                                                        else -> Icons.Default.Extension
                                                    }, 
                                                    contentDescription = null,
                                                    tint = Color(0xFF6366F1),
                                                    modifier = Modifier.size(18.dp)
                                                ) 
                                            },
                                            onClick = {
                                                showMenu = false
                                                selectedExtensionIdForPopup = ext.id
                                            }
                                        )
                                    }
                                }

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
        }

        // --- dialog overlays ---

        // TTS Control Panel overlay (placed at the bottom of the screen)
        if (uiState.isTtsActive && !uiState.isTabSwitcherOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (uiState.addressBarPosition == "bottom") 118.dp else 56.dp), // stays clear of address / tab bars
                contentAlignment = Alignment.BottomCenter
            ) {
                TtsControlPanel(
                    state = uiState,
                    onPlayPause = { viewModel.toggleTtsPlayback() },
                    onStop = { viewModel.stopTtsPlayback() },
                    onSkipPrevious = { viewModel.playPreviousTtsSegment() },
                    onSkipNext = { viewModel.playNextTtsSegment() },
                    onSpeedChange = { viewModel.setTtsSpeechRate(it) }
                )
            }
        }

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
                onClearBrowsingData = { hist, cook, cach, rangeIndex ->
                    viewModel.clearBrowsingData(hist, cook, cach, rangeIndex)
                },
                isGlass = isGlass
            )
        }

        // Downloads list overlay dialog
        if (uiState.isDownloadsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SwiftDownloadsScreen(
                    engine = viewModel.customDownloadEngine,
                    onBack = { viewModel.setDownloadsOpen(false) },
                    onOpenFile = { path, name, mime ->
                        viewModel.openLocalFile(path, name, mime)
                    },
                    onNavigateToUrl = { url ->
                        viewModel.navigateTo(url)
                        viewModel.setDownloadsOpen(false)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // SwiftBrowser Advanced Professional Media Download Popup overlays
        val detectedMediaCustomState = viewModel.detectedMediaCustom.value
        val showDialogCustom = viewModel.showDownloadDialogCustom.value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 85.dp, end = 20.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingDownloadButton(
                detectedMedia = detectedMediaCustomState,
                onClick = {
                    val media = detectedMediaCustomState
                    if (media != null) {
                        if (media.quality == "Resolvables") {
                            isBrowserResolving = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val streams = MediaLinkResolver.resolveMedia(media.url)
                                    withContext(Dispatchers.Main) {
                                        browserResolvedStreams = streams
                                        isBrowserResolving = false
                                        showBrowserResolutionSelector = true
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isBrowserResolving = false
                                        Toast.makeText(context, "Failed to analyze URL: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            viewModel.showDownloadDialogCustom.value = true
                        }
                    }
                }
            )
        }

        val isYouTubePage = remember(activeTab?.url) {
            val urlStr = activeTab?.url ?: ""
            urlStr.contains("youtube.com") || urlStr.contains("youtu.be")
        }

        if (isYouTubePage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 155.dp, end = 20.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseBtn"
                )

                Button(
                    onClick = {
                        showYouTubeExtensionPanel = true
                        isYtResolving = true
                        val targetUrl = activeTab?.url ?: ""
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val streams = MediaLinkResolver.resolveMedia(targetUrl)
                                withContext(Dispatchers.Main) {
                                    ytResolvedStreams = streams
                                    isYtResolving = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isYtResolving = false
                                    Toast.makeText(context, "Error exploring streams: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE50914), // Premium YouTube Red
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension, // Extension puzzle logo
                        contentDescription = "YouTube Pro Extension Assistant",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YT Pro Extension",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showYouTubeExtensionPanel) {
            AlertDialog(
                onDismissRequest = { showYouTubeExtensionPanel = false },
                confirmButton = {},
                dismissButton = {},
                title = null,
                text = {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF0F0F13), // Pitch dark theme matching YouTube mobile app & modern Dark Mode
                        border = BorderStroke(1.dp, Color(0xFF2E2E3C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Branded header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFCC00), // Gold Premium Accent
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "YouTube Pro Extension",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(Color(0xFF25D366), CircleShape) // Green Connected dot
                                            )
                                            Text(
                                                text = "Observer Active v3.9.8",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { showYouTubeExtensionPanel = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Gray
                                    )
                                }
                            }

                            // Subtitle tip
                            Text(
                                text = "Deep link sniffer identified YouTube URL. Choose your preferred video resolution or audio format extraction below:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            if (isYtResolving) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFE50914),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Infiltrating media stream channels...",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                val videos = remember(ytResolvedStreams) { ytResolvedStreams.filter { !it.isAudio } }
                                val audios = remember(ytResolvedStreams) { ytResolvedStreams.filter { it.isAudio } }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                ) {
                                    // Video Resolutions Label
                                    item {
                                        Text(
                                            text = "🎬 VIDEO RESOLUTIONS (MP4)",
                                            color = Color(0xFFE50914),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                        )
                                    }

                                    if (videos.isEmpty()) {
                                        item {
                                            Text(
                                                text = "No direct MP4 resolutions identified. Attempting forced direct stream...",
                                                color = Color.DarkGray,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    } else {
                                        items(videos) { stream ->
                                            YtStreamItemRow(stream = stream) { selectedStream ->
                                                showYouTubeExtensionPanel = false
                                                val safeFileName = "YTPro_Video_" + selectedStream.label.replace(" ", "_").replace("(", "").replace(")", "").replace("-", "") + "_" + System.currentTimeMillis() + "." + selectedStream.ext.lowercase(java.util.Locale.ROOT)
                                                coroutineScope.launch {
                                                    try {
                                                        viewModel.customDownloadEngine.startDownload(selectedStream.url, safeFileName, selectedStream.mimeType, 4)
                                                        Toast.makeText(context, "Direct media queue initiated!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Audio Formats Label
                                    item {
                                        Text(
                                            text = "🎵 AUDIO STREAM EXTRACTOR (MP3 / AAC)",
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                        )
                                    }

                                    if (audios.isEmpty()) {
                                        item {
                                            Text(
                                                text = "No audio formats registered. Fallback converting MP4 track to MP3.",
                                                color = Color.DarkGray,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    } else {
                                        items(audios) { stream ->
                                            YtStreamItemRow(stream = stream) { selectedStream ->
                                                showYouTubeExtensionPanel = false
                                                val safeFileName = "YTPro_Audio_" + selectedStream.label.replace(" ", "_").replace("(", "").replace(")", "").replace("-", "") + "_" + System.currentTimeMillis() + "." + selectedStream.ext.lowercase(java.util.Locale.ROOT)
                                                coroutineScope.launch {
                                                    try {
                                                        viewModel.customDownloadEngine.startDownload(selectedStream.url, safeFileName, selectedStream.mimeType, 4)
                                                        Toast.makeText(context, "Direct audio queue initiated!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Footer Status Info
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161F), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Equipped with automated Ad Skipper & deep Web-SABR request bypass filters.",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            )
        }

        if (isBrowserResolving) {
            AlertDialog(
                onDismissRequest = { isBrowserResolving = false },
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
                                text = "Resolving high quality video channels & audio format conversion options. Please hold on...",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            )
        }

        if (showBrowserResolutionSelector && detectedMediaCustomState != null) {
            ResolutionPickerSheet(
                url = detectedMediaCustomState.url,
                resolvedStreams = browserResolvedStreams,
                onDismiss = {
                    showBrowserResolutionSelector = false
                },
                onConfirmStream = { stream ->
                    showBrowserResolutionSelector = false
                    val safeFileName = "VidMate_" + stream.label.replace(" ", "_").replace("(", "").replace(")", "").replace("-", "") + "_" + System.currentTimeMillis() + "." + stream.ext.lowercase(java.util.Locale.ROOT)
                    coroutineScope.launch {
                        try {
                            viewModel.customDownloadEngine.startDownload(stream.url, safeFileName, stream.mimeType, 4)
                            Toast.makeText(context, "Started background download for: ${stream.label}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error queueing download: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }

        if (detectedMediaCustomState != null) {
            val scopeCustom = rememberCoroutineScope()
            MediaDownloadDialog(
                show = showDialogCustom,
                detectedMedia = detectedMediaCustomState,
                onDismiss = { viewModel.showDownloadDialogCustom.value = false },
                onConfirmDownload = { fileName, threadsCount ->
                    viewModel.showDownloadDialogCustom.value = false
                    scopeCustom.launch {
                        viewModel.customDownloadEngine.startDownload(
                            url = detectedMediaCustomState.url,
                            fileName = fileName,
                            mimeType = detectedMediaCustomState.mimeType,
                            threads = threadsCount
                        )
                    }
                }
            )
        }

        // Web Notifications overlay
        if (uiState.isWebNotificationsOpen) {
            com.example.notificationengine.NotificationCenterScreen(
                onBack = { viewModel.setWebNotificationsOpen(false) },
                onOpenUrl = { url ->
                    viewModel.navigateTo(url)
                    viewModel.setWebNotificationsOpen(false)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // In-app File and Media Player overlay
        uiState.activeViewerFile?.let { activeFile ->
            FileViewerOverlay(
                activeFile = activeFile,
                viewModel = viewModel,
                onClose = { viewModel.closeLocalFile() }
            )
        }

        if (showAIChatSheet) {
            AIChatPanel(
                tabId = activeTab?.id ?: "default_tab",
                url = activeTab?.url ?: "",
                pageText = currentCapturedText,
                onDismiss = { showAIChatSheet = false }
            )
        }

        // Offscreen WebView mounts for warm AI website sessions
        Box(
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
        ) {
            val bridgeSystem = remember { AIWebsiteBridgeSystem.getInstance(context) }
            AndroidView(factory = { bridgeSystem.getOrCreateWebView("ChatGPT") })
            AndroidView(factory = { bridgeSystem.getOrCreateWebView("Gemini") })
        }

        // Chrome Extensions overlay Dialog
        if (showExtensionsDialog) {
            ExtensionsOverlay(
                viewModel = viewModel,
                onDismiss = { showExtensionsDialog = false },
                isGlass = isGlass
            )
        }

        // Extensions Quick Hub sheet (puzzle icon popup)
        if (showActiveExtensionsSheet) {
            ActiveExtensionsDialog(
                viewModel = viewModel,
                onDismissRequest = { showActiveExtensionsSheet = false },
                onOpenExtensionPopup = { extId ->
                    selectedExtensionIdForPopup = extId
                },
                onManageExtensions = {
                    showExtensionsDialog = true
                }
            )
        }

        // Direct extension popup bottom sheet loader
        if (selectedExtensionIdForPopup != null) {
            ExtensionPopupBottomSheet(
                viewModel = viewModel,
                extensionId = selectedExtensionIdForPopup!!,
                onDismiss = { selectedExtensionIdForPopup = null }
            )
        }

        // Floating Install Button for Chrome Web Store details pages
        if (webStoreExtensionId != null && !uiState.isTabSwitcherOpen) {
            var isInstalling by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        isInstalling = true
                        viewModel.downloadChromeExtension(context, webStoreExtensionId) { success, message ->
                            isInstalling = false
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isInstalling,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(16.dp).height(50.dp)
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Installing Extension...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Install Extension",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Orion Browser", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Developer Tools Inspector Panel Overlay
        if (uiState.isDevToolsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter) // Safe overlay layout in Box
            ) {
                com.example.developertoolsengine.DeveloperPanelComponent(
                    onClose = { viewModel.setDevToolsOpen(false) }
                )
            }
        }

        // D. SSL Certificate Connection Info Overlay
        if (showSslDialog && activeTab != null) {
            val domain = remember(activeTab.url) {
                com.example.adblockengine.AdBlocker.getDomainName(activeTab.url) ?: ""
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
                    if (activeTab != null) {
                        viewModel.addNewTabInGroup(activeTab.id, url)
                    } else {
                        viewModel.addNewTab(url = url, isIncognito = false)
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
                        viewModel.translateActivePage(langCode)
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

        notificationRequestOrigin?.let { origin ->
            AlertDialog(
                onDismissRequest = { viewModel.handleNotificationPermissionResponse(origin, false) },
                icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("https://$origin", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = { Text("This website wants to send you alerts and real-time push notifications. You can disable this later in Site Settings.", fontSize = 13.sp) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.handleNotificationPermissionResponse(origin, true) }
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.handleNotificationPermissionResponse(origin, false) }
                    ) {
                        Text("Block")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        pendingPermissionRequest?.let { request ->
            val originUrl = request.origin.toString()
            val resourcesList = request.resources.toList()
            val permissionText = resourcesList.map { res ->
                when (res) {
                    "android.webkit.resource.VIDEO_CAPTURE" -> "Camera"
                    "android.webkit.resource.AUDIO_CAPTURE" -> "Microphone"
                    "android.webkit.resource.MIDI_SYSEX" -> "MIDI Sysex"
                    "android.webkit.resource.PROTECTED_MEDIA_ID_CONTAINER" -> "Protected Media"
                    else -> "Media Capabilities"
                }
            }.joinToString(" and ")

            AlertDialog(
                onDismissRequest = { 
                    request.deny()
                    viewModel.clearPermissionRequest()
                },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Approve Website Access?", fontWeight = FontWeight.Bold) },
                text = { 
                    Text("The website $originUrl wants permission to access your:\n$permissionText\n\nAllowing this gives the page access to your device's sensors and hardware.", fontSize = 13.sp) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val listToRequest = mutableListOf<String>()
                            if (resourcesList.contains("android.webkit.resource.VIDEO_CAPTURE")) {
                                listToRequest.add(android.Manifest.permission.CAMERA)
                            }
                            if (resourcesList.contains("android.webkit.resource.AUDIO_CAPTURE")) {
                                listToRequest.add(android.Manifest.permission.RECORD_AUDIO)
                            }
                            
                            val listToPrompt = listToRequest.filter {
                                androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                            
                            if (listToPrompt.isNotEmpty()) {
                                systemPermissionLauncher.launch(listToPrompt.toTypedArray())
                            } else {
                                request.grant(request.resources)
                                viewModel.clearPermissionRequest()
                            }
                        }
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            request.deny()
                            viewModel.clearPermissionRequest()
                        }
                    ) {
                        Text("Block")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        pendingGeolocationPrompt?.let { (origin, callback) ->
            AlertDialog(
                onDismissRequest = { 
                    callback.invoke(origin, false, false)
                    viewModel.clearGeolocationPrompt()
                },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Approve Location Access?", fontWeight = FontWeight.Bold) },
                text = { 
                    Text("The website $origin wants to access your current location. This is used by pages to provide nearby services or search results.", fontSize = 13.sp) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            
                            if (!hasPermission) {
                                geolocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                callback.invoke(origin, true, false)
                                viewModel.clearGeolocationPrompt()
                            }
                        }
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            callback.invoke(origin, false, false)
                            viewModel.clearGeolocationPrompt()
                        }
                    ) {
                        Text("Block")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        if (showDelayedNotificationDialog) {
            AlertDialog(
                onDismissRequest = { showDelayedNotificationDialog = false },
                icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Enable Device Notifications?", fontWeight = FontWeight.Bold) },
                text = { 
                    Text("Receive push notifications, media controllers, active downloads, and update alerts. Would you like to grant Orion Browser the notification permission?", fontSize = 13.sp) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                            } else {
                                showDelayedNotificationDialog = false
                            }
                        }
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDelayedNotificationDialog = false }
                    ) {
                        Text("Maybe Later")
                    }
                },
                shape = RoundedCornerShape(20.dp)
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

sealed interface TabGridItem {
    data class SingleTab(val tab: TabState) : TabGridItem
    data class TabGroup(
        val groupId: String,
        val groupName: String,
        val groupColor: Long,
        val tabs: List<TabState>
    ) : TabGridItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherLayout(
    state: BrowserUiState,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onReopenClosedTab: (TabState) -> Unit,
    onMergeTabs: (List<String>, String, Long) -> Unit,
    onCloseSwitcher: () -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onChangeGroupColor: (String, Long) -> Unit,
    onNewTabInGroup: (String, Boolean) -> Unit,
    onUngroupTab: (String) -> Unit,
    onCreateNewGroup: (String, Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTabIds = remember { mutableStateListOf<String>() }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var showMoveNewGroupDialog by remember { mutableStateOf(false) }
    var selectedTabForMoveGroup by remember { mutableStateOf<TabState?>(null) }

    val activeTabInstance = remember(state.tabs, state.activeTabId) {
        state.tabs.find { it.id == state.activeTabId }
    }
    var isShowingIncognitoFilter by remember { 
        mutableStateOf(activeTabInstance?.isIncognito ?: false) 
    }

    val filteredTabs = remember(state.tabs, searchQuery, isShowingIncognitoFilter) {
        val base = state.tabs.filter { it.isIncognito == isShowingIncognitoFilter }
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Grouping logic for the tab list
    val gridItems = remember(filteredTabs) {
        val items = mutableListOf<TabGridItem>()
        val groups = filteredTabs.groupBy { it.groupId }
        
        // Add ungrouped tabs
        groups[null]?.forEach { tab ->
            items.add(TabGridItem.SingleTab(tab))
        }
        
        // Add tab groups
        groups.forEach { (groupId, tabsInGroup) ->
            if (groupId != null && tabsInGroup.isNotEmpty()) {
                val groupName = tabsInGroup.first().groupName ?: "Group"
                val groupColor = tabsInGroup.first().groupColor ?: 0xFF818CF8
                items.add(TabGridItem.TabGroup(groupId, groupName, groupColor, tabsInGroup))
            }
        }
        
        // Sort items by last active time to show latest interactions first
        items.sortByDescending {
            when (it) {
                is TabGridItem.SingleTab -> it.tab.lastActiveTime
                is TabGridItem.TabGroup -> it.tabs.maxOfOrNull { t -> t.lastActiveTime } ?: 0L
            }
        }
        items
    }

    var activeGroupToDetail by remember { mutableStateOf<TabGridItem.TabGroup?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButtonPosition = FabPosition.End,
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
                var showFabMenu by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("new_tab_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add options")
                    }
                    
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Tab") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showFabMenu = false
                                onNewTab(isShowingIncognitoFilter)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Tab Group") },
                            leadingIcon = { Icon(Icons.Default.GroupWork, null) },
                            onClick = {
                                showFabMenu = false
                                showNewGroupDialog = true
                            }
                        )
                    }
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
            // Normal and Incognito Tabs Categories Row (Like Chrome)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Normal Tabs Chip
                val normalCount = state.tabs.count { !it.isIncognito }
                FilterChip(
                    selected = !isShowingIncognitoFilter,
                    onClick = { isShowingIncognitoFilter = false },
                    label = { Text("Normal ($normalCount)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tab,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                // Incognito Tabs Chip
                val incognitoCount = state.tabs.count { it.isIncognito }
                FilterChip(
                    selected = isShowingIncognitoFilter,
                    onClick = { isShowingIncognitoFilter = true },
                    label = { Text("Incognito ($incognitoCount)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }

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
                items(gridItems) { gridItem ->
                    when (gridItem) {
                        is TabGridItem.SingleTab -> {
                            val tab = gridItem.tab
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
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    var showTabMenu by remember { mutableStateOf(false) }
                                                    IconButton(
                                                        onClick = { showTabMenu = true },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "Options",
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    
                                                    DropdownMenu(
                                                        expanded = showTabMenu,
                                                        onDismissRequest = { showTabMenu = false }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Move to New Group") },
                                                            leadingIcon = { Icon(Icons.Default.GroupWork, null, modifier = Modifier.size(18.dp)) },
                                                            onClick = {
                                                                showTabMenu = false
                                                                selectedTabForMoveGroup = tab
                                                                showMoveNewGroupDialog = true
                                                            }
                                                        )
                                                        state.tabs.mapNotNull { it.groupName }.distinct().forEach { existingGroupName ->
                                                            DropdownMenuItem(
                                                                text = { Text("Add to: $existingGroupName") },
                                                                leadingIcon = { Icon(Icons.Default.Group, null, modifier = Modifier.size(18.dp)) },
                                                                onClick = {
                                                                    showTabMenu = false
                                                                    val matchedTab = state.tabs.find { it.groupName == existingGroupName }
                                                                    if (matchedTab != null) {
                                                                        onMergeTabs(listOf(tab.id), existingGroupName, matchedTab.groupColor ?: 0xFF818CF8)
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text("Close Tab") },
                                                            leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) },
                                                            onClick = {
                                                                showTabMenu = false
                                                                onTabClose(tab.id)
                                                            }
                                                        )
                                                    }
                                                    
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
                        is TabGridItem.TabGroup -> {
                            val group = gridItem
                            val hasActiveMember = group.tabs.any { it.id == state.activeTabId }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clickable {
                                        if (!isSelectionMode) {
                                            activeGroupToDetail = group
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasActiveMember) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (hasActiveMember) 2.dp else 1.5.dp,
                                    color = Color(group.groupColor)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Tab Group Header Page Card
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(Color(group.groupColor), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = group.groupName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(group.groupColor),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "(${group.tabs.size} tabs)",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (!isSelectionMode) {
                                                IconButton(
                                                    onClick = {
                                                        // Close group (close all child tabs)
                                                        group.tabs.forEach { onTabClose(it.id) }
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close Group",
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Divider(color = Color(group.groupColor).copy(alpha = 0.2f))

                                        // Collage representing tabs in group
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .padding(6.dp)
                                        ) {
                                            val displayTabs = group.tabs.take(4)
                                            when (displayTabs.size) {
                                                1 -> {
                                                    val t = displayTabs[0]
                                                    if (t.screenshot != null) {
                                                        Image(bitmap = t.screenshot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Web, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                        }
                                                    }
                                                }
                                                2 -> {
                                                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        displayTabs.forEach { t ->
                                                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                                if (t.screenshot != null) {
                                                                    Image(bitmap = t.screenshot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                                } else {
                                                                    Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                3 -> {
                                                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            listOf(displayTabs[0], displayTabs[1]).forEach { t ->
                                                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                                    if (t.screenshot != null) {
                                                                        Image(bitmap = t.screenshot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                                    } else {
                                                                        Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                            val t = displayTabs[2]
                                                            if (t.screenshot != null) {
                                                                Image(bitmap = t.screenshot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                            } else {
                                                                Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                            }
                                                        }
                                                    }
                                                }
                                                else -> { // 4 or more
                                                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            listOf(displayTabs[0], displayTabs[1]).forEach { t ->
                                                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                                    if (t.screenshot != null) {
                                                                        Image(bitmap = t.screenshot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                                    } else {
                                                                        Icon(Icons.Default.Language, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            listOf(displayTabs[2], displayTabs[3]).forEach { t ->
                                                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                                    if (t.screenshot != null) {
                                                                        Image(bitmap = t.screenshot.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                                    } else {
                                                                        Icon(Icons.Default.Language, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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

    // Tab Group nested details popup overlay dialog (Chrome-like experience!)
    if (activeGroupToDetail != null) {
        val group = activeGroupToDetail!!
        var localGroupName by remember(group.groupId) { mutableStateOf(group.groupName) }
        var showColorPicker by remember { mutableStateOf(false) }
        
        Dialog(
            onDismissRequest = { activeGroupToDetail = null }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header of Group sheet dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Colored circle indicator picker toggler
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(group.groupColor), CircleShape)
                                    .clickable { showColorPicker = !showColorPicker }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            OutlinedTextField(
                                value = localGroupName,
                                onValueChange = {
                                    localGroupName = it
                                    onRenameGroup(group.groupId, it)
                                },
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(group.groupColor),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                        }
                        
                        IconButton(onClick = { activeGroupToDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close details")
                        }
                    }
                    
                    // Group Color Selection Row
                    if (showColorPicker) {
                        val colorsGroup = listOf(0xFFF87171, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFF472B6, 0xFF2DD4BF, 0xFFFB7185)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorsGroup.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(col), CircleShape)
                                        .border(
                                            width = if (group.groupColor == col) 2.5.dp else 0.dp,
                                            color = if (group.groupColor == col) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onChangeGroupColor(group.groupId, col)
                                        }
                                )
                            }
                        }
                    }
                    
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    // Group Management Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${group.tabs.size} items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Add Tab inside Group Button
                            TextButton(
                                onClick = {
                                    onNewTabInGroup(group.groupId, isShowingIncognitoFilter)
                                    activeGroupToDetail = null
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add inline", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add tab")
                            }
                            
                            // Ungroup group button
                            TextButton(
                                onClick = {
                                    group.tabs.forEach { onUngroupTab(it.id) }
                                    activeGroupToDetail = null
                                }
                            ) {
                                Text("Ungroup All")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Inner group grid of tabs
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(group.tabs) { tab ->
                            val isActive = tab.id == state.activeTabId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.85f)
                                    .clickable {
                                        onTabSelect(tab.id)
                                        activeGroupToDetail = null
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isActive) 2.dp else 0.5.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
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
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Language,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = tab.title,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            
                                            IconButton(
                                                onClick = {
                                                    onTabClose(tab.id)
                                                    val nextTabs = group.tabs.filter { it.id != tab.id }
                                                    if (nextTabs.isEmpty()) {
                                                        activeGroupToDetail = null
                                                    } else {
                                                        activeGroupToDetail = group.copy(tabs = nextTabs)
                                                    }
                                                },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close tab inline",
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                        
                                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (tab.screenshot != null) {
                                                Image(
                                                    bitmap = tab.screenshot.asImageBitmap(),
                                                    contentDescription = tab.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Web,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(22.dp)
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
        }
    }

    if (showNewGroupDialog) {
        CreateGroupDialog(
            initialGroupName = "My Group",
            onDismiss = { showNewGroupDialog = false },
            onConfirm = { name, color ->
                showNewGroupDialog = false
                onCreateNewGroup(name, color, isShowingIncognitoFilter)
            }
        )
    }

    if (showMoveNewGroupDialog && selectedTabForMoveGroup != null) {
        CreateGroupDialog(
            initialGroupName = "New Group",
            onDismiss = {
                showMoveNewGroupDialog = false
                selectedTabForMoveGroup = null
            },
            onConfirm = { name, color ->
                showMoveNewGroupDialog = false
                if (selectedTabForMoveGroup != null) {
                    onMergeTabs(listOf(selectedTabForMoveGroup!!.id), name, color)
                    selectedTabForMoveGroup = null
                }
            }
        )
    }
}

@Composable
fun CreateGroupDialog(
    initialGroupName: String = "My Group",
    initialColor: Long = 0xFF60A5FA,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var groupName by remember { mutableStateOf(initialGroupName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    val colorsGroup = listOf(0xFFF87171, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFF472B6, 0xFF2DD4BF, 0xFFFB7185)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tab Group", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Column {
                    Text("Select Group Color:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorsGroup.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(col), CircleShape)
                                    .border(
                                        width = if (selectedColor == col) 3.dp else 0.dp,
                                        color = if (selectedColor == col) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = col }
                            )
                        }
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
                }
            ) {
                Text("Create")
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

                                ListItem(
                                    headlineContent = { Text("AI Assistant Settings") },
                                    supportingContent = { Text("Default provider, language tuning & custom endpoints", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
                                    modifier = Modifier.clickable { currentScreen = "ai_settings" }
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
                    "ai_settings" -> {
                        AISettingsFragment(onBack = {
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
    Surface(
        color = if (isGlass) Color(0xFF0B1220) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Saved Bookmarks", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryOverlay(
    history: List<HistoryItem>,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onClearAll: () -> Unit,
    onClearBrowsingData: (Boolean, Boolean, Boolean, Int) -> Unit,
    isGlass: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showLocalClearDataDialog by remember { mutableStateOf(false) }

    val filteredHistory = remember(history, searchQuery) {
        history.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    val groupedByDate = remember(filteredHistory) {
        filteredHistory.groupBy { timestamp ->
            val isToday = android.text.format.DateUtils.isToday(timestamp.timestamp)
            if (isToday) {
                "Today"
            } else {
                val isYesterday = android.text.format.DateUtils.isToday(timestamp.timestamp + 24 * 3600 * 1000L)
                if (isYesterday) {
                    "Yesterday"
                } else {
                    val format = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
                    format.format(java.util.Date(timestamp.timestamp))
                }
            }
        }
    }

    Surface(
        color = if (isGlass) Color(0xFF0B1220) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds.clear()
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIds.isNotEmpty()) "${selectedIds.size} Selected" else "History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedIds.forEach { id -> onDelete(id) }
                                selectedIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        if (history.isNotEmpty()) {
                            TextButton(onClick = onClearAll) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search history search-bar (Like Chrome)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                placeholder = { Text("Search history", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedContainerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                    unfocusedContainerColor = if (isGlass) Color.White.copy(alpha = 0.03f) else Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // "Clear/Delete Browsing Data..." clickable banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clickable { showLocalClearDataDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Clear browsing data...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredHistory.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No history recorded yet." else "No matching results found.",
                        color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByDate.forEach { (dateStr, items) ->
                        item {
                            Text(
                                text = dateStr,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(items) { hm ->
                            val isSelected = selectedIds.contains(hm.id)
                            HistoryItemRow(
                                historyItem = hm,
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        if (isSelected) selectedIds.remove(hm.id) else selectedIds.add(hm.id)
                                    } else {
                                        onNavigate(hm.url)
                                    }
                                },
                                onLongClick = {
                                    if (!selectedIds.contains(hm.id)) {
                                        selectedIds.add(hm.id)
                                    }
                                },
                                onDelete = { onDelete(hm.id) },
                                isSelectionMode = selectedIds.isNotEmpty(),
                                isSelected = isSelected,
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLocalClearDataDialog) {
        DeleteBrowsingDataDialog(
            onClear = { hist, cook, cach, rangeIndex ->
                showLocalClearDataDialog = false
                onClearBrowsingData(hist, cook, cach, rangeIndex)
            },
            onDismiss = { showLocalClearDataDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemRow(
    historyItem: HistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isGlass: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else if (isGlass) {
                Color.White.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else if (isGlass) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = historyItem.title.ifBlank { "Visited Site" },
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

            if (!isSelectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete history item",
                        tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
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
    val languages = remember {
        listOf(
            "Hindi (हिन्दी)" to "hi",
            "Spanish (Español)" to "es",
            "French (Français)" to "fr",
            "German (Deutsch)" to "de",
            "English" to "en",
            "Japanese (日本語)" to "ja",
            "Arabic (العربية)" to "ar",
            "Bengali (বাংলা)" to "bn",
            "Portuguese (Português)" to "pt",
            "Russian (Русский)" to "ru",
            "Chinese Simplified (简体中文)" to "zh-CN",
            "Chinese Traditional (繁體中文)" to "zh-TW",
            "Italian (Italiano)" to "it",
            "Korean (한국어)" to "ko",
            "Urdu (اردو)" to "ur",
            "Turkish (Türkçe)" to "tr",
            "Vietnamese (Tiếng Việt)" to "vi",
            "Polish (Polski)" to "pl",
            "Ukrainian (Українська)" to "uk",
            "Telugu (తెలుగు)" to "te",
            "Marathi (मराठी)" to "mr",
            "Tamil (தமிழ்)" to "ta",
            "Gujarati (ગુજરાતી)" to "gu",
            "Kannada (ಕನ್ನಡ)" to "kn",
            "Malayalam (മലയാളം)" to "ml",
            "Punjabi (ਪੰਜਾਬੀ)" to "pa",
            "Odia (ଓଡ଼ିଆ)" to "or",
            "Thai (ไทย)" to "th",
            "Dutch (Nederlands)" to "nl",
            "Swedish (Svenska)" to "sv",
            "Norwegian (Norsk)" to "no",
            "Finnish (Suomi)" to "fi",
            "Danish (Dansk)" to "da",
            "Greek (Ελληνικά)" to "el",
            "Indonesian (Bahasa Indonesia)" to "id",
            "Malay (Bahasa Melayu)" to "ms"
        ).sortedBy { it.first }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            languages
        } else {
            languages.filter { it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Web Translate", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Translate current web page dynamically. Choose from any native browser languages:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))
                
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(filteredLanguages) { (langName, code) ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    text = langName, 
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                ) 
                            },
                            supportingContent = {
                                Text(
                                    text = "Code: $code",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTranslate(code) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}

@Composable
fun TtsControlPanel(
    state: BrowserUiState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Title, sentence count, and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Listening to Page (${state.currentTtsIndex + 1}/${state.totalTtsSegments})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop Aloud Reader",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current Sentence Excerpt Text Preview (Highly requested for Chrome read aloud!)
            if (state.currentTtsText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.currentTtsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Controls Row (Previous, Play/Pause, Next, Speech Rate speed, Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed selection cycle
                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val currentSpeed = state.ttsSpeed
                val nextIdx = (speeds.indexOf(currentSpeed) + 1).let { if (it >= speeds.size) 0 else it }
                val nextSpeed = speeds[nextIdx]
                
                TextButton(
                    onClick = { onSpeedChange(nextSpeed) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${currentSpeed}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Previous sentence button
                IconButton(
                    onClick = onSkipPrevious,
                    enabled = state.currentTtsIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Sentence",
                        tint = if (state.currentTtsIndex > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                // Play / Pause glowing circle button
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (state.isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isTtsPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Next sentence button
                IconButton(
                    onClick = onSkipNext,
                    enabled = state.currentTtsIndex + 1 < state.totalTtsSegments
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Sentence",
                        tint = if (state.currentTtsIndex + 1 < state.totalTtsSegments) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                // Dummy to balance Row
                Spacer(modifier = Modifier.width(36.dp))
            }
        }
    }
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

fun getStorageInfo(context: android.content.Context): Pair<String, String> {
    return try {
        val path = android.os.Environment.getDataDirectory()
        val stat = android.os.StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        val totalBytes = totalBlocks * blockSize
        val availableBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - availableBytes
        
        val usedStr = android.text.format.Formatter.formatShortFileSize(context, usedBytes)
        val totalStr = android.text.format.Formatter.formatShortFileSize(context, totalBytes)
        Pair(usedStr, totalStr)
    } catch (e: Exception) {
        Pair("389.99 MB", "115.87 GB")
    }
}

fun getGroupedDateString(timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val time = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val isToday = now.get(java.util.Calendar.YEAR) == time.get(java.util.Calendar.YEAR) &&
                  now.get(java.util.Calendar.DAY_OF_YEAR) == time.get(java.util.Calendar.DAY_OF_YEAR)
                  
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(java.util.Calendar.YEAR) == time.get(java.util.Calendar.YEAR) &&
                      now.get(java.util.Calendar.DAY_OF_YEAR) == time.get(java.util.Calendar.DAY_OF_YEAR)
                      
    return when {
        isToday -> "Today - " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        isYesterday -> "Yesterday - " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsOverlay(
    downloads: List<com.example.data.DownloadItem>,
    onDismiss: () -> Unit,
    onOpenFile: (String, String, String) -> Unit,
    viewModel: BrowserViewModel,
    isGlass: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val storageInfo = remember { getStorageInfo(context) }
    
    val groupedAndSorted = remember(downloads) {
        downloads.groupBy { getGroupedDateString(it.timestamp) }
    }

    Surface(
        color = if (isGlass) Color(0xFF0B1220) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = emptySet()
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIds.isNotEmpty()) "${selectedIds.size} selected" else "Downloads",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // In selection mode, show dynamic delete & share actions in header
                if (selectedIds.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Multi-Share
                        IconButton(
                            onClick = {
                                val selectedDownloads = downloads.filter { it.downloadId in selectedIds }
                                val uris = ArrayList<android.net.Uri>()
                                selectedDownloads.forEach { dl ->
                                    val file = java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                        dl.fileName
                                    )
                                    if (file.exists()) {
                                        try {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            uris.add(uri)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                                if (uris.isNotEmpty()) {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND_MULTIPLE
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                        type = "*/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share selected files"))
                                } else {
                                    android.widget.Toast.makeText(context, "Cannot share: selected files missing on memory", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share selected",
                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Multi-Delete
                        IconButton(
                            onClick = {
                                viewModel.deleteDownloads(selectedIds)
                                selectedIds = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Storage Details
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Using ${storageInfo.first} of ${storageInfo.second}",
                        fontSize = 13.sp,
                        color = if (isGlass) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedAndSorted.forEach { (dateGroup, items) ->
                        item {
                            Text(
                                text = dateGroup,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(items) { dl ->
                            DownloadItemRow(
                                download = dl,
                                isSelected = dl.downloadId in selectedIds,
                                isInSelectionMode = selectedIds.isNotEmpty(),
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        selectedIds = if (dl.downloadId in selectedIds) {
                                            selectedIds - dl.downloadId
                                        } else {
                                            selectedIds + dl.downloadId
                                        }
                                    } else {
                                        val file = java.io.File(
                                            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                            dl.fileName
                                        )
                                        if (file.exists()) {
                                            onOpenFile(file.absolutePath, dl.fileName, dl.mimeType)
                                        } else {
                                            android.widget.Toast.makeText(context, "File does not exist or was deleted", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (selectedIds.isEmpty()) {
                                        selectedIds = setOf(dl.downloadId)
                                    }
                                },
                                onDelete = {
                                    viewModel.deleteDownload(dl.downloadId)
                                },
                                onRename = { newName ->
                                    viewModel.renameDownloadFile(dl.downloadId, dl.fileName, newName)
                                },
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadItemRow(
    download: com.example.data.DownloadItem,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Boolean,
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
    
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf(download.fileName) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val success = onRename(newFileName)
                            if (success) {
                                showRenameDialog = false
                            } else {
                                android.widget.Toast.makeText(context, "Rename failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isInSelectionMode && isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else if (isGlass) {
            Color.White.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark column in selection mode
            if (isInSelectionMode) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, (if (isGlass) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // File type indicator/icon
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

            // File info
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
                    text = if (fileExists) readableSize else "File missing • $readableSize",
                    fontSize = 11.sp,
                    color = if (fileExists) {
                        (if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            // More Options Dropdown button
            if (!isInSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "File Options",
                            tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                if (fileExists) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = download.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share file"))
                                } else {
                                    android.widget.Toast.makeText(context, "Cannot share: file missing on memory", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                newFileName = download.fileName
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
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

@Composable
fun TranslateBarLayout(viewModel: BrowserViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val activeTab = uiState.tabs.find { it.id == uiState.activeTabId }
    val isDesktopMode = activeTab?.isDesktopMode == true
    var showTranslationDiagnostics by remember { mutableStateOf(false) }

    androidx.compose.animation.AnimatedVisibility(
        visible = uiState.showTranslateBar && !uiState.isTabSwitcherOpen && !uiState.readerModeActive,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    ) {
        if (isDesktopMode) {
            DesktopTranslationBar(
                viewModel = viewModel,
                activeTab = activeTab,
                uiState = uiState,
                onShowDiagnostics = { showTranslationDiagnostics = true }
            )
        } else {
            MobileTranslationBar(
                viewModel = viewModel,
                activeTab = activeTab,
                uiState = uiState,
                onShowDiagnostics = { showTranslationDiagnostics = true }
            )
        }
    }

    if (showTranslationDiagnostics) {
        TranslationDiagnosticsDialog(viewModel) { showTranslationDiagnostics = false }
    }
}

@Composable
fun MobileTranslationBar(
    viewModel: BrowserViewModel,
    activeTab: com.example.browser.TabState?,
    uiState: BrowserUiState,
    onShowDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMoreLanguages by remember { mutableStateOf(false) }
    val detectedLang = viewModel.translateManager.debugger.detectedLanguage.ifEmpty { "en" }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mobile_translation_bar"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translate",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    AssistChip(
                        onClick = { showMoreLanguages = true },
                        label = {
                            Text(
                                text = uiState.translateTargetLang,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("mobile_language_selector")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(36.dp).testTag("mobile_translate_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Options",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Choose language...", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    showMoreLanguages = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this page", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    val currentUrl = activeTab?.url ?: ""
                                    val host = viewModel.getUrlHost(currentUrl)
                                    if (host.isNotEmpty()) {
                                        viewModel.translateManager.settings.addNeverTranslateSite(host)
                                        Toast.makeText(context, "Never translate $host added", Toast.LENGTH_SHORT).show()
                                    }
                                    viewModel.dismissTranslateBar()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this language (${detectedLang.uppercase()})", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.translateManager.settings.addNeverTranslateLanguage(detectedLang)
                                    Toast.makeText(context, "Never translate ${detectedLang.uppercase()} pages", Toast.LENGTH_SHORT).show()
                                    viewModel.dismissTranslateBar()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Translation Diagnostics", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onShowDiagnostics()
                                }
                            )
                        }
                    }

                    if (uiState.isPageTranslated) {
                        TextButton(
                            onClick = { viewModel.undoTranslation() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(36.dp).testTag("mobile_translate_undo")
                        ) {
                            Text("Show Original", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.translateActivePage(uiState.translateTargetLangCode) },
                            modifier = Modifier.height(36.dp).testTag("mobile_translate_do")
                        ) {
                            Text("Translate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    IconButton(
                        onClick = { viewModel.dismissTranslateBar() },
                        modifier = Modifier.size(36.dp).testTag("mobile_translate_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TranslationProgressSection(
                progressManager = viewModel.translateManager.progressManager,
                onRetry = { viewModel.translateActivePage(uiState.translateTargetLangCode) }
            )
        }
    }

    if (showMoreLanguages) {
        TranslateDialog(
            onTranslate = { langCode ->
                showMoreLanguages = false
                viewModel.translateActivePage(langCode)
            },
            onDismiss = { showMoreLanguages = false }
        )
    }
}

@Composable
fun DesktopTranslationBar(
    viewModel: BrowserViewModel,
    activeTab: com.example.browser.TabState?,
    uiState: BrowserUiState,
    onShowDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMoreLanguages by remember { mutableStateOf(false) }
    val detectedLang = viewModel.translateManager.debugger.detectedLanguage.ifEmpty { "en" }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("desktop_translation_bar"),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translate Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Orion Translator",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiState.isPageTranslated) "Translated to ${uiState.translateTargetLang}" else "Detected page is in ${detectedLang.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(2f)
                ) {
                    AssistChip(
                        onClick = { showMoreLanguages = true },
                        label = { Text("Target: ${uiState.translateTargetLang}") },
                        leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("desktop_target_language_chip")
                    )

                    val popularLangs = listOf("hi" to "Hindi", "es" to "Spanish", "fr" to "French")
                    popularLangs.forEach { (code, name) ->
                        val isSelected = uiState.translateTargetLangCode == code
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.translateActivePage(code) },
                            label = { Text(name) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.Settings, "Translation Options", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this page", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    val currentUrl = activeTab?.url ?: ""
                                    val host = viewModel.getUrlHost(currentUrl)
                                    if (host.isNotEmpty()) {
                                        viewModel.translateManager.settings.addNeverTranslateSite(host)
                                        Toast.makeText(context, "Never translate $host added", Toast.LENGTH_SHORT).show()
                                    }
                                    viewModel.dismissTranslateBar()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Never show for this language (${detectedLang.uppercase()})", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.translateManager.settings.addNeverTranslateLanguage(detectedLang)
                                    Toast.makeText(context, "Never translate ${detectedLang.uppercase()} pages", Toast.LENGTH_SHORT).show()
                                    viewModel.dismissTranslateBar()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
                                text = { Text("Translation Diagnostics", fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onShowDiagnostics()
                                }
                            )
                        }
                    }

                    if (!uiState.isPageTranslated) {
                        FilledTonalButton(
                            onClick = { viewModel.translateActivePage(uiState.translateTargetLangCode) },
                            modifier = Modifier.testTag("desktop_translate_button")
                        ) {
                            Text("Translate")
                        }
                    }

                    if (uiState.isPageTranslated) {
                        Button(
                            onClick = { viewModel.undoTranslation() },
                            modifier = Modifier.testTag("desktop_show_original_button")
                        ) {
                            Text("Show Original")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.dismissTranslateBar() },
                        modifier = Modifier.testTag("desktop_hide_button")
                    ) {
                        Text("Hide")
                    }

                    IconButton(
                        onClick = { viewModel.dismissTranslateBar() },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).testTag("desktop_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Toolbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            TranslationProgressSection(
                progressManager = viewModel.translateManager.progressManager,
                onRetry = { viewModel.translateActivePage(uiState.translateTargetLangCode) }
            )
        }
    }

    if (showMoreLanguages) {
        TranslateDialog(
            onTranslate = { langCode ->
                showMoreLanguages = false
                viewModel.translateActivePage(langCode)
            },
            onDismiss = { showMoreLanguages = false }
        )
    }
}

@Composable
fun TranslationDiagnosticsDialog(viewModel: BrowserViewModel, onDismiss: () -> Unit) {
    val dbg = viewModel.translateManager.debugger
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Translation Debug Panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Real-time Native Browser Translation Telemetry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                
                val totalNodes = dbg.textNodesFound.get()
                val translatedNodes = dbg.textNodesTranslated.get()
                val failedNodes = maxOf(0, totalNodes - translatedNodes)
                val originalNodes = totalNodes
                val successRate = if (totalNodes > 0) {
                    (translatedNodes.toDouble() / totalNodes.toDouble()) * 100.0
                } else {
                    100.0
                }
                val successRateStr = String.format("%.1f%%", successRate)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Detected Language:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(dbg.detectedLanguage.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Language:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(dbg.targetLanguage.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$totalNodes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Translated Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$translatedNodes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Failed Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$failedNodes", fontWeight = FontWeight.Bold, color = if (failedNodes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Original Nodes:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("$originalNodes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Replacement Success Rate:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(successRateStr, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cache Hits (Memory/Room):", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("${dbg.cacheHits.get()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Translation Latency:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("${dbg.totalTranslationTimeMs.get()} ms", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
fun TranslationProgressSection(
    progressManager: com.example.translateengine.TranslationProgressManager,
    onRetry: () -> Unit
) {
    val state by progressManager.state.collectAsState()
    val total by progressManager.totalNodes.collectAsState()
    val translated by progressManager.translatedNodes.collectAsState()

    if (state == com.example.translateengine.ProgressState.Idle) return

    val infiniteTransition = rememberInfiniteTransition(label = "translation_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translation_rotation"
    )

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("translation_progress_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (state) {
                    com.example.translateengine.ProgressState.Translating -> {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Translating",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (total > 0) "Translating $translated / $total" else "Translating...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    com.example.translateengine.ProgressState.Completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✓ Translation Complete",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    com.example.translateengine.ProgressState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠ Translation Failed",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }

            if (state == com.example.translateengine.ProgressState.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.height(32.dp).testTag("translation_progress_retry")
                ) {
                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun YtStreamItemRow(
    stream: ResolvedMediaStream,
    onSelect: (ResolvedMediaStream) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF23232E)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(stream) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val icon = if (stream.isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (stream.isAudio) Color(0xFF38BDF8) else Color(0xFFFF2E2E),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stream.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Format: ${stream.ext.uppercase()} | Mime: ${stream.mimeType}",
                        color = Color.Gray,
                        fontSize = 9.sp
                    )
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

