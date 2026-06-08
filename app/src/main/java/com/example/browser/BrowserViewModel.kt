package com.example.browser

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.webkit.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

enum class SuggestionType {
    SEARCH, HISTORY, BOOKMARK
}

data class SearchSuggestion(
    val type: SuggestionType,
    val title: String,
    val url: String = ""
)

data class TabState(
    val id: String,
    val url: String = "orion://newtab",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis(),
    val screenshot: Bitmap? = null,
    val faviconUrl: String? = null,
    val favicon: Bitmap? = null,
    val isWebViewDestroyed: Boolean = false,
    val readerModeAvailable: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isIncognito: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupColor: Long? = null,
    val blockedAdsCount: Int = 0,
    val hasLoadedSuccessfully: Boolean = false,
    val parentTabId: String? = null
)

data class BrowserUiState(
    val tabs: List<TabState> = emptyList(),
    val activeTabId: String = "",
    val isJavaScriptEnabled: Boolean = true,
    val isHardwareAccelerationEnabled: Boolean = true,
    val newTabWallpaper: String = "Frosted Glass",
    val findInPageActive: Boolean = false,
    val findInPageQuery: String = "",
    val findInPageCurrentMatch: Int = 0,
    val findInPageTotalMatches: Int = 0,
    val readerModeActive: Boolean = false,
    val readerModeTitle: String = "",
    val readerModeAuthor: String? = null,
    val readerModeContent: String = "",
    val readerModeDate: String? = null,
    val readerModeDomain: String? = null,
    val readerFontSize: Int = 16,
    val isSettingsOpen: Boolean = false,
    val isHistoryOpen: Boolean = false,
    val isBookmarksOpen: Boolean = false,
    val isTabSwitcherOpen: Boolean = false,
    val isDownloadsOpen: Boolean = false,
    val isSearchFocused: Boolean = false,
    val currentInputUrl: String = "",
    val feedCategory: String = "For You",
    val articles: List<com.example.data.ArticleCacheEntity> = emptyList(),
    val isFeedLoading: Boolean = false,
    
    // Ad Blocking properties
    val globalAdBlockEnabled: Boolean = true,
    val globalTrackersEnabled: Boolean = true,
    val youtubeAdSkipEnabled: Boolean = true,
    val adblockWhitelist: Set<String> = emptySet(),
    val adblockBlacklist: Set<String> = emptySet(),
    
    // Text To Speech (Listen to page) properties
    val isTtsActive: Boolean = false,
    val isTtsPlaying: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    val currentTtsText: String = "",
    val currentTtsIndex: Int = 0,
    val totalTtsSegments: Int = 0,
    
    // Recently closed tabs tracking
    val recentlyClosedTabs: List<TabState> = emptyList(),
    
    // Address Bar & Home Shortcut settings states
    val addressBarPosition: String = "top",
    val showHomeButton: Boolean = true,

    // Download states
    val downloadConfirmState: DownloadConfirmState = DownloadConfirmState(),
    val downloadProgressState: DownloadProgressState = DownloadProgressState(),

    // Long-press Context Menu state
    val contextMenuState: ContextMenuState = ContextMenuState(),

    // Search suggestions dropdown list
    val searchSuggestions: List<SearchSuggestion> = emptyList(),

    // File picker trigger
    val showFilePicker: Boolean = false,
    
    // Fullscreen auto-hide toolbars visible state
    val areToolbarsVisible: Boolean = true,
    
    // Active local file tracker (for Video, Music, PDF, Zip viewer)
    val activeViewerFile: ActiveViewerFile? = null,

    // Google Translate State
    val showTranslateBar: Boolean = false,
    val isPageTranslated: Boolean = false,
    val translateTargetLang: String = "Hindi",
    val translateTargetLangCode: String = "hi",
    val originalTranslationUrl: String = "",
    val autoTranslateEnabled: Boolean = false
)

data class ActiveViewerFile(
    val filePath: String,
    val fileName: String,
    val mimeType: String
)

data class ContextMenuState(
    val show: Boolean = false,
    val url: String = "",
    val isImage: Boolean = false,
    val isImageLink: Boolean = false,
    val imageUrl: String = ""
)

data class DownloadConfirmState(
    val show: Boolean = false,
    val url: String = "",
    val fileName: String = "",
    val contentLength: Long = 0L,
    val mimeType: String = "",
    val userAgent: String = ""
)

data class DownloadProgressState(
    val showProgress: Boolean = false,
    val fileName: String = "",
    val progress: Int = 0,
    val downloadId: Long = 0L,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val mimeType: String = ""
)

class BrowserViewModel(
    application: Application,
    private val repository: BrowserRepository,
    val prefs: PreferenceManager
) : AndroidViewModel(application), com.example.extensionengine.BrowserDelegate {

    val extensionManager = com.example.extensionengine.ExtensionManager(application, this)
    val permissionEngine: com.example.permissionengine.PermissionEngine = com.example.permissionengine.PermissionEngineImpl(application)
    val translateManager = com.example.translateengine.TranslateManager(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    // Notification permission request variables
    private val _notificationRequestOrigin = MutableStateFlow<String?>(null)
    val notificationRequestOrigin: StateFlow<String?> = _notificationRequestOrigin

    // Web Permission properties for camera/mic
    private val _pendingPermissionRequest = MutableStateFlow<android.webkit.PermissionRequest?>(null)
    val pendingPermissionRequest: StateFlow<android.webkit.PermissionRequest?> = _pendingPermissionRequest.asStateFlow()

    // Web Geolocation properties
    private val _pendingGeolocationPrompt = MutableStateFlow<Pair<String, android.webkit.GeolocationPermissions.Callback>?>(null)
    val pendingGeolocationPrompt: StateFlow<Pair<String, android.webkit.GeolocationPermissions.Callback>?> = _pendingGeolocationPrompt.asStateFlow()

    fun setPendingPermissionRequest(request: android.webkit.PermissionRequest?) {
        _pendingPermissionRequest.value = request
    }

    fun clearPermissionRequest() {
        _pendingPermissionRequest.value = null
    }

    fun setPendingGeolocationPrompt(prompt: Pair<String, android.webkit.GeolocationPermissions.Callback>?) {
        _pendingGeolocationPrompt.value = prompt
    }

    fun clearGeolocationPrompt() {
        _pendingGeolocationPrompt.value = null
    }

    // Map of active WebViews
    private val webViewMap = mutableMapOf<String, WebView>()

    // Desktop Mode variables
    private var isUASwitchPending = false
    private var lastManualLoadUrl = ""
    private val domainDesktopMap = HashMap<String, Boolean>()

    // Search suggestions variables
    private var suggestionFetchJob: kotlinx.coroutines.Job? = null
    private val suggestionMemoryCache = object : java.util.LinkedHashMap<String, List<SearchSuggestion>>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchSuggestion>>?): Boolean {
            return size > 10
        }
    }

    // Favicon LRU cache (max 50 entries)
    private val faviconCache = LruCache<String, Bitmap>(50)

    // OkHttp 50MB disk cache
    private val okHttpClient: OkHttpClient

    private val tabHistory = mutableListOf<String>()

    // File choosing callback
    var fileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    // Back press twice to exit
    private var lastBackPressTime = 0L

    // Fullscreen state variables
    private val _fullscreenState = MutableStateFlow<FullscreenState?>(null)
    val fullscreenState: StateFlow<FullscreenState?> = _fullscreenState

    data class FullscreenState(
        val view: android.view.View,
        val callback: android.webkit.WebChromeClient.CustomViewCallback
    )

    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSites: StateFlow<List<TopSite>> = repository.getMergedTopSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var ttsEngine: android.speech.tts.TextToSpeech? = null
    private var ttsSegments = listOf<String>()
    private var currentTtsSegmentIndex = 0
    private val sslDomainsToIgnore = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        // Load saved desktop mode entries
        try {
            val sharedPrefs = application.getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
            sharedPrefs.all.forEach { (key, value) ->
                if (key.startsWith("desktop_mode_") && value is Boolean) {
                    val domain = key.substring("desktop_mode_".length)
                    domainDesktopMap[domain] = value
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize AdBlocker
        com.example.adblockengine.AdBlocker.init(application)
        
        // OkHttp Cache Setup (50MB)
        val cacheSize = 50 * 1024 * 1024L
        val cacheDirectory = File(application.cacheDir, "http_cache_orion")
        val httpCache = Cache(cacheDirectory, cacheSize)
        okHttpClient = OkHttpClient.Builder()
            .cache(httpCache)
            .build()

        _uiState.update {
            it.copy(
                globalAdBlockEnabled = com.example.adblockengine.AdBlocker.globalAdBlockEnabled,
                globalTrackersEnabled = com.example.adblockengine.AdBlocker.globalTrackersEnabled,
                youtubeAdSkipEnabled = com.example.adblockengine.AdBlocker.youtubeAdSkipEnabled,
                adblockWhitelist = com.example.adblockengine.AdBlocker.whitelistedSites.toSet(),
                adblockBlacklist = com.example.adblockengine.AdBlocker.blockedSites.toSet()
            )
        }

        try {
            ttsEngine = android.speech.tts.TextToSpeech(application) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    ttsEngine?.language = java.util.Locale.getDefault()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sync settings from PreferenceManager
        viewModelScope.launch {
            combine(
                prefs.isJavaScriptEnabled,
                prefs.isHardwareAccelerationEnabled,
                prefs.newTabWallpaper,
                prefs.readerFontSize
            ) { js, hw, bg, size ->
                _uiState.update {
                    it.copy(
                        isJavaScriptEnabled = js,
                        isHardwareAccelerationEnabled = hw,
                        newTabWallpaper = bg,
                        readerFontSize = size
                    )
                }
                // Apply update to active WebViews
                _uiState.value.tabs.forEach { tab ->
                    webViewMap[tab.id]?.let { webView ->
                        applyWebViewSettings(webView, js, hw, tab.isDesktopMode)
                    }
                }
            }.collect()
        }

        // Add initial tab or restore previous tabs
        restoreTabsState()
        loadArticlesForCategory("For You", false)
        refreshSettings()
    }

    fun refreshSettings() {
        val position = prefs.getString("address_bar_position", "top")
        val showHome = prefs.getBoolean("show_home_button", true)
        _uiState.update {
            it.copy(
                addressBarPosition = position,
                showHomeButton = showHome
            )
        }
    }

    // Tab Management
    fun addNewTab(url: String = "orion://newtab", isIncognito: Boolean = false, parentTabId: String? = null) {
        val tabId = UUID.randomUUID().toString()
        val formatted = if (url == "orion://newtab" && isIncognito) "orion://newtab-incognito" else formatUrlOrSearch(url)
        val newTab = TabState(
            id = tabId,
            url = formatted,
            title = if (isIncognito) "Incognito Tab" else if (formatted == "orion://newtab") "New Tab" else "Loading...",
            lastActiveTime = System.currentTimeMillis(),
            isIncognito = isIncognito,
            parentTabId = parentTabId
        )

        _uiState.update { state ->
            val updatedTabs = state.tabs + newTab
            state.copy(
                tabs = updatedTabs,
                activeTabId = tabId,
                currentInputUrl = if (formatted == "orion://newtab" || formatted == "orion://newtab-incognito") "" else formatted,
                isTabSwitcherOpen = false,
                readerModeActive = false,
                areToolbarsVisible = true
            )
        }

        // Lazy preloading of adjacent tabs
        cleanupOlderWebViews()
        preloadAdjacentTabs()
        saveTabsState()
    }

    fun selectTab(tabId: String) {
        val currentState = _uiState.value
        val oldTabId = currentState.activeTabId
        if (oldTabId == tabId) {
            _uiState.update { state ->
                state.copy(
                    isTabSwitcherOpen = false,
                    readerModeActive = false,
                    findInPageActive = false,
                    areToolbarsVisible = true
                )
            }
            return
        }

        tabHistory.remove(tabId)
        if (currentState.tabs.any { it.id == oldTabId }) {
            tabHistory.add(oldTabId)
        }

        // 1. Select new tab IMMEDIATELY in state
        _uiState.update { state ->
            val updatedTabs = state.tabs.map {
                if (it.id == tabId) it.copy(lastActiveTime = System.currentTimeMillis()) else it
            }
            val activeTab = updatedTabs.find { it.id == tabId }
            state.copy(
                tabs = updatedTabs,
                activeTabId = tabId,
                currentInputUrl = if (activeTab?.url == "orion://newtab") "" else (activeTab?.url ?: ""),
                isTabSwitcherOpen = false,
                readerModeActive = false,
                findInPageActive = false,
                areToolbarsVisible = true,
                showTranslateBar = false,
                isPageTranslated = false
            )
        }
        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Hidden)

        try {
            val activeParams = org.json.JSONObject().apply {
                put("activeInfo", org.json.JSONObject().apply {
                    put("tabId", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                    put("windowId", 1)
                })
            }
            extensionManager.engine.eventManager.triggerEvent("tabs.onActivated", activeParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Resume the newly active tab WebView IMMEDIATELY
        val activeWebView = webViewMap[tabId]
        activeWebView?.onResume()

        // 3. Capture screen of previous active WebView and pause asynchronously
        val prevWebView = webViewMap[oldTabId]
        if (prevWebView != null) {
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    val bmp = captureWebViewScreenshot(prevWebView)
                    _uiState.update { state ->
                        state.copy(
                            tabs = state.tabs.map {
                                if (it.id == oldTabId) it.copy(screenshot = bmp ?: it.screenshot) else it
                            }
                        )
                    }
                    prevWebView.onPause()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        preloadAdjacentTabs()
    }

    fun closeTab(tabId: String) {
        tabHistory.remove(tabId)
        val currentState = _uiState.value
        val tabs = currentState.tabs
        if (tabs.size <= 1) {
            // If it's the last tab, close then reopen an empty new tab
            destroyTabWebView(tabId)
            _uiState.update { state ->
                state.copy(tabs = emptyList(), activeTabId = "")
            }
            addNewTab()
            return
        }

        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        val updatedTabs = tabs.filter { it.id != tabId }
        val newSelection = if (currentState.activeTabId == tabId) {
            val nextIndex = if (tabIndex < updatedTabs.size) tabIndex else updatedTabs.size - 1
            updatedTabs[nextIndex].id
        } else {
            currentState.activeTabId
        }

        destroyTabWebView(tabId)

        _uiState.update { state ->
            val activeTab = updatedTabs.find { it.id == newSelection }
            state.copy(
                tabs = updatedTabs,
                activeTabId = newSelection,
                currentInputUrl = if (activeTab?.url == "orion://newtab") "" else (activeTab?.url ?: "")
            )
        }

        val activeWebView = webViewMap[newSelection]
        activeWebView?.onResume()

        preloadAdjacentTabs()
        saveTabsState()
    }

    private fun destroyTabWebView(tabId: String) {
        val webView = webViewMap.remove(tabId)
        webView?.let {
            it.stopLoading()
            it.clearHistory()
            it.removeAllViews()
            it.destroy()
        }
    }

    fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        val context = getApplication<Application>()

        val notificationUrl = intent.getStringExtra("NOTIFICATION_URL")
        if (!notificationUrl.isNullOrEmpty()) {
            addNewTab(url = notificationUrl)
            return
        }

        if (action == android.content.Intent.ACTION_SEND && type == "text/plain") {
            val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
            // Extract HTTP/HTTPS link
            val urlRegex = Regex("https?://[a-zA-Z0-9.\\-_/\\?&+=~%]+")
            val match = urlRegex.find(sharedText)
            val extractedUrl = match?.value ?: ""
            if (extractedUrl.isNotEmpty()) {
                addNewTab(url = extractedUrl)
            }
            return
        }

        if (action == android.content.Intent.ACTION_VIEW) {
            val dataUri = intent.data
            if (dataUri != null) {
                var mime = type ?: context.contentResolver.getType(dataUri) ?: ""
                var name = "External File"
                
                // Query name from ContentResolver
                try {
                    context.contentResolver.query(dataUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Deduce mime type from file extension if empty
                if (mime.isEmpty()) {
                    val ext = name.substringAfterLast(".").lowercase()
                    mime = when (ext) {
                        "mp4", "mkv", "3gp", "avi", "webm" -> "video/*"
                        "mp3", "wav", "ogg", "aac", "m4a", "flac" -> "audio/*"
                        "pdf" -> "application/pdf"
                        "html", "htm" -> "text/html"
                        else -> "*/*"
                    }
                }

                // HTML files can be launched directly inside the WebView!
                if (mime.contains("html", ignoreCase = true)) {
                    try {
                        val input = context.contentResolver.openInputStream(dataUri)
                        val text = input?.bufferedReader()?.use { it.readText() } ?: ""
                        addNewTab(url = "data:text/html;charset=utf-8," + android.net.Uri.encode(text))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        addNewTab(url = dataUri.toString())
                    }
                    return
                }

                // Copy to local app cache for consistent background playback and permission safety
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val cacheDir = java.io.File(context.cacheDir, "external_view")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val cacheFile = java.io.File(cacheDir, name)
                        
                        context.contentResolver.openInputStream(dataUri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (cacheFile.exists()) {
                            _uiState.update {
                                it.copy(
                                    activeViewerFile = ActiveViewerFile(
                                        filePath = cacheFile.absolutePath,
                                        fileName = name,
                                        mimeType = mime
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return
            }
        }

        val url = intent.dataString ?: intent.getStringExtra("url")
        if (!url.isNullOrEmpty()) {
            val activeId = _uiState.value.activeTabId
            val activeTab = _uiState.value.tabs.find { it.id == activeId }
            if (activeTab != null && (activeTab.url == "orion://newtab" || activeTab.url == "orion://newtab-incognito")) {
                navigateTo(url)
            } else {
                addNewTab(url = url)
            }
        }
    }

    fun saveTabsState() {
        val currentState = _uiState.value
        val tabs = currentState.tabs
        if (tabs.isEmpty()) return
        
        try {
            val jsonArray = org.json.JSONArray()
            for (tab in tabs) {
                if (tab.isIncognito) continue // Do not store incognito sessions
                
                val obj = org.json.JSONObject().apply {
                    put("id", tab.id)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("isDesktopMode", tab.isDesktopMode)
                    put("parentTabId", tab.parentTabId)
                }
                jsonArray.put(obj)
            }
            
            val prefs = getApplication<Application>().getSharedPreferences("orion_tab_state", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("saved_tabs_list", jsonArray.toString())
                putString("active_tab_id", currentState.activeTabId)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreTabsState() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("orion_tab_state", Context.MODE_PRIVATE)
            val savedListStr = prefs.getString("saved_tabs_list", null)
            val activeTabId = prefs.getString("active_tab_id", "")
            
            if (!savedListStr.isNullOrEmpty()) {
                val jsonArray = org.json.JSONArray(savedListStr)
                val restoredTabs = mutableListOf<TabState>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val url = obj.optString("url", "orion://newtab")
                    val title = obj.optString("title", "New Tab")
                    val isDesktopMode = obj.optBoolean("isDesktopMode", false)
                    val parentTabId = if (obj.has("parentTabId")) obj.optString("parentTabId") else null
                    
                    restoredTabs.add(
                        TabState(
                            id = id,
                            url = url,
                            title = title,
                            isDesktopMode = isDesktopMode,
                            isIncognito = false,
                            parentTabId = if (parentTabId.isNullOrEmpty()) null else parentTabId
                        )
                    )
                }
                if (restoredTabs.isNotEmpty()) {
                    val savedActiveId = prefs.getString("active_tab_id", "") ?: ""
                    val finalActiveId = if (restoredTabs.any { it.id == savedActiveId }) savedActiveId else restoredTabs.first().id
                    val activeTab = restoredTabs.find { it.id == finalActiveId }
                    _uiState.update { state ->
                        state.copy(
                            tabs = restoredTabs,
                            activeTabId = finalActiveId,
                            currentInputUrl = if (activeTab?.url == "orion://newtab") "" else (activeTab?.url ?: "")
                        )
                    }
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Default startup tab
        addNewTab()
    }

    fun resetFilePickerState() {
        _uiState.update { it.copy(showFilePicker = false) }
    }

    inner class BrowserWebView(context: Context, val tabId: String) : WebView(context) {
        private var startX = 0f
        private var startY = 0f
        private val swipeThreshold = 150f
        private val yThreshold = 100f
        private var accumulatedNativeScroll = 0

        override fun onWindowVisibilityChanged(visibility: Int) {
            if (visibility == View.GONE || visibility == View.INVISIBLE) {
                super.onWindowVisibilityChanged(View.VISIBLE)
            } else {
                super.onWindowVisibilityChanged(visibility)
            }
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            val currentY = t
            val diff = currentY - oldt
            
            if ((diff > 0 && accumulatedNativeScroll < 0) || (diff < 0 && accumulatedNativeScroll > 0)) {
                accumulatedNativeScroll = 0
            }
            
            accumulatedNativeScroll += diff
            
            if (currentY <= 15) {
                setToolbarsVisible(true)
                accumulatedNativeScroll = 0
            } else {
                if (accumulatedNativeScroll > 100) {
                    setToolbarsVisible(false)
                    accumulatedNativeScroll = 0
                } else if (accumulatedNativeScroll < -60) {
                    setToolbarsVisible(true)
                    accumulatedNativeScroll = 0
                }
            }
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val diffX = event.x - startX
                    val diffY = event.y - startY
                    
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > swipeThreshold && Math.abs(diffY) < yThreshold) {
                        if (diffX > 0) {
                            if (canGoBack()) {
                                goBack()
                                return true
                            }
                        } else {
                            if (canGoForward()) {
                                goForward()
                                return true
                            }
                        }
                    }
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // Lazily get or create the WebView for Composable use
    fun getOrCreateWebView(tabId: String, context: Context): WebView {
        val existing = webViewMap[tabId]
        if (existing != null) {
            if (_uiState.value.activeTabId == tabId) {
                existing.onResume()
            } else {
                existing.onPause()
            }
            return existing
        }

        val currentTab = _uiState.value.tabs.find { it.id == tabId }

        val webView = BrowserWebView(context, tabId).apply {
            id = View.generateViewId()

            setOnLongClickListener {
                val result = hitTestResult
                val type = result.type
                val extra = result.extra
                if (extra != null) {
                    when (type) {
                        android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE,
                        android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            showContextMenu(url = extra, isImage = false, isImageLink = (type == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE))
                            true
                        }
                        android.webkit.WebView.HitTestResult.IMAGE_TYPE -> {
                            showContextMenu(url = extra, isImage = true)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            applyWebViewSettings(
                this,
                _uiState.value.isJavaScriptEnabled,
                _uiState.value.isHardwareAccelerationEnabled,
                currentTab?.isDesktopMode ?: false
            )
            extensionManager.setupWebView(this, tabId)

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                try {
                    val fileName = extractFileName(contentDisposition, url, mimetype)
                    showDownloadConfirmation(url, fileName, contentLength, mimetype, userAgent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return try {
                        val urlStr = request?.url?.toString()
                        if (urlStr != null && (urlStr.startsWith("chrome-extension://") || urlStr.startsWith("orion-extension://"))) {
                            val interceptRes = com.example.extensionengine.ExtensionDirectoryResolver.handleExtensionRequest(context, urlStr)
                            if (interceptRes != null) return interceptRes
                        }
                        val currentDocUrl = view?.url ?: _uiState.value.tabs.find { it.id == tabId }?.url
                        if (com.example.adblockengine.AdBlocker.shouldBlock(urlStr, currentDocUrl)) {
                            incrementBlockedAdsCount(tabId)
                            com.example.adblockengine.AdBlocker.createEmptyResponse()
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        super.shouldInterceptRequest(view, request)
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    try {
                        super.onPageStarted(view, url, favicon)
                        isUASwitchPending = false
                        if (url != null && view != null && !url.startsWith("orion://")) {
                            val host = android.net.Uri.parse(url).host
                            if (!host.isNullOrEmpty()) {
                                val isDesktop = domainDesktopMap[host] == true
                                val currentTab = _uiState.value.tabs.find { it.id == tabId }
                                val needsUpdate = (currentTab?.isDesktopMode != isDesktop)
                                if (needsUpdate) {
                                    updateTabState(tabId) { it.copy(isDesktopMode = isDesktop) }
                                }
                                view.settings.userAgentString = if (isDesktop || url.contains("chromewebstore.google.com") || url.contains("chrome.google.com/webstore")) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Safari/537.36"
                                } else {
                                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
                                }

                                // Direct mobile domain enforcement
                                if (!isDesktop && !url.contains("translate.google.com") && !url.contains("chromewebstore.google.com") && !url.contains("chrome.google.com/webstore")) {
                                    val redirectedUrl = enforceMobileUrl(url)
                                    if (redirectedUrl != url) {
                                        view.post {
                                            try {
                                                view.loadUrl(redirectedUrl)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        return
                                    }
                                }
                            }
                        }
                        val currentTabState = _uiState.value.tabs.find { it.id == tabId }
                        val isTabIncognito = currentTabState?.isIncognito == true
                        val currentUrl = currentTabState?.url ?: ""
                        val finalUrl = if (url == "about:blank") {
                            if (currentUrl == "about:blank" || currentUrl.isEmpty() || currentUrl.startsWith("orion://newtab")) {
                                if (isTabIncognito) "orion://newtab-incognito" else "orion://newtab"
                            } else {
                                currentUrl
                            }
                        } else if (url == "orion://newtab" || url == "orion://newtab-incognito") {
                            url
                        } else {
                            url ?: (if (currentUrl.isNotEmpty()) currentUrl else if (isTabIncognito) "orion://newtab-incognito" else "orion://newtab")
                        }
                        updateTabState(tabId) {
                            it.copy(
                                url = finalUrl,
                                isLoading = true,
                                progress = 10,
                                favicon = favicon ?: it.favicon,
                                readerModeAvailable = false,
                                blockedAdsCount = 0,
                                hasLoadedSuccessfully = false
                            )
                        }
                        if (_uiState.value.activeTabId == tabId) {
                            _uiState.update { 
                                it.copy(
                                    currentInputUrl = if (finalUrl == "orion://newtab" || finalUrl == "orion://newtab-incognito") "" else finalUrl,
                                    isPageTranslated = false,
                                    showTranslateBar = false
                                ) 
                            }
                            translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Hidden)
                        }
                        try {
                            if (url != null) {
                                val params = org.json.JSONObject().apply {
                                    put("tabId", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                    put("changeInfo", org.json.JSONObject().put("status", "loading").put("url", url))
                                    put("tab", org.json.JSONObject().apply {
                                        put("id", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                        put("url", url)
                                        put("status", "loading")
                                    })
                                }
                                extensionManager.engine.eventManager.triggerEvent("tabs.onUpdated", params)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    if (view != null && url != null && !url.startsWith("orion://") && !url.startsWith("about:") && !url.startsWith("file://")) {
                        try {
                            // Inject early API bootstrap hook rules so extension script variables are populated before page scripts complete
                            extensionManager.setupWebView(view, tabId)
                            extensionManager.injectContentScripts(view, url)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        super.onPageFinished(view, url)
                        // Persist cookies to disk instantly
                        try {
                            android.webkit.CookieManager.getInstance().flush()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        isUASwitchPending = false
                        if (url != null) {
                            val host = try { android.net.Uri.parse(url).host } catch (e: Exception) { null }
                            if (!host.isNullOrEmpty()) {
                                val isDesktopModeSaved = domainDesktopMap[host] == true
                                val currentTab = _uiState.value.tabs.find { it.id == tabId }
                                if (currentTab?.isDesktopMode != isDesktopModeSaved) {
                                    updateTabState(tabId) { it.copy(isDesktopMode = isDesktopModeSaved) }
                                }
                            }
                        }
                        val currentTabState = _uiState.value.tabs.find { it.id == tabId }
                        val isTabIncognito = currentTabState?.isIncognito == true
                        val currentUrl = currentTabState?.url ?: ""
                        val finalUrl = if (url == "about:blank") {
                            if (currentUrl == "about:blank" || currentUrl.isEmpty() || currentUrl.startsWith("orion://newtab")) {
                                if (isTabIncognito) "orion://newtab-incognito" else "orion://newtab"
                            } else {
                                currentUrl
                            }
                        } else if (url == "orion://newtab" || url == "orion://newtab-incognito") {
                            url
                        } else {
                            url ?: (if (currentUrl.isNotEmpty()) currentUrl else if (isTabIncognito) "orion://newtab-incognito" else "orion://newtab")
                        }
                        val title = view?.title ?: ""
                        updateTabState(tabId) {
                            it.copy(
                                url = finalUrl,
                                title = if (finalUrl == "orion://newtab" || finalUrl == "orion://newtab-incognito") {
                                    if (isTabIncognito) "Incognito Tab" else "New Tab"
                                } else {
                                    title.ifBlank { finalUrl }
                                },
                                isLoading = false,
                                progress = 100,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false,
                                hasLoadedSuccessfully = true
                            )
                        }



                        val isDesktop = _uiState.value.tabs.find { it.id == tabId }?.isDesktopMode == true
                        if (isDesktop && view != null) {
                            view.evaluateJavascript("""
                                var meta = document.querySelector('meta[name=viewport]');
                                if(meta) {
                                    meta.content = 'width=1280, initial-scale=0.25';
                                } else {
                                    var newMeta = document.createElement('meta');
                                    newMeta.name = 'viewport';
                                    newMeta.content = 'width=1280, initial-scale=0.25';
                                    document.head.appendChild(newMeta);
                                }
                            """.trimIndent(), null)
                        }

                        // Inject Simulated Chrome/User extensions upon page load complete
                        if (view != null && url != null && !url.startsWith("orion://") && url != "about:blank") {
                            // A. Dark Reader Extension
                            if (prefs.getBoolean("ext_dark_reader", false)) {
                                val brightness = prefs.getInt("ext_dark_reader_brightness", 90)
                                val contrast = prefs.getInt("ext_dark_reader_contrast", 100)
                                val sepia = prefs.getInt("ext_dark_reader_sepia", 0)
                                val grayscale = prefs.getInt("ext_dark_reader_grayscale", 0)
                                val filterString = "invert(90%) hue-rotate(180deg) brightness($brightness%) contrast($contrast%) sepia($sepia%) grayscale($grayscale%)"
                                val jsDark = """
                                    (function() {
                                        var css = 'html { filter: $filterString !important; background: #121212 !important; } img, video, iframe, canvas, [style*="background-image"] { filter: invert(100%) hue-rotate(180deg) !important; }';
                                        var style = document.getElementById('dark-reader-ext');
                                        if (!style) {
                                            style = document.createElement('style');
                                            style.id = 'dark-reader-ext';
                                            style.type = 'text/css';
                                            style.appendChild(document.createTextNode(css));
                                            document.head.appendChild(style);
                                        }
                                    })()
                                """.trimIndent()
                                view.evaluateJavascript(jsDark, null)
                            }
                            
                            // B. Auto Translate to Hindi Extension
                            if (prefs.getBoolean("ext_auto_translate", false)) {
                                val jsTranslate = """
                                    (function() {
                                        var existingCombo = document.querySelector('select.goog-te-combo');
                                        if (existingCombo) {
                                            existingCombo.value = 'hi';
                                            existingCombo.dispatchEvent(new Event('change'));
                                            return;
                                        }
                                        var container = document.createElement('div');
                                        container.id = 'google_translate_element';
                                        container.style.display = 'none';
                                        document.body.appendChild(container);
                                        
                                        window.googleTranslateElementInit = function() {
                                            new google.translate.TranslateElement({
                                                pageLanguage: 'auto',
                                                layout: google.translate.TranslateElement.InlineLayout.SIMPLE,
                                                autoDisplay: false
                                            }, 'google_translate_element');
                                            
                                            var checkInterval = setInterval(function() {
                                                var combo = document.querySelector('select.goog-te-combo');
                                                if (combo) {
                                                    clearInterval(checkInterval);
                                                    combo.value = 'hi';
                                                    combo.dispatchEvent(new Event('change'));
                                                }
                                            }, 150);
                                        };
                                        
                                        var tag = document.createElement('script');
                                        tag.type = 'text/javascript';
                                        tag.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
                                        document.body.appendChild(tag);
                                    })()
                                """.trimIndent()
                                view.evaluateJavascript(jsTranslate, null)
                            }

                            // C. Custom Script Extension
                            if (prefs.getBoolean("ext_custom_script_enabled", false)) {
                                val customScript = prefs.getString("ext_custom_script_code", "")
                                if (customScript.isNotEmpty()) {
                                    view.evaluateJavascript("(function() { $customScript })()", null)
                                }
                            }

                            // D. Uploaded Zip Extension Script
                            if (prefs.getBoolean("ext_uploaded_script_enabled", false)) {
                                val uploadedScript = prefs.getString("ext_uploaded_script_code", "")
                                if (uploadedScript.isNotEmpty()) {
                                    view.evaluateJavascript("(function() { $uploadedScript })()", null)
                                }
                            }

                            // Chrome Web Store 'Add to Chrome' click-interception hook
                            if (url.contains("chromewebstore.google.com") || url.contains("chrome.google.com/webstore")) {
                                val webStoreHookJs = """
                                    (function() {
                                        function extractExtensionId() {
                                            var path = window.location.pathname;
                                            if (path.includes("/detail/")) {
                                                var parts = path.split("/");
                                                var idCandidate = parts[parts.length - 1];
                                                if (idCandidate && idCandidate.length === 32 && /^[a-z]+$/.test(idCandidate)) {
                                                    return idCandidate;
                                                }
                                            }
                                            return null;
                                        }

                                        function hookAddToChromeButtons() {
                                            var buttons = document.querySelectorAll('button');
                                            buttons.forEach(function(btn) {
                                                var text = btn.innerText || btn.textContent || "";
                                                var ariaLabel = btn.getAttribute("aria-label") || "";
                                                var isTarget = /Add to Chrome|Add to Orion|Add to Brave|Add to Desktop/i.test(text) || 
                                                               /Add to Chrome|Add to Orion|Add to Brave|Add to Desktop/i.test(ariaLabel);
                                                               
                                                if (isTarget && !btn.hasAttribute("data-orion-hooked")) {
                                                    btn.setAttribute("data-orion-hooked", "true");
                                                    btn.style.border = "4px solid #6366F1";
                                                    btn.addEventListener('click', function(e) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        var extId = extractExtensionId();
                                                        if (extId) {
                                                            try {
                                                                OrionJS.installWebStoreExtension(extId);
                                                            } catch(err) {
                                                                console.error("OrionJS webstore install integration failed", err);
                                                            }
                                                        } else {
                                                            alert("Could not detect extension ID from the page URL.");
                                                        }
                                                    }, true);
                                                }
                                            });
                                        }

                                        setInterval(hookAddToChromeButtons, 500);
                                        hookAddToChromeButtons();
                                    })();
                                """.trimIndent()
                                view.evaluateJavascript(webStoreHookJs, null)
                            }

                            // E. True MV2 & MV3 Content Scripts and Bridges
                            extensionManager.injectContentScripts(view, url)

                            // F. Web Notification Polyfill Injection
                            val notifJs = """
                                (function() {
                                    if (!window.Notification) {
                                        window.Notification = function(title, options) {
                                            this.title = title;
                                            this.body = (options && options.body) ? options.body : "";
                                            try {
                                                OrionJS.showWebNotification(window.location.host, this.title, this.body);
                                            } catch(e){}
                                        };
                                        window.Notification.permission = "default";
                                        window.Notification.requestPermission = function(callback) {
                                            return new Promise(function(resolve, reject) {
                                                try {
                                                    OrionJS.requestNotificationPermission(window.location.host);
                                                } catch(e){}
                                                resolve("granted");
                                            });
                                        };
                                    }
                                })()
                            """.trimIndent()
                            view.evaluateJavascript(notifJs, null)
                        }

                        // Translate engine hook
                        if (url != null && !url.contains("translate.google.com") && !url.startsWith("orion://") && url != "about:blank") {
                            // Extract sample text for Chrome-style language detection Offer
                            val detectionScript = """
                                (function() {
                                    try {
                                        return (document.title || '') + ' ' + (document.body ? document.body.innerText.substring(0, 300) : '');
                                    } catch(e) {
                                        return '';
                                    }
                                })()
                            """.trimIndent()
                            view?.evaluateJavascript(detectionScript) { innerText ->
                                if (!innerText.isNullOrBlank() && innerText != "null" && innerText != "\"\"") {
                                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        try {
                                            val detectedLang = translateManager.detectPageLanguage(innerText)
                                            val targetLangCode = _uiState.value.translateTargetLangCode
                                            val host = com.example.adblockengine.AdBlocker.getDomainName(url) ?: ""
                                            val isNeverSite = translateManager.settings.getNeverTranslateSites().contains(host)
                                            val isNeverLang = translateManager.settings.getNeverTranslateLanguages().contains(detectedLang)
                                            
                                            if (detectedLang.isNotEmpty() && detectedLang != "unknown" && detectedLang != targetLangCode) {
                                                // Check auto translate first
                                                val autoTranslate = translateManager.settings.isAutoTranslateEnabled() || 
                                                                    translateManager.settings.getAlwaysTranslateSites().contains(host)
                                                
                                                if (autoTranslate && !isNeverSite && !isNeverLang) {
                                                    // Auto translate
                                                    val targetLangName = _uiState.value.translateTargetLang
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        executeGoogleTranslation(targetLangCode, targetLangName)
                                                    }
                                                }
                                                // Automatic offer popups are completely disabled under any circumstances! Only manual triggers.
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        } else if (url != null && url.contains("translate.google.com")) {
                            _uiState.update { 
                                it.copy(
                                    isPageTranslated = true,
                                    showTranslateBar = true
                                ) 
                            }
                        }

                        if (url != null && url != "orion://newtab" && url != "orion://newtab-incognito" && !url.startsWith("orion://") && !isTabIncognito) {
                            viewModelScope.launch {
                                repository.addHistory(url, title)
                            }
                        }

                        if (url != null && url.contains("youtube.com") && com.example.adblockengine.AdBlocker.youtubeAdSkipEnabled) {
                            view?.evaluateJavascript("""
                                (function() {
                                    var selectors = [
                                        '.ad-showing', '.ad-interrupting',
                                        '#player-ads', '.ytp-ad-module',
                                        '.ytd-display-ad-renderer',
                                        '.ytd-promoted-sparkles-web-renderer',
                                        'ytd-ad-slot-renderer',
                                        'ytd-in-feed-ad-layout-renderer'
                                    ];
                                    selectors.forEach(function(sel) {
                                        document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
                                    });
                                    
                                    setInterval(function() {
                                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button');
                                        if (skipBtn) { skipBtn.click(); }
                                        
                                        var adVideo = document.querySelector('video.ad-showing');
                                        if (adVideo) { adVideo.currentTime = adVideo.duration; }
                                    }, 300);
                                })();
                            """.trimIndent(), null)
                        }

                        if (url != null && !url.startsWith("orion://")) {
                            detectReaderModeAvailability(view)
                        }
                        try {
                            if (url != null) {
                                val params = org.json.JSONObject().apply {
                                    put("tabId", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                    put("changeInfo", org.json.JSONObject().put("status", "complete").put("url", url))
                                    put("tab", org.json.JSONObject().apply {
                                        put("id", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                        put("url", url)
                                        put("status", "complete")
                                    })
                                }
                                extensionManager.engine.eventManager.triggerEvent("tabs.onUpdated", params)

                                val navParams = org.json.JSONObject().apply {
                                    put("details", org.json.JSONObject().apply {
                                        put("tabId", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                        put("url", url)
                                        put("timeStamp", System.currentTimeMillis())
                                    })
                                }
                                extensionManager.engine.eventManager.triggerEvent("webNavigation.onCompleted", navParams)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    try {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (url != null && view != null && !url.startsWith("orion://") && url != "about:blank") {
                            val currentTab = _uiState.value.tabs.find { it.id == tabId }
                            if (currentTab != null && currentTab.url != url) {
                                val cleanUrl = if (url == "about:blank") "" else url
                                updateTabState(tabId) {
                                    it.copy(
                                        url = cleanUrl,
                                        title = view.title?.ifBlank { cleanUrl } ?: cleanUrl
                                    )
                                }
                                if (_uiState.value.activeTabId == tabId) {
                                    _uiState.update { 
                                        it.copy(currentInputUrl = cleanUrl) 
                                    }
                                }
                                
                                extensionManager.injectContentScripts(view, url)
                                
                                try {
                                    val params = org.json.JSONObject().apply {
                                        put("tabId", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                        put("changeInfo", org.json.JSONObject().put("status", "complete").put("url", url))
                                        put("tab", org.json.JSONObject().apply {
                                            put("id", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                            put("url", url)
                                            put("status", "complete")
                                        })
                                    }
                                    extensionManager.engine.eventManager.triggerEvent("tabs.onUpdated", params)

                                    val navParams = org.json.JSONObject().apply {
                                        put("details", org.json.JSONObject().apply {
                                            put("tabId", com.example.extensionengine.TabIdMapper.getIntId(tabId))
                                            put("url", url)
                                            put("timeStamp", System.currentTimeMillis())
                                        })
                                    }
                                    extensionManager.engine.eventManager.triggerEvent("webNavigation.onHistoryStateUpdated", navParams)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    try {
                        val isMainFrame = request?.isForMainFrame ?: false
                        val currentTab = _uiState.value.tabs.find { it.id == tabId }
                        if (isMainFrame && currentTab?.hasLoadedSuccessfully != true) {
                            val failingUrl = request?.url?.toString() ?: ""
                            val description = error?.description?.toString() ?: ""
                            val errorCode = error?.errorCode ?: 0
                            
                            // Check if the device is actually offline
                            val isConnected = try {
                                val cm = getApplication<android.app.Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                val activeNetwork = cm.activeNetwork
                                if (activeNetwork == null) {
                                    false
                                } else {
                                    val capabilities = cm.getNetworkCapabilities(activeNetwork)
                                    capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
                                }
                            } catch (e: Exception) {
                                true // default to online to avoid false positives
                            }

                            if (!isConnected) {
                                val errorType = when {
                                    description.contains("disconnected", true) || description.contains("offline", true) || errorCode == ERROR_CONNECT -> "offline"
                                    errorCode == ERROR_TIMEOUT -> "timeout"
                                    else -> "offline"
                                }
                                loadErrorHtml(view, errorType, failingUrl)
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    try {
                        val isMainFrame = request?.isForMainFrame ?: false
                        if (isMainFrame && errorResponse?.statusCode == 404) {
                            val failingUrl = request?.url?.toString() ?: ""
                            loadErrorHtml(view, "404", failingUrl)
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    try {
                        val failingUrl = error?.url ?: ""
                        if (failingUrl.isNotEmpty()) {
                            val host = try { android.net.Uri.parse(failingUrl).host } catch (e: Exception) { null }
                            if (host != null && sslDomainsToIgnore.contains(host)) {
                                handler?.proceed()
                                return
                            }
                        }
                        
                        val webViewUrl = view?.url ?: ""
                        val isMainFrame = if (failingUrl.isNotEmpty() && webViewUrl.isNotEmpty()) {
                            val failingHost = try { android.net.Uri.parse(failingUrl).host } catch (e: Exception) { null }
                            val webViewHost = try { android.net.Uri.parse(webViewUrl).host } catch (e: Exception) { null }
                            failingHost != null && webViewHost != null && failingHost.equals(webViewHost, ignoreCase = true)
                        } else {
                            true // fallback can be true to handle errors gracefully
                        }

                        if (isMainFrame) {
                            loadErrorHtml(view, "ssl", failingUrl)
                            handler?.cancel()
                        } else {
                            handler?.proceed() // bypass subresource certificate issues smoothly
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        handler?.cancel()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    try {
                        val url = request?.url?.toString() ?: return false
                        
                        if (isUASwitchPending) {
                            isUASwitchPending = false
                            return false
                        }
                        
                        val domainCheck = try { android.net.Uri.parse(url).host } catch (e: Exception) { null }
                        val lastDomain = try { android.net.Uri.parse(lastManualLoadUrl).host } catch (e: Exception) { null }
                        if (domainCheck != null && lastDomain != null && domainCheck == lastDomain) {
                            return false
                        }

                        if (url.startsWith("orion://retry")) {
                            val queryUrl = request.url.getQueryParameter("url")
                            if (!queryUrl.isNullOrBlank()) {
                                safeLoadUrl(view, queryUrl)
                            } else {
                                safeLoadUrl(view, "orion://newtab")
                            }
                            return true
                        }
                        if (url.startsWith("orion://proceed_ssl")) {
                            val queryUrl = request.url.getQueryParameter("url")
                            if (!queryUrl.isNullOrBlank()) {
                                val host = try { android.net.Uri.parse(queryUrl).host } catch (e: Exception) { null }
                                if (host != null) {
                                    sslDomainsToIgnore.add(host)
                                }
                                safeLoadUrl(view, queryUrl)
                            } else {
                                safeLoadUrl(view, "orion://newtab")
                            }
                            return true
                        }
                        if (url.startsWith("javascript:", true)) {
                            return true
                        }
                        if (url.startsWith("intent://", true)) {
                            try {
                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                intent.setComponent(null)
                                intent.setSelector(null)
                                view?.context?.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return true
                        }
                        if (url.startsWith("tel:", true) || url.startsWith("mailto:", true) || url.startsWith("market:", true)) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                view?.context?.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return true
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                    return false
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    try {
                        android.util.Log.e("WebViewClient", "WebView render process gone")
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    updateTabState(tabId) {
                        it.copy(progress = newProgress, isLoading = newProgress < 100)
                    }
                }

                override fun onReceivedTitle(view: WebView?, titleStr: String?) {
                    super.onReceivedTitle(view, titleStr)
                    val currentUrl = view?.url ?: ""
                    updateTabState(tabId) {
                        it.copy(title = if (currentUrl == "orion://newtab" || currentUrl == "orion://newtab-incognito") {
                            val isTabIncognito = _uiState.value.tabs.find { it.id == tabId }?.isIncognito == true
                            if (isTabIncognito) "Incognito Tab" else "New Tab"
                        } else (titleStr ?: it.title).ifBlank { currentUrl })
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    val host = view?.url?.let { android.net.Uri.parse(it).host } ?: ""
                    if (host.isNotEmpty() && icon != null) {
                        faviconCache.put(host, icon)
                        updateTabState(tabId) {
                            it.copy(favicon = icon)
                        }
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: android.webkit.GeolocationPermissions.Callback?
                ) {
                    if (origin != null && callback != null) {
                        setPendingGeolocationPrompt(Pair(origin, callback))
                    } else {
                        callback?.invoke(origin, false, false)
                    }
                }

                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                    if (request != null) {
                        setPendingPermissionRequest(request)
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val contextLocal = view!!.context
                    val newTabId = java.util.UUID.randomUUID().toString()
                    val isTabIncognito = _uiState.value.tabs.find { it.id == tabId }?.isIncognito == true
                    
                    val newTab = TabState(
                        id = newTabId,
                        url = "about:blank",
                        title = "Loading...",
                        isIncognito = isTabIncognito,
                        parentTabId = tabId
                    )
                    
                    _uiState.update { state ->
                        state.copy(
                            tabs = state.tabs + newTab,
                            activeTabId = newTabId
                        )
                    }
                    
                    val newWebView = getOrCreateWebView(newTabId, contextLocal)
                    
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        transport.webView = newWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                    return false
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback
                    _uiState.update { it.copy(showFilePicker = true) }
                    return true
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    if (view != null && callback != null) {
                        _fullscreenState.value = FullscreenState(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    _fullscreenState.value?.callback?.onCustomViewHidden()
                    _fullscreenState.value = null
                }
            }

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onReaderModeDetected(available: Boolean) {
                    viewModelScope.launch {
                        updateTabState(tabId) {
                            it.copy(readerModeAvailable = available)
                        }
                    }
                }

                @JavascriptInterface
                fun onReaderContentExtracted(title: String, author: String, content: String) {
                    viewModelScope.launch {
                        _uiState.update {
                            it.copy(
                                readerModeTitle = title,
                                readerModeAuthor = author.ifBlank { null },
                                readerModeContent = content,
                                readerModeActive = true
                            )
                        }
                    }
                }

                @JavascriptInterface
                fun setToolbarsVisible(visible: Boolean) {
                    viewModelScope.launch(Dispatchers.Main) {
                        setToolbarsVisible(visible)
                    }
                }

                @JavascriptInterface
                fun toggleToolbars() {
                    viewModelScope.launch(Dispatchers.Main) {
                        toggleToolbarsVisible()
                    }
                }

                @JavascriptInterface
                fun requestNotificationPermission(origin: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        showNotificationPermissionDialog(origin)
                    }
                }

                @JavascriptInterface
                fun showWebNotification(origin: String, title: String, body: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        triggerWebNotification(origin, title, body)
                    }
                }

                @JavascriptInterface
                fun installWebStoreExtension(extensionId: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        val app = getApplication<Application>()
                        android.widget.Toast.makeText(app, "Web Store 'Add to Chrome' captured. Installing...", android.widget.Toast.LENGTH_LONG).show()
                        downloadChromeExtension(app, extensionId) { success, msg ->
                            android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }, "OrionJS")
        }

        applyWebViewSettings(
            webView,
            _uiState.value.isJavaScriptEnabled,
            _uiState.value.isHardwareAccelerationEnabled,
            currentTab?.isDesktopMode ?: false
        )

        // If the URL is already present in TabState, load it initially
        val urlToLoad = currentTab?.url ?: "orion://newtab"
        if (urlToLoad != "orion://newtab" && urlToLoad != "orion://newtab-incognito") {
            safeLoadUrl(webView, urlToLoad)
        }

        webViewMap[tabId] = webView
        return webView
    }

    private fun applyWebViewSettings(webView: WebView, jsEnabled: Boolean, hwEnabled: Boolean, isDesktop: Boolean = false) {
        webView.settings.apply {
            javaScriptEnabled = jsEnabled
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setGeolocationEnabled(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            userAgentString = if (isDesktop) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
        }

        try {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hwEnabled) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun updateTabState(tabId: String, update: (TabState) -> TabState) {
        val oldTab = _uiState.value.tabs.find { it.id == tabId }
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map {
                    if (it.id == tabId) update(it) else it
                }
            )
        }
        val newTab = _uiState.value.tabs.find { it.id == tabId }
        if (oldTab != null && newTab != null && (oldTab.url != newTab.url || oldTab.title != newTab.title || oldTab.isDesktopMode != newTab.isDesktopMode)) {
            saveTabsState()
        }
    }

    // Lazily preloading adjacent tabs
    private fun preloadAdjacentTabs() {
        val activeId = _uiState.value.activeTabId
        val tabs = _uiState.value.tabs
        val activeIndex = tabs.indexOfFirst { it.id == activeId }
        if (activeIndex == -1) return

        // Preload next and prev tab
        val indicesToPreload = listOf(activeIndex - 1, activeIndex + 1)
        indicesToPreload.forEach { index ->
            if (index in tabs.indices) {
                val adjacentTab = tabs[index]
                if (adjacentTab.url != "orion://newtab" && !webViewMap.containsKey(adjacentTab.id) && !adjacentTab.isWebViewDestroyed) {
                    viewModelScope.launch {
                        // Create webview on main thread via getOrCreateWebView (handled in UI)
                    }
                }
            }
        }
    }

    // Clean up background webviews inactive for 30 minutes
    private fun cleanupOlderWebViews() {
        val activeId = _uiState.value.activeTabId
        val tabs = _uiState.value.tabs
        val thirtyMinsMs = 30 * 60 * 1000L
        val now = System.currentTimeMillis()

        tabs.forEach { tab ->
            if (tab.id != activeId && webViewMap.containsKey(tab.id)) {
                if (now - tab.lastActiveTime > thirtyMinsMs) {
                    // Destroy webview and mark state as destroyed
                    destroyTabWebView(tab.id)
                    updateTabState(tab.id) {
                        it.copy(isWebViewDestroyed = true)
                    }
                }
            }
        }
    }

    // Custom Error HTML generator loaded into WebView
    private fun loadErrorHtml(webView: WebView?, errorType: String, failingUrl: String) {
        if (webView == null) return
        val titleText = when (errorType) {
            "offline" -> "You're offline"
            "404" -> "Page not found"
            "ssl" -> "Connection not secure"
            "timeout" -> "Page took too long"
            else -> "Something went wrong"
        }

        val descText = when (errorType) {
            "offline" -> "Check your internet connection and try reloading the page."
            "404" -> "The page you're looking for doesn't exist or has been moved."
            "ssl" -> "The site's security certificate is invalid. Your connection to this site is not private."
            "timeout" -> "The server at ${failingUrl.take(40)} is taking too long to respond."
            else -> "We encountered an error while trying to process your request."
        }

        val iconSvg = when (errorType) {
            "offline" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#BB86FC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19A3.5 3.5 0 0 0 21 15.5c0-2.79-2.54-4.5-5-4.5-.42-1.95-2-3.5-4-3.5a5.52 5.52 0 0 0-5.18 3.79c-1.3-.23-2.82.21-3.82 1.21A4.5 4.5 0 0 0 3 15.5c0 1.93 1.57 3.5 3.5 3.5h11z"/><path d="M15 11l-6 6"/><path d="M9 11l6 6"/></svg>"""
            "ssl" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#CF6679" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>"""
            "timeout" -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#BB86FC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>"""
            else -> """<svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="#BB86FC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>"""
        }

        var extraContent = ""
        if (errorType == "offline") {
            // Draw a neat minimal list of bookmarks and recent history as cached offline options!
            viewModelScope.launch {
                val bms = bookmarks.value.take(5)
                val hms = history.value.take(5)
                val listHtml = StringBuilder()
                if (bms.isNotEmpty() || hms.isNotEmpty()) {
                    listHtml.append("""<div style="margin-top: 32px; text-align: left; background:#1E1E1E; padding: 16px; border-radius: 12px; box-shadow: inset 0 0 10px rgba(0,0,0,0.5)">""")
                    listHtml.append("""<h3 style="font-size: 14px; margin-top: 0; color: #BB86FC; border-bottom: 1px solid #333; padding-bottom: 6px;">Saved Pages / Offline Links</h3>""")
                    
                    bms.forEach { bm ->
                        listHtml.append("""<a href="orion://retry?url=${Uri.encode(bm.url)}" style="color: #E0E0E0; text-decoration: none; display: block; font-size: 13px; margin: 8px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">⭐ ${bm.title}</a>""")
                    }
                    hms.forEach { hm ->
                        listHtml.append("""<a href="orion://retry?url=${Uri.encode(hm.url)}" style="color: #A0A0A0; text-decoration: none; display: block; font-size: 13px; margin: 8px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">🕒 ${hm.title}</a>""")
                    }
                    listHtml.append("</div>")
                }
                extraContent = listHtml.toString()
                loadHtml(webView, titleText, descText, iconSvg, failingUrl, extraContent)
            }
        } else {
            if (errorType == "ssl") {
                extraContent = """
                    <div style="margin-top: 24px;">
                        <a href="orion://proceed_ssl?url=${Uri.encode(failingUrl)}" style="color: #CF6679; text-decoration: none; font-size: 14px; font-weight: bold; border: 1.5px solid #CF6679; padding: 12px 24px; border-radius: 24px; display: inline-block; transition: all 0.2s ease;">Proceed anyway (unsafe)</a>
                    </div>
                """.trimIndent()
            }
            loadHtml(webView, titleText, descText, iconSvg, failingUrl, extraContent)
        }
    }

    private fun loadHtml(webView: WebView, titleText: String, descText: String, iconSvg: String, failingUrl: String, extraContent: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    body {
                        background-color: #121212;
                        color: #E0E0E0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        margin: 0;
                        padding: 24px;
                        box-sizing: border-box;
                        text-align: center;
                    }
                    .container {
                        max-width: 420px;
                        width: 100%;
                    }
                    .icon-container {
                        margin-bottom: 24px;
                    }
                    h1 {
                        font-size: 22px;
                        margin-bottom: 12px;
                        font-weight: 600;
                        color: #FFF;
                    }
                    p {
                        font-size: 14px;
                        color: #9E9E9E;
                        margin-bottom: 28px;
                        line-height: 1.6;
                    }
                    .btn {
                        background-color: #3700B3;
                        background: linear-gradient(135deg, #BB86FC 0%, #3700B3 100%);
                        color: #121212;
                        border: none;
                        padding: 12px 32px;
                        font-size: 14px;
                        font-weight: bold;
                        border-radius: 24px;
                        cursor: pointer;
                        box-shadow: 0 4px 12px rgba(187, 134, 252, 0.2);
                        text-decoration: none;
                        display: inline-block;
                        transition: transform 0.2s;
                    }
                    .btn:active {
                        transform: scale(0.96);
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon-container">$iconSvg</div>
                    <h1>$titleText</h1>
                    <p>$descText</p>
                    <a class="btn" href="orion://retry?url=${Uri.encode(failingUrl)}">Try again</a>
                    $extraContent
                </div>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("orion://error", html, "text/html", "UTF-8", failingUrl)
    }

    // Active tab navigation
    fun navigateTo(url: String) {
        val formatted = formatUrlOrSearch(url)
        val activeId = _uiState.value.activeTabId
        if (activeId.isEmpty()) return

        updateTabState(activeId) {
            it.copy(
                url = formatted,
                isWebViewDestroyed = false,
                readerModeAvailable = false
            )
        }

        _uiState.update {
            it.copy(
                currentInputUrl = if (formatted == "orion://newtab" || formatted == "orion://newtab-incognito") "" else formatted,
                readerModeActive = false,
                isTabSwitcherOpen = false,
                isSearchFocused = false,
                areToolbarsVisible = true
            )
        }

        val webView = webViewMap[activeId]
        if (webView != null) {
            if (formatted == "orion://newtab" || formatted == "orion://newtab-incognito") {
                safeLoadUrl(webView, "about:blank")
            } else {
                safeLoadUrl(webView, formatted)
            }
        }
    }

    fun goBack() {
        val activeId = _uiState.value.activeTabId
        webViewMap[activeId]?.let {
            if (it.canGoBack()) {
                it.goBack()
            }
        }
    }

    fun goForward() {
        val activeId = _uiState.value.activeTabId
        webViewMap[activeId]?.let {
            if (it.canGoForward()) {
                it.goForward()
            }
        }
    }

    fun reload() {
        val activeId = _uiState.value.activeTabId
        webViewMap[activeId]?.reload()
    }

    // Reader Mode Logic
    private fun detectReaderModeAvailability(webView: WebView?) {
        webView?.evaluateJavascript(
            """
            (function() {
                var article = document.querySelector('article');
                if (article) return true;
                
                var paras = document.querySelectorAll('p');
                var wordCount = 0;
                paras.forEach(p => wordCount += p.innerText.split(/\s+/).length);
                return (paras.length >= 3 && wordCount > 350);
            })()
            """.trimIndent()
        ) { result ->
            val isAvailable = result?.toBoolean() ?: false
            val webUrl = webView.url ?: ""
            val isHttpOrHttps = webUrl.startsWith("http://", ignoreCase = true) || webUrl.startsWith("https://", ignoreCase = true)
            val finalAvailable = isAvailable || isHttpOrHttps
            webViewMap.forEach { (id, view) ->
                if (view == webView) {
                    updateTabState(id) { it.copy(readerModeAvailable = finalAvailable) }
                }
            }
        }
    }

    fun triggerReaderMode() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        
        webView.evaluateJavascript(
            """
            (function() {
                var title = "";
                var h1 = document.querySelector('h1');
                if (h1) title = h1.innerText;
                if (!title) title = document.title || "";
                
                var author = "";
                var metaAuthor = document.querySelector('meta[name="author"]');
                if (metaAuthor) author = metaAuthor.getAttribute('content');
                if (!author) {
                    var authorEl = document.querySelector("[class*='author'], [id*='author'], [rel='author']");
                    if (authorEl) author = authorEl.innerText;
                }
                
                var date = "";
                var metaDate = document.querySelector('meta[property="article:published_time"], meta[name="publish-date"], meta[name="pubdate"]');
                if (metaDate) date = metaDate.getAttribute('content');
                if (!date) {
                    var timeEl = document.querySelector('time');
                    if (timeEl) date = timeEl.innerText || timeEl.getAttribute('datetime');
                }
                if (!date) {
                    var dateEl = document.querySelector("[class*='date'], [class*='publish'], [id*='date']");
                    if (dateEl) date = dateEl.innerText;
                }

                var origin = window.location.origin || "";
                var domain = window.location.hostname || "";
                
                var container = document.querySelector('article');
                if (!container) {
                    var bestParent = null;
                    var maxPCount = 0;
                    var parents = Array.from(document.querySelectorAll('p')).map(p => p.parentElement);
                    var uniqueParents = Array.from(new Set(parents));
                    uniqueParents.forEach(p => {
                        if (!p) return;
                        var count = p.querySelectorAll('p').length;
                        if (count > maxPCount) {
                            maxPCount = count;
                            bestParent = p;
                        }
                    });
                    if (bestParent && maxPCount >= 2) container = bestParent;
                }
                
                var extractedHtml = "";
                if (container) {
                    var clone = container.cloneNode(true);
                    var selector = "script, style, nav, footer, header, form, iframe, .sidebar, .ads, [class*='ads'], [id*='ads'], [class*='promo'], [id*='promo'], [class*='widget'], [id*='widget'], [class*='share'], [id*='share'], [class*='cookie'], [class*='popup'], [class*='social']";
                    var toRemove = clone.querySelectorAll(selector);
                    toRemove.forEach(el => el.remove());
                    
                    var imgs = clone.querySelectorAll('img');
                    imgs.forEach(img => {
                        var src = img.getAttribute('src');
                        var dataSrc = img.getAttribute('data-src') || img.getAttribute('data-original');
                        if (dataSrc) src = dataSrc;
                        if (src) {
                            if (src.startsWith('//')) {
                                img.setAttribute('src', 'https:' + src);
                            } else if (src.startsWith('/')) {
                                img.setAttribute('src', origin + src);
                            } else if (!src.startsWith('http')) {
                                var path = window.location.pathname;
                                var basePath = path.substring(0, path.lastIndexOf('/') + 1);
                                img.setAttribute('src', origin + basePath + src);
                            } else {
                                img.setAttribute('src', src);
                            }
                            img.style.maxWidth = "100%";
                            img.style.height = "auto";
                            img.style.borderRadius = "8px";
                            img.style.margin = "12px 0";
                            img.style.display = "block";
                        } else {
                            img.remove();
                        }
                    });
                    
                    extractedHtml = clone.innerHTML;
                } else {
                    var paras = Array.from(document.querySelectorAll('p')).map(p => p.outerHTML);
                    extractedHtml = paras.join("");
                }
                
                if (!extractedHtml || extractedHtml.trim().length < 50) {
                    var divs = Array.from(document.querySelectorAll('div')).filter(function(d) {
                        var txt = d.innerText || "";
                        return txt.trim().split(/\s+/).length > 50 && d.querySelectorAll('div').length < 3;
                    });
                    if (divs.length > 0) {
                        extractedHtml = divs.map(function(d) { return "<p>" + (d.innerText || "") + "</p>"; }).join("");
                    }
                }
                if (!extractedHtml || extractedHtml.trim().length < 50) {
                    var bodyText = document.body ? (document.body.innerText || "") : "";
                    var lines = bodyText.split(/\n\n+/).filter(function(l) { return l.trim().length > 30; });
                    extractedHtml = lines.map(function(l) { return "<p>" + l.trim() + "</p>"; }).join("");
                }
                
                var payload = JSON.stringify({
                    title: title,
                    author: author,
                    date: date,
                    domain: domain,
                    html: extractedHtml
                });
                try {
                    return btoa(unescape(encodeURIComponent(payload)));
                } catch(e) {
                    return btoa(unescape(encodeURIComponent(JSON.stringify({
                        title: title,
                        author: author,
                        date: date,
                        domain: domain,
                        html: "Error encoding content: " + e.message
                    }))));
                }
            })()
            """.trimIndent()
        ) { b64Result ->
            try {
                var cleaned = b64Result ?: ""
                if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2) {
                    cleaned = cleaned.substring(1, cleaned.length - 1)
                }
                val decodedBytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
                val decodedString = String(decodedBytes, Charsets.UTF_8)
                
                val jsonObject = org.json.JSONObject(decodedString)
                val extractedTitle = jsonObject.optString("title", "Article")
                val extractedAuthor = jsonObject.optString("author", "")
                val extractedDate = jsonObject.optString("date", "")
                val extractedDomain = jsonObject.optString("domain", "")
                val extractedHtml = jsonObject.optString("html", "No text content found.")

                _uiState.update {
                    it.copy(
                        readerModeTitle = extractedTitle,
                        readerModeAuthor = extractedAuthor.ifBlank { null },
                        readerModeDate = extractedDate.ifBlank { null },
                        readerModeDomain = extractedDomain.ifBlank { null },
                        readerModeContent = extractedHtml,
                        readerModeActive = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        readerModeTitle = "Article Reader",
                        readerModeContent = "Failed to parse content cleanly: ${e.localizedMessage}. Please read the web version.",
                        readerModeActive = true
                    )
                }
            }
        }
    }

    fun closeReaderMode() {
        _uiState.update { it.copy(readerModeActive = false) }
    }

    fun updateReaderFontSize(size: Int) {
        viewModelScope.launch {
            prefs.setReaderFontSize(size)
        }
    }

    // Find In Page Implementation
    fun toggleFindInPage(active: Boolean) {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId]
        if (!active) {
            webView?.clearMatches()
            _uiState.update {
                it.copy(
                    findInPageActive = false,
                    findInPageQuery = "",
                    findInPageCurrentMatch = 0,
                    findInPageTotalMatches = 0
                )
            }
        } else {
            _uiState.update { it.copy(findInPageActive = true) }
            setupFindListener(webView)
        }
    }

    private fun setupFindListener(webView: WebView?) {
        webView?.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                _uiState.update {
                    it.copy(
                        findInPageCurrentMatch = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                        findInPageTotalMatches = numberOfMatches
                    )
                }
            }
        }
    }

    fun findInPageSearch(query: String) {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        _uiState.update { it.copy(findInPageQuery = query) }
        webView.findAllAsync(query)
    }

    fun findInPageNext() {
        val activeId = _uiState.value.activeTabId
        webViewMap[activeId]?.findNext(true)
    }

    fun findInPagePrev() {
        val activeId = _uiState.value.activeTabId
        webViewMap[activeId]?.findNext(false)
    }

    // Bookmark / History View/Edit Options
    fun toggleBookmarkActive() {
        val activeId = _uiState.value.activeTabId
        val currTab = _uiState.value.tabs.find { it.id == activeId } ?: return
        val url = currTab.url
        val title = currTab.title
        viewModelScope.launch {
            if (repository.isBookmarked(url)) {
                repository.removeBookmark(url)
            } else {
                repository.addBookmark(url, title)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun addCustomShortcut(url: String, title: String) {
        viewModelScope.launch {
            repository.addCustomShortcut(formatUrlOrSearch(url), title)
        }
    }

    fun removeTopSite(topSite: TopSite) {
        viewModelScope.launch {
            if (topSite.isCustom) {
                repository.deleteCustomShortcut(topSite.url)
            } else {
                repository.hideTopSite(topSite.url)
            }
        }
    }

    // SharedPreferences Settings
    fun toggleJavaScript(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setJavaScriptEnabled(enabled)
        }
    }

    fun toggleHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setHardwareAccelerationEnabled(enabled)
        }
    }

    fun setNewTabWallpaper(wallpaper: String) {
        viewModelScope.launch {
            prefs.setNewTabWallpaper(wallpaper)
        }
    }

    // Web Notification and Permission Helper Methods
    fun showNotificationPermissionDialog(origin: String) {
        val currentOriginStatus = permissionEngine.getPermissionState(origin, "notifications")
        if (currentOriginStatus == "Ask") {
            _notificationRequestOrigin.value = origin
        }
    }

    fun handleNotificationPermissionResponse(origin: String, allowed: Boolean) {
        permissionEngine.setPermissionState(origin, "notifications", if (allowed) "Allow" else "Block")
        _notificationRequestOrigin.value = null
        if (allowed) {
            triggerWebNotification(origin, "🔔 Notifications Allowed", "You will now receive notifications from $origin.")
        }
    }

    fun triggerWebNotification(origin: String, title: String, body: String) {
        val status = permissionEngine.getPermissionState(origin, "notifications")
        // Check default if not set
        val defaultGlobal = prefs.getString("site_perm_default/notifications", "Ask")
        val isGranted = if (status != "Ask") status == "Allow" else defaultGlobal == "Allow" || defaultGlobal == "Ask"
        if (!isGranted) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val context = getApplication<Application>()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channelId = "website_notifications_channel"

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        channelId,
                        "Website Push Notifications",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications received from websites"
                        enableLights(true)
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val clickIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                    action = android.content.Intent.ACTION_VIEW
                    putExtra("NOTIFICATION_URL", if (origin.startsWith("http")) origin else "https://$origin")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = android.app.PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), clickIntent, pendingFlags)

                val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSubText(origin)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun applyDarkReaderLiveFilters() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId]
        if (webView != null) {
            val enabled = prefs.getBoolean("ext_dark_reader", false)
            if (enabled) {
                val brightness = prefs.getInt("ext_dark_reader_brightness", 90)
                val contrast = prefs.getInt("ext_dark_reader_contrast", 100)
                val sepia = prefs.getInt("ext_dark_reader_sepia", 0)
                val grayscale = prefs.getInt("ext_dark_reader_grayscale", 0)
                val filterString = "invert(90%) hue-rotate(180deg) brightness($brightness%) contrast($contrast%) sepia($sepia%) grayscale($grayscale%)"
                val js = """
                    (function() {
                        var css = 'html { filter: $filterString !important; background: #121212 !important; } img, video, iframe, canvas, [style*="background-image"] { filter: invert(100%) hue-rotate(180deg) !important; }';
                        var style = document.getElementById('dark-reader-ext');
                        if (style) {
                            style.innerHTML = css;
                        } else {
                            style = document.createElement('style');
                            style.id = 'dark-reader-ext';
                            style.type = 'text/css';
                            style.appendChild(document.createTextNode(css));
                            document.head.appendChild(style);
                        }
                    })()
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            } else {
                val js = "var el = document.getElementById('dark-reader-ext'); if(el) el.remove();"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    // Chrome Extension manager helper methods
    fun evaluateJavascriptOnActiveWebview(jsCode: String, onResult: ((String) -> Unit)? = null) {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        webView.post {
            webView.evaluateJavascript(jsCode) { result ->
                onResult?.invoke(result ?: "")
            }
        }
    }

    fun isExtensionEnabled(key: String): Boolean {
        return prefs.getBoolean(key, key == "ext_adblock" || key == "ext_dark_reader" || key == "ext_grok_automation")
    }

    fun setExtensionEnabled(key: String, enabled: Boolean) {
        prefs.setBoolean(key, enabled)

        if (key == "ext_dark_reader") {
            applyDarkReaderLiveFilters()
        }
    }

    fun getCustomExtensionScript(): String {
        return prefs.getString("ext_custom_script_code", "")
    }

    fun setCustomExtensionScript(code: String) {
        prefs.setString("ext_custom_script_code", code)
    }

    fun getUploadedExtensionName(): String {
        return prefs.getString("ext_uploaded_name", "No uploaded ZIP extension")
    }

    fun uninstallUploadedExtension() {
        val uploadedId = prefs.getString("ext_uploaded_id", "")
        prefs.setString("ext_uploaded_name", "No uploaded ZIP extension")
        prefs.setString("ext_uploaded_id", "")
        prefs.setString("ext_uploaded_script_code", "")
        prefs.setBoolean("ext_uploaded_script_enabled", false)
        
        if (uploadedId.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    extensionManager.uninstallExtension(uploadedId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getInstalledDbExtensions(): List<com.example.extensionengine.ParsedExtension> {
        return extensionManager.engine.registry.getAllActiveExtensions()
    }

    fun uninstallDbExtension(id: String) {
        viewModelScope.launch {
            try {
                extensionManager.uninstallExtension(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportExtension(extensionId: String, context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dbExt = extensionManager.engine.database.extensionDao().getExtensionById(extensionId)
                val extName = dbExt?.name?.replace("[^a-zA-Z0-9]".toRegex(), "_") ?: "extension"
                val extensionDir = com.example.extensionengine.ExtensionDirectoryResolver.getExtensionDir(context, extensionId, dbExt?.name)
                if (!extensionDir.exists() || !extensionDir.isDirectory) {
                    throw Exception("Extension source files do not exist.")
                }
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && !downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                
                val zipFileName = "${extName}_${extensionId}_export.zip"
                val destinationZipFile = java.io.File(downloadsDir, zipFileName)
                
                java.io.FileOutputStream(destinationZipFile).use { fos ->
                    java.util.zip.ZipOutputStream(fos).use { zos ->
                        zipDirectory(extensionDir, extensionDir, zos)
                    }
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(true, "Exported successfully to Downloads/$zipFileName")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun shareExtension(extensionId: String, context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = context.applicationContext
            try {
                val dbExt = extensionManager.engine.database.extensionDao().getExtensionById(extensionId)
                val extName = dbExt?.name?.replace("[^a-zA-Z0-9]".toRegex(), "_") ?: "extension"
                val extensionDir = com.example.extensionengine.ExtensionDirectoryResolver.getExtensionDir(app, extensionId, dbExt?.name)
                
                if (!extensionDir.exists() || !extensionDir.isDirectory) {
                    throw Exception("Extension files not found.")
                }
                
                // Save zip in external files directory to match file_paths provider config
                val shareFile = java.io.File(app.getExternalFilesDir(null), "share_${extName}_$extensionId.zip")
                if (shareFile.exists()) shareFile.delete()
                
                java.io.FileOutputStream(shareFile).use { fos ->
                    java.util.zip.ZipOutputStream(fos).use { zos ->
                        zipDirectory(extensionDir, extensionDir, zos)
                    }
                }
                
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.provider",
                    shareFile
                )
                
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Share Extension: ${dbExt?.name ?: extensionId}")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Here is the Chrome Extension: ${dbExt?.name ?: extensionId} (from Orion Browser)")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooserIntent = android.content.Intent.createChooser(intent, "Share Extension via").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                app.startActivity(chooserIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(app, "Share failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun zipDirectory(rootFolder: java.io.File, sourceFolder: java.io.File, zos: java.util.zip.ZipOutputStream) {
        val files = sourceFolder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(rootFolder, file, zos)
            } else {
                val relativePath = file.absolutePath.substring(rootFolder.absolutePath.length + 1)
                val entry = java.util.zip.ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    fun loadExtensionFromZip(context: Context, uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val parsed = extensionManager.installExtension(uri)
                prefs.setString("ext_uploaded_id", parsed.id)
                prefs.setString("ext_uploaded_name", parsed.name)
                prefs.setBoolean("ext_uploaded_script_enabled", true)
                onResult(true, "Installed '${parsed.name}' successfully!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Failed to load extension: ${e.localizedMessage}")
            }
        }
    }

    fun downloadChromeExtension(context: Context, extensionId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var downloadSuccess = false
            var errorMsg = ""
            val app = context.applicationContext
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(app, "Connecting to Chrome Web Store...", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            val tempFile = java.io.File(app.cacheDir, "temp_webstore_${extensionId}.crx")
            if (tempFile.exists()) {
                tempFile.delete()
            }
            
            // Try url 1
            val urls = listOf(
                "https://clients2.google.com/service/update2/crx?response=redirect&acceptformat=crx2,crx3&prodversion=114.0&x=id%3D${extensionId}%26installsource%3Dondemand%26uc",
                "https://clients2.google.com/service/update2/crx?response=redirect&os=win&arch=x86-64&nacl_arch=x86-64&prod=chromecrx&prodchannel=stable&prodversion=114.0&acceptformat=crx2,crx3&x=id%3D${extensionId}%26uc"
            )
            
            for (url in urls) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                        .build()
                    
                    okHttpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body
                            if (body != null) {
                                val contentLength = body.contentLength()
                                val inputStream = body.byteStream()
                                val output = java.io.FileOutputStream(tempFile)
                                
                                val buffer = ByteArray(8192)
                                var bytesRead: Long = 0
                                var lastProgress = -1
                                
                                output.use { fos ->
                                    inputStream.use { fis ->
                                        var count = fis.read(buffer)
                                        while (count != -1) {
                                            fos.write(buffer, 0, count)
                                            bytesRead += count
                                            
                                            if (contentLength > 0) {
                                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                                if (progress != lastProgress && (progress % 20 == 0 || progress == 100)) {
                                                    lastProgress = progress
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        android.widget.Toast.makeText(app, "Downloading: $progress%", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            count = fis.read(buffer)
                                        }
                                    }
                                }
                                
                                if (contentLength > 0 && bytesRead != contentLength) {
                                    tempFile.delete()
                                    errorMsg = "Partial download: got $bytesRead of $contentLength"
                                } else if (bytesRead == 0L) {
                                    tempFile.delete()
                                    errorMsg = "Downloaded file was empty"
                                } else {
                                    downloadSuccess = true
                                }
                            } else {
                                errorMsg = "Null response body"
                            }
                        } else {
                            errorMsg = "HTTP ${response.code}"
                        }
                    }
                    if (downloadSuccess) break
                } catch (e: Exception) {
                    errorMsg = e.localizedMessage ?: "Unknown network error"
                }
            }
            
            if (!downloadSuccess || !tempFile.exists()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Download failed: $errorMsg")
                }
                return@launch
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val parsed = extensionManager.installExtension(Uri.fromFile(tempFile))
                    prefs.setString("ext_uploaded_id", parsed.id)
                    prefs.setString("ext_uploaded_name", parsed.name)
                    prefs.setBoolean("ext_uploaded_script_enabled", true)
                    
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    onResult(true, "Installed '${parsed.name}' from Web Store!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(false, "Failed to load unpacked extension: ${e.localizedMessage}")
                }
            }
        }
    }

    // Dynamic checks
    fun clearWebViewCache(context: Context) {
        // Clear all web cache
        try {
            val wv = WebView(context)
            wv.clearCache(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Also clear OkHttp cache
        try {
            okHttpClient.cache?.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exitFullscreen() {
        _fullscreenState.value?.callback?.onCustomViewHidden()
        _fullscreenState.value = null
    }

    fun getMemoryCacheSizeInBytes(context: Context): Long {
        // Approximate Cache Directory size
        val cacheDirOrion = File(context.cacheDir, "http_cache_orion")
        return getFolderSize(cacheDirOrion) + getFolderSize(File(context.cacheDir, "webview"))
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    size += getFolderSize(child)
                }
            }
        } else if (file.exists()) {
            size += file.length()
        }
        return size
    }

    fun safeLoadUrl(webView: android.webkit.WebView?, url: String) {
        if (webView == null) return
        var trimmed = url.trim()
        if (trimmed.isEmpty()) return

        // Mobile layout enforcement
        val activeId = _uiState.value.activeTabId
        val tab = _uiState.value.tabs.find { it.id == activeId }
        val isDesktop = tab?.isDesktopMode == true
        if (!isDesktop && !trimmed.startsWith("orion://") && !trimmed.startsWith("about:") && !trimmed.contains("translate.google.com")) {
            trimmed = enforceMobileUrl(trimmed)
        }
        
        val finalUrl = trimmed
        webView.post {
            try {
                webView.loadUrl(finalUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getUrlHost(url: String): String {
        return com.example.adblockengine.AdBlocker.getDomainName(url) ?: ""
    }

    fun triggerTranslationSelection() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        val currentUrl = webView.url ?: ""
        if (currentUrl.isEmpty() || currentUrl.startsWith("orion://") || currentUrl.startsWith("about:")) return

        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Visible)

        _uiState.update { 
            it.copy(
                showTranslateBar = true,
                originalTranslationUrl = if (!it.isPageTranslated) currentUrl else it.originalTranslationUrl
            )
        }
    }

    fun dismissTranslateBar() {
        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Hidden)
        _uiState.update { it.copy(showTranslateBar = false) }
    }

    fun undoTranslation() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Original)
        _uiState.update { 
            it.copy(
                isPageTranslated = false,
                showTranslateBar = true
            )
        }
        com.example.translateengine.DomRestoreEngine.restoreOriginal(webView) { res ->
            android.util.Log.d("BrowserViewModel", "DOM restoration finished! Result: $res")
        }
    }

    fun executeGoogleTranslation(langCode: String, langName: String) {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        val currentUrl = webView.url ?: ""
        if (currentUrl.isEmpty() || currentUrl.startsWith("orion://") || currentUrl.startsWith("about:")) return

        val originalUrl = if (!_uiState.value.isPageTranslated) currentUrl else _uiState.value.originalTranslationUrl
        
        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Translating)
        _uiState.update { 
            it.copy(
                isPageTranslated = true,
                translateTargetLang = langName,
                translateTargetLangCode = langCode,
                originalTranslationUrl = originalUrl,
                showTranslateBar = true
            )
        }

        val activeTab = _uiState.value.tabs.find { it.id == activeId }
        val isDesktop = activeTab?.isDesktopMode == true

        // Call our advanced background translation manager
        translateManager.translateWebView(webView, langCode, activeId, isDesktop) { count ->
            android.util.Log.d("BrowserViewModel", "Page translation finished! Injected $count nodes.")
            translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Translated)
        }
    }

    fun translateActivePage(targetLangCode: String) {
        val languagesMap = mapOf(
            "hi" to "Hindi",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "en" to "English",
            "ja" to "Japanese",
            "ar" to "Arabic",
            "bn" to "Bengali",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "zh-CN" to "Chinese Simplified",
            "ur" to "Urdu",
            "tr" to "Turkish",
            "te" to "Telugu",
            "mr" to "Marathi",
            "ta" to "Tamil",
            "gu" to "Gujarati",
            "kn" to "Kannada",
            "ml" to "Malayalam",
            "pa" to "Punjabi",
            "or" to "Odia"
        )
        val langName = languagesMap[targetLangCode] ?: targetLangCode
        executeGoogleTranslation(targetLangCode, langName)
    }

    private fun getSelectedSearchEngine(): String {
        return prefs.getString("default_search_engine", "Google")
    }

    private fun getSearchEngineUrl(engineName: String): String {
        return when (engineName) {
            "Google" -> "https://www.google.com/search?q={query}"
            "Bing" -> "https://www.bing.com/search?q={query}"
            "DuckDuckGo" -> "https://duckduckgo.com/?q={query}"
            "Yahoo" -> "https://search.yahoo.com/search?p={query}"
            "Brave" -> "https://search.brave.com/search?q={query}"
            "ChatGPT" -> "https://chatgpt.com/?q={query}"
            "Claude (Anthropic)" -> "https://claude.ai/search?q={query}"
            "Perplexity AI" -> "https://www.perplexity.ai/search?q={query}"
            "Ecosia" -> "https://www.ecosia.org/search?q={query}"
            "Yandex" -> "https://yandex.com/search/?text={query}"
            "Startpage" -> "https://www.startpage.com/search?q={query}"
            "Qwant" -> "https://www.qwant.com/?q={query}"
            else -> "https://www.google.com/search?q={query}"
        }
    }

    fun processInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "about:blank"
        
        // Check if it's a local or internal URI
        if (trimmed.startsWith("file://") || trimmed.startsWith("content://") || trimmed.startsWith("about:") || trimmed.startsWith("orion://")) {
            return trimmed
        }
        
        // Check if it's a valid URL or IP address
        val webUrlPattern = android.util.Patterns.WEB_URL
        val isUrl = webUrlPattern.matcher(trimmed).matches() || 
                    (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3)
                    
        if (isUrl) {
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
        
        // If not a URL, treat it as a search query
        return buildSearchUrl(trimmed)
    }

    fun buildSearchUrl(query: String): String {
        val defaultEngine = getSelectedSearchEngine()
        val queryEncoded = java.net.URLEncoder.encode(query, "UTF-8")
        val urlTemplate = getSearchEngineUrl(defaultEngine)
        return urlTemplate.replace("{query}", queryEncoded)
    }

    private fun formatUrlOrSearch(input: String): String {
        val trimmed = input.trim()
        if (trimmed == "orion://newtab" || trimmed == "orion://newtab-incognito") return trimmed
        return processInput(trimmed)
    }

    private fun captureWebViewScreenshot(webView: WebView): Bitmap? {
        return try {
            val width = webView.width.coerceAtLeast(1)
            val height = webView.height.coerceAtLeast(1)

            val maxW = 300
            val maxH = 500
            val scale = (maxW.toFloat() / width).coerceAtMost(maxH.toFloat() / height).coerceAtMost(1f)

            val scaledW = (width * scale).toInt().coerceAtLeast(1)
            val scaledH = (height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.draw(canvas)

            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    // Navigation UI panels
    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun setHistoryOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isHistoryOpen = isOpen) }
    }

    fun setBookmarksOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isBookmarksOpen = isOpen) }
    }

    fun setDownloadsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDownloadsOpen = isOpen) }
    }

    fun openLocalFile(filePath: String, fileName: String, mimeType: String) {
        _uiState.update {
            it.copy(
                activeViewerFile = ActiveViewerFile(filePath, fileName, mimeType),
                isDownloadsOpen = false
            )
        }
    }

    fun closeLocalFile() {
        stopBackgroundAudio()
        _uiState.update {
            it.copy(activeViewerFile = null)
        }
    }

    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch {
            try {
                val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                dm.remove(downloadId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.deleteDownloadFromDb(downloadId)
        }
    }

    fun hasTabHistory(): Boolean {
        val currentTabIds = _uiState.value.tabs.map { it.id }
        tabHistory.removeAll { it !in currentTabIds }
        return tabHistory.isNotEmpty()
    }

    fun goToHomepage() {
        val activeId = _uiState.value.activeTabId
        val tab = _uiState.value.tabs.find { it.id == activeId }
        val isIncognito = tab?.isIncognito == true

        val type = prefs.getString("homepage_type", "ntp")
        val targetUrl = if (type == "custom") {
            prefs.getString("homepage_custom_url", "https://www.google.com")
        } else {
            if (isIncognito) "orion://newtab-incognito" else "orion://newtab"
        }
        navigateTo(targetUrl)
    }

    fun showContextMenu(url: String, isImage: Boolean, isImageLink: Boolean = false, imageUrl: String = "") {
        _uiState.update {
            it.copy(
                contextMenuState = ContextMenuState(
                    show = true,
                    url = url,
                    isImage = isImage,
                    isImageLink = isImageLink,
                    imageUrl = imageUrl
                )
            )
        }
    }

    fun dismissContextMenu() {
        _uiState.update {
            it.copy(contextMenuState = ContextMenuState())
        }
    }

    fun addBookmarkExternally(url: String, title: String) {
        viewModelScope.launch {
            repository.addBookmark(url, title)
        }
    }

    fun handleBackNavigation(onShowExitToast: () -> Unit): Boolean {
        val currentState = _uiState.value
        
        // 1. If active local file viewer is open
        if (currentState.activeViewerFile != null) {
            closeLocalFile()
            return true
        }
        
        // 2. If settings overlay is open
        if (currentState.isSettingsOpen) {
            return false // Let nested settings handle it
        }
        
        // 2. If tab switcher is open
        if (currentState.isTabSwitcherOpen) {
            setTabSwitcherOpen(false)
            return true
        }

        // 3. If downloads list is open
        if (currentState.isDownloadsOpen) {
            setDownloadsOpen(false)
            return true
        }

        // 4. If bookmarks overlay is open
        if (currentState.isBookmarksOpen) {
            setBookmarksOpen(false)
            return true
        }

        // 5. If history overlay is open
        if (currentState.isHistoryOpen) {
            setHistoryOpen(false)
            return true
        }

        // 6. If find in page is active
        if (currentState.findInPageActive) {
            toggleFindInPage(false)
            return true
        }

        // 6.5. If reader mode is active, close reader mode
        if (currentState.readerModeActive) {
            closeReaderMode()
            return true
        }

        // 7. If current web tab can go back in WebView history
        val activeId = currentState.activeTabId
        val webView = webViewMap[activeId]
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
            return true
        }

        // 7.5. Switch to parent tab if this was opened as a child tab from a link click
        val currentTab = currentState.tabs.find { it.id == activeId }
        val parentId = currentTab?.parentTabId
        if (parentId != null && currentState.tabs.any { it.id == parentId }) {
            selectTab(parentId)
            closeTab(activeId)
            return true
        }
        
        // 8. If we have a previously active tab in tab history, switch back to it
        val currentTabIds = currentState.tabs.map { it.id }
        tabHistory.removeAll { it !in currentTabIds }
        if (tabHistory.isNotEmpty()) {
            val previousTabId = tabHistory.removeAt(tabHistory.size - 1)
            
            val oldTabId = currentState.activeTabId
            
            // 1. Switch back state instantly
            _uiState.update { state ->
                val updatedTabs = state.tabs.map {
                    if (it.id == previousTabId) it.copy(lastActiveTime = System.currentTimeMillis()) else it
                }
                val activeTab = updatedTabs.find { it.id == previousTabId }
                state.copy(
                    tabs = updatedTabs,
                    activeTabId = previousTabId,
                    currentInputUrl = if (activeTab?.url == "orion://newtab") "" else (activeTab?.url ?: ""),
                    isTabSwitcherOpen = false,
                    readerModeActive = false,
                    findInPageActive = false
                )
            }
            
            // 2. Resume newly active WebView instantly
            val activeWebView = webViewMap[previousTabId]
            activeWebView?.onResume()

            // 3. Capture screen of previous active WebView and pause asynchronously
            val prevWebView = webViewMap[oldTabId]
            if (prevWebView != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    try {
                        val bmp = captureWebViewScreenshot(prevWebView)
                        _uiState.update { state ->
                            state.copy(
                                tabs = state.tabs.map {
                                    if (it.id == oldTabId) it.copy(screenshot = bmp ?: it.screenshot) else it
                                }
                            )
                        }
                        prevWebView.onPause()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            preloadAdjacentTabs()
            return true
        }
        
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime > 2000) {
            lastBackPressTime = now
            onShowExitToast()
            return true
        }
        
        return false // Let system do default back navigation
    }

    fun setTabSwitcherOpen(isOpen: Boolean) {
        _uiState.update { state ->
            if (isOpen) {
                // Capture current tab screenshot on open switcher
                val activeId = state.activeTabId
                val activeWebView = webViewMap[activeId]
                val bmp = activeWebView?.let { captureWebViewScreenshot(it) }
                state.copy(
                    isTabSwitcherOpen = true,
                    tabs = state.tabs.map {
                        if (it.id == activeId) it.copy(screenshot = bmp ?: it.screenshot) else it
                    }
                )
            } else {
                state.copy(isTabSwitcherOpen = false)
            }
        }
    }

    fun setInputUrlAndQuery(input: String) {
        _uiState.update { it.copy(currentInputUrl = input) }
        
        suggestionFetchJob?.cancel()
        if (input.trim().isEmpty()) {
            _uiState.update { it.copy(searchSuggestions = emptyList()) }
            return
        }

        suggestionFetchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(200)

            // Memory Cache check
            val cached = suggestionMemoryCache[input]
            if (cached != null) {
                _uiState.update { it.copy(searchSuggestions = cached) }
                return@launch
            }

            // Local History
            val historyResults = repository.searchHistory(input, 3).map {
                SearchSuggestion(SuggestionType.HISTORY, it.title, it.url)
            }

            // Local Bookmarks
            val bookmarkResults = repository.searchBookmarks(input, 2).map {
                SearchSuggestion(SuggestionType.BOOKMARK, it.title, it.url)
            }

            // Search engine API fetch
            val engine = getSelectedSearchEngine()
            val apiUrl = getSuggestionApiUrl(engine, input)
            val searchResults = if (!apiUrl.isNullOrEmpty()) {
                fetchSearchSuggestions(apiUrl, engine, input)
            } else {
                emptyList()
            }

            val merged = (historyResults + bookmarkResults + searchResults).take(9)
            suggestionMemoryCache[input] = merged
            _uiState.update { it.copy(searchSuggestions = merged) }
        }
    }

    private fun getSuggestionApiUrl(engineName: String, query: String): String? {
        val encoded = try { java.net.URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        return when (engineName) {
            "Google" -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            "Bing" -> "https://api.bing.com/osjson.aspx?query=$encoded"
            "DuckDuckGo" -> "https://duckduckgo.com/ac/?q=$encoded"
            "Yahoo" -> "https://ff.search.yahoo.com/gossip?output=fxjson&command=$encoded"
            else -> null
        }
    }

    private fun fetchSearchSuggestions(url: String, engineName: String, query: String): List<SearchSuggestion> {
        val request = okhttp3.Request.Builder().url(url).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()

                if (engineName == "DuckDuckGo") {
                    val jsonArray = org.json.JSONArray(bodyString)
                    val suggestions = mutableListOf<SearchSuggestion>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val phrase = obj.optString("phrase")
                        if (!phrase.isNullOrEmpty()) {
                            suggestions.add(SearchSuggestion(SuggestionType.SEARCH, phrase))
                        }
                    }
                    return suggestions.take(5)
                } else {
                    val jsonArray = org.json.JSONArray(bodyString)
                    if (jsonArray.length() > 1) {
                        val innerArray = jsonArray.getJSONArray(1)
                        val suggestions = mutableListOf<SearchSuggestion>()
                        for (i in 0 until innerArray.length()) {
                            val value = innerArray.optString(i)
                            if (!value.isNullOrEmpty()) {
                                suggestions.add(SearchSuggestion(SuggestionType.SEARCH, value))
                            }
                        }
                        return suggestions.take(5)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    fun setSearchFocused(focused: Boolean) {
        _uiState.update { it.copy(isSearchFocused = focused) }
    }

    fun setToolbarsVisible(visible: Boolean) {
        if (_uiState.value.areToolbarsVisible != visible) {
            _uiState.update { it.copy(areToolbarsVisible = visible) }
        }
    }

    fun toggleToolbarsVisible() {
        _uiState.update { it.copy(areToolbarsVisible = !_uiState.value.areToolbarsVisible) }
    }

    fun toggleDesktopMode() {
        val activeId = _uiState.value.activeTabId
        if (activeId.isEmpty()) return
        val currentTab = _uiState.value.tabs.find { it.id == activeId } ?: return
        val webView = webViewMap[activeId] ?: return
        val currentUrl = webView.url ?: currentTab.url
        if (currentUrl.isEmpty() || currentUrl.startsWith("orion://") || currentUrl.startsWith("about:")) {
            val newMode = !currentTab.isDesktopMode
            updateTabState(activeId) {
                it.copy(isDesktopMode = newMode)
            }
            webView.settings.userAgentString = if (newMode) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
            }
            return
        }

        val domain = Uri.parse(currentUrl).host ?: return
        val currentMode = domainDesktopMap[domain] ?: false
        val newMode = !currentMode
        domainDesktopMap[domain] = newMode

        // Save to SharedPreferences
        val sharedPrefs = getApplication<Application>().getSharedPreferences("desktop_mode_sites", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("desktop_mode_$domain", newMode).apply()

        isUASwitchPending = true
        lastManualLoadUrl = currentUrl

        webView.settings.userAgentString = if (newMode) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
        }

        updateTabState(activeId) {
            it.copy(isDesktopMode = newMode)
        }

        // Smart redirect to mobile or desktop URL depending on the toggle
        val redirectUrl = if (newMode) enforceDesktopUrl(currentUrl) else enforceMobileUrl(currentUrl)

        if (redirectUrl != currentUrl) {
            webView.loadUrl(redirectUrl)
        } else {
            webView.reload()
        }
    }

    private var feedJob: kotlinx.coroutines.Job? = null

    fun changeFeedCategory(category: String) {
        _uiState.update { it.copy(feedCategory = category, articles = emptyList()) }
        loadArticlesForCategory(category, forceRefresh = false)
    }

    fun refreshArticles() {
        loadArticlesForCategory(_uiState.value.feedCategory, forceRefresh = true)
    }

    fun loadArticlesForCategory(category: String, forceRefresh: Boolean = false) {
        feedJob?.cancel()
        feedJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isFeedLoading = true) }
            
            try {
                repository.getArticlesByCategory(category).first().let { cached ->
                    if (cached.isNotEmpty()) {
                        _uiState.update { it.copy(articles = cached) }
                        
                        val isFresh = (System.currentTimeMillis() - cached.first().cachedAt) < 30 * 60 * 1000L
                        if (isFresh && !forceRefresh) {
                            _uiState.update { it.copy(isFeedLoading = false) }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val feedUrl = when (category) {
                "For You" -> "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
                "Top Stories" -> "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
                "India" -> "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"
                "Technology" -> "https://news.google.com/rss/search?q=technology&hl=en-IN&gl=IN&ceid=IN:en"
                "Sports" -> "https://news.google.com/rss/search?q=sports&hl=en-IN&gl=IN&ceid=IN:en"
                "Entertainment" -> "https://news.google.com/rss/search?q=entertainment&hl=en-IN&gl=IN&ceid=IN:en"
                "Business" -> "https://news.google.com/rss/search?q=business&hl=en-IN&gl=IN&ceid=IN:en"
                "Science" -> "https://news.google.com/rss/search?q=science&hl=en-IN&gl=IN&ceid=IN:en"
                else -> "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
            }

            val fetched = com.example.network.RssFeedParser.fetchAndParseRss(okHttpClient, feedUrl, category)
            if (fetched.isNotEmpty()) {
                repository.clearArticlesByCategory(category)
                repository.saveArticles(fetched)
                _uiState.update { it.copy(articles = fetched, isFeedLoading = false) }
            } else {
                _uiState.update { it.copy(isFeedLoading = false) }
            }
        }
    }

    fun printCurrentPage(context: Context) {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("SwiftBrowser Document")
            printManager.print("SwiftBrowser Print Job", printAdapter, android.print.PrintAttributes.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Print failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Ad blocker counters & preference bindings
    fun incrementBlockedAdsCount(tabId: String) {
        updateTabState(tabId) {
            it.copy(blockedAdsCount = it.blockedAdsCount + 1)
        }
    }

    fun setGlobalAdBlockEnabled(enabled: Boolean) {
        com.example.adblockengine.AdBlocker.globalAdBlockEnabled = enabled
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update { it.copy(globalAdBlockEnabled = enabled) }
    }

    fun setGlobalTrackersEnabled(enabled: Boolean) {
        com.example.adblockengine.AdBlocker.globalTrackersEnabled = enabled
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update { it.copy(globalTrackersEnabled = enabled) }
    }

    fun setYoutubeAdSkipEnabled(enabled: Boolean) {
        com.example.adblockengine.AdBlocker.youtubeAdSkipEnabled = enabled
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update { it.copy(youtubeAdSkipEnabled = enabled) }
    }

    fun toggleAdBlockForSite(url: String) {
        val domain = com.example.adblockengine.AdBlocker.getDomainName(url) ?: return
        if (com.example.adblockengine.AdBlocker.whitelistedSites.contains(domain)) {
            com.example.adblockengine.AdBlocker.whitelistedSites.remove(domain)
        } else {
            com.example.adblockengine.AdBlocker.whitelistedSites.add(domain)
        }
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(
                adblockWhitelist = com.example.adblockengine.AdBlocker.whitelistedSites.toSet()
            )
        }
    }

    fun addWhitelistedSite(domain: String) {
        if (domain.isBlank()) return
        val clean = domain.trim().lowercase().removePrefix("www.")
        com.example.adblockengine.AdBlocker.whitelistedSites.add(clean)
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockWhitelist = com.example.adblockengine.AdBlocker.whitelistedSites.toSet())
        }
    }

    fun removeWhitelistedSite(domain: String) {
        com.example.adblockengine.AdBlocker.whitelistedSites.remove(domain)
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockWhitelist = com.example.adblockengine.AdBlocker.whitelistedSites.toSet())
        }
    }

    fun addBlockedSite(domain: String) {
        if (domain.isBlank()) return
        val clean = domain.trim().lowercase().removePrefix("www.")
        com.example.adblockengine.AdBlocker.blockedSites.add(clean)
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockBlacklist = com.example.adblockengine.AdBlocker.blockedSites.toSet())
        }
    }

    fun removeBlockedSite(domain: String) {
        com.example.adblockengine.AdBlocker.blockedSites.remove(domain)
        com.example.adblockengine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockBlacklist = com.example.adblockengine.AdBlocker.blockedSites.toSet())
        }
    }

    fun updateAdBlockerRulesList() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isFeedLoading = true) }
                val size = com.example.adblockengine.AdBlocker.downloadBlocklists(getApplication())
                android.widget.Toast.makeText(getApplication(), "Blocklists updated! $size rules downloaded.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication(), "Failed to update blocklists: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isFeedLoading = false) }
            }
        }
    }

    // TTS Control Methods
    private fun setupTtsProgressListener() {
        ttsEngine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    playNextTtsSegment()
                }
            }

            override fun onError(utteranceId: String?) {}
        })
    }

    fun playTtsSegment(index: Int) {
        if (index < 0 || index >= ttsSegments.size) {
            stopTtsPlayback()
            return
        }
        currentTtsSegmentIndex = index
        val textToSpeak = ttsSegments[index]
        
        _uiState.update { 
            it.copy(
                currentTtsText = textToSpeak,
                currentTtsIndex = index,
                isTtsActive = true,
                isTtsPlaying = true
            ) 
        }
        
        ttsEngine?.setSpeechRate(_uiState.value.ttsSpeed)
        val params = android.os.Bundle()
        params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "segment_$index")
        ttsEngine?.speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "segment_$index")
    }

    fun playNextTtsSegment() {
        if (currentTtsSegmentIndex + 1 < ttsSegments.size) {
            playTtsSegment(currentTtsSegmentIndex + 1)
        } else {
            stopTtsPlayback()
        }
    }

    fun playPreviousTtsSegment() {
        if (currentTtsSegmentIndex - 1 >= 0) {
            playTtsSegment(currentTtsSegmentIndex - 1)
        } else {
            playTtsSegment(currentTtsSegmentIndex)
        }
    }

    fun startListeningToPageText() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        
        val js = """
            (function() {
                var articleContent = "";
                var mainElement = document.querySelector('article, main, .main-content, #main-content, .post, .entry-content');
                if (mainElement) { articleContent = mainElement.innerText; }
                if (!articleContent || articleContent.trim().length < 200) {
                    var paragraphs = document.querySelectorAll('p');
                    var pTexts = [];
                    paragraphs.forEach(function(p) {
                        var text = p.innerText.trim();
                        if (text.length > 20) { pTexts.push(text); }
                    });
                    articleContent = pTexts.join("\n\n");
                }
                if (!articleContent || articleContent.trim().length < 100) {
                    articleContent = document.body.innerText;
                }
                return articleContent;
            })()
        """.trimIndent()
        
        webView.evaluateJavascript(js) { result ->
            val cleanResult = if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                try {
                    org.json.JSONTokener(result).nextValue() as? String ?: result
                } catch (e: Exception) {
                    result
                }
            } else {
                result ?: ""
            }
            
            val trimmedText = cleanResult.trim()
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            
            if (trimmedText.isNotBlank() && trimmedText != "null") {
                val sentences = trimmedText.split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim().replace(Regex("\\s+"), " ") }
                    .filter { it.isNotBlank() && it.length > 3 }
                
                if (sentences.isNotEmpty()) {
                    ttsSegments = sentences
                    currentTtsSegmentIndex = 0
                    
                    _uiState.update { 
                        it.copy(
                            isTtsActive = true, 
                            isTtsPlaying = true,
                            currentTtsText = sentences[0],
                            currentTtsIndex = 0,
                            totalTtsSegments = sentences.size
                        ) 
                    }
                    
                    setupTtsProgressListener()
                    playTtsSegment(0)
                } else {
                    android.widget.Toast.makeText(getApplication(), "No read-aloud sentences found on this page.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(getApplication(), "No text content found on this webpage.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleTtsPlayback() {
        val playing = _uiState.value.isTtsPlaying
        if (playing) {
            ttsEngine?.stop()
            _uiState.update { it.copy(isTtsPlaying = false) }
        } else {
            if (ttsSegments.isNotEmpty() && currentTtsSegmentIndex in ttsSegments.indices) {
                playTtsSegment(currentTtsSegmentIndex)
            } else {
                startListeningToPageText()
            }
        }
    }

    fun stopTtsPlayback() {
        ttsEngine?.stop()
        _uiState.update { it.copy(isTtsActive = false, isTtsPlaying = false, currentTtsText = "", currentTtsIndex = 0, totalTtsSegments = 0) }
    }

    fun setTtsSpeechRate(speed: Float) {
        _uiState.update { it.copy(ttsSpeed = speed) }
        ttsEngine?.setSpeechRate(speed)
        if (_uiState.value.isTtsPlaying && ttsSegments.isNotEmpty() && currentTtsSegmentIndex in ttsSegments.indices) {
            playTtsSegment(currentTtsSegmentIndex)
        }
    }

    // Tab Grouping & Group States
    fun moveTabToGroup(tabId: String, name: String, colorValue: Long) {
        updateTabState(tabId) {
            it.copy(
                groupId = name.lowercase().trim(),
                groupName = name,
                groupColor = colorValue
            )
        }
    }

    fun removeTabFromGroup(tabId: String) {
        updateTabState(tabId) {
            it.copy(
                groupId = null,
                groupName = null,
                groupColor = null
            )
        }
    }

    // Advanced tab deletions / privately-held tabs
    fun reopenClosedTab(closedTab: TabState) {
        addNewTab(url = closedTab.url, isIncognito = closedTab.isIncognito)
        _uiState.update { state ->
            state.copy(
                recentlyClosedTabs = state.recentlyClosedTabs.filter { it.id != closedTab.id }
            )
        }
    }

    fun closeAllTabs() {
        val tabs = _uiState.value.tabs
        tabs.forEach { destroyTabWebView(it.id) }
        _uiState.update { state ->
            state.copy(tabs = emptyList(), activeTabId = "")
        }
        addNewTab() // reopen default new tab
    }

    fun closeAllIncognitoTabs() {
        val tabs = _uiState.value.tabs
        val (incognito, normal) = tabs.partition { it.isIncognito }
        incognito.forEach { destroyTabWebView(it.id) }
        _uiState.update { state ->
            state.copy(
                tabs = normal,
                activeTabId = normal.firstOrNull()?.id ?: ""
            )
        }
        if (_uiState.value.tabs.isEmpty()) {
            addNewTab()
        }
    }

    fun clearBrowsingData(
        history: Boolean,
        cookies: Boolean,
        cache: Boolean,
        timeRangeIndex: Int
    ) {
        viewModelScope.launch {
            val cutoff = when (timeRangeIndex) {
                0 -> System.currentTimeMillis() - 3600 * 1000L
                1 -> System.currentTimeMillis() - 24 * 3600 * 1000L
                2 -> System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
                3 -> System.currentTimeMillis() - 28 * 24 * 3600 * 1000L
                else -> 0L
            }
            if (history) {
                if (cutoff == 0L) {
                    repository.clearHistory()
                } else {
                    repository.deleteHistorySince(cutoff)
                }
            }
            if (cookies) {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
            }
            if (cache) {
                _uiState.value.tabs.forEach { tab ->
                    webViewMap[tab.id]?.clearCache(true)
                }
            }
            android.widget.Toast.makeText(getApplication(), "Browsing data successfully cleared", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissDownloadConfirm() {
        _uiState.update { it.copy(downloadConfirmState = DownloadConfirmState(show = false)) }
    }

    fun dismissDownloadProgress() {
        _uiState.update { it.copy(downloadProgressState = DownloadProgressState(showProgress = false)) }
    }

    fun showDownloadConfirmation(url: String, fileName: String, contentLength: Long, mimeType: String, userAgent: String) {
        _uiState.update {
            it.copy(
                downloadConfirmState = DownloadConfirmState(
                    show = true,
                    url = url,
                    fileName = fileName,
                    contentLength = contentLength,
                    mimeType = mimeType,
                    userAgent = userAgent
                )
            )
        }
    }

    fun extractFileName(disposition: String?, url: String, mimeType: String): String {
        if (!disposition.isNullOrEmpty()) {
            val match = Regex("""filename[^;=\n]*=((['"]).*?\2|[^;\n]*)""")
                .find(disposition)
            if (match != null) {
                return match.groupValues[1].trim('"', '\'', ' ')
            }
        }
        
        val urlPath = android.net.Uri.parse(url).lastPathSegment
        if (!urlPath.isNullOrEmpty() && urlPath.contains(".")) {
            return urlPath
        }
        
        val ext = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    fun startDownload(url: String, fileName: String, mimeType: String, userAgent: String, destinationDir: String = android.os.Environment.DIRECTORY_DOWNLOADS) {
        try {
            if (url.contains("youtube.com") || url.contains("youtu.be")) {
                android.widget.Toast.makeText(getApplication(), "To download YouTube videos, use YouTube Premium or a dedicated downloader app", android.widget.Toast.LENGTH_LONG).show()
                return
            }

            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading...")
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Cookie", android.webkit.CookieManager.getInstance().getCookie(url) ?: "")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(destinationDir, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            viewModelScope.launch {
                repository.saveDownloadToDb(downloadId, fileName, url, mimeType, "PENDING")
            }
            
            trackDownloadProgress(downloadId, fileName, mimeType)
            
            android.widget.Toast.makeText(getApplication(), "Downloading: $fileName", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(getApplication(), "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun trackDownloadProgress(downloadId: Long, fileName: String, mimeType: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            
            _uiState.update {
                it.copy(
                    downloadProgressState = DownloadProgressState(
                        showProgress = true,
                        fileName = fileName,
                        progress = 0,
                        downloadId = downloadId,
                        mimeType = mimeType
                    )
                )
            }

            var isDownloading = true
            while (isDownloading) {
                val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor != null && cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    val downloadedCol = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusCol >= 0 && downloadedCol >= 0 && totalCol >= 0) {
                        val status = cursor.getInt(statusCol)
                        val downloaded = cursor.getLong(downloadedCol)
                        val total = cursor.getLong(totalCol)
                        
                        when (status) {
                            android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                                isDownloading = false
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    _uiState.update {
                                        it.copy(
                                            downloadProgressState = it.downloadProgressState.copy(
                                                progress = 100,
                                                isComplete = true
                                            )
                                        )
                                    }
                                    repository.updateDownloadStatusInDb(downloadId, "COMPLETE")
                                }
                            }
                            android.app.DownloadManager.STATUS_FAILED -> {
                                isDownloading = false
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    _uiState.update {
                                        it.copy(
                                            downloadProgressState = it.downloadProgressState.copy(
                                                isFailed = true
                                            )
                                        )
                                    }
                                    repository.updateDownloadStatusInDb(downloadId, "FAILED")
                                }
                            }
                            else -> {
                                val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    _uiState.update {
                                        it.copy(
                                            downloadProgressState = it.downloadProgressState.copy(
                                                progress = progress
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                cursor?.close()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun printSavedPdf(fileName: String, tabId: String) {
        try {
            val webView = webViewMap[tabId] ?: return
            val printManager = getApplication<Application>().getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            val printAdapter = webView.createPrintDocumentAdapter(fileName)
            printManager.print(
                fileName,
                printAdapter,
                android.print.PrintAttributes.Builder().build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(getApplication(), "Failed to save PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun closeLocalViewer() {
        _uiState.update { it.copy(activeViewerFile = null) }
        stopBackgroundAudio()
    }

    // Continuous music / background playback player
    private var mediaPlayer: android.media.MediaPlayer? = null
    
    val isMediaPlaying = androidx.compose.runtime.mutableStateOf(false)
    val mediaDuration = androidx.compose.runtime.mutableIntStateOf(0)
    val mediaPosition = androidx.compose.runtime.mutableIntStateOf(0)
    val mediaTrackName = androidx.compose.runtime.mutableStateOf("")
    val isShuffleEnabled = androidx.compose.runtime.mutableStateOf(false)
    val isRepeatEnabled = androidx.compose.runtime.mutableStateOf(false)
    
    fun initAudioPlayer(filePath: String, trackName: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                isLooping = isRepeatEnabled.value
            }
            isMediaPlaying.value = true
            mediaDuration.value = mediaPlayer?.duration ?: 0
            mediaTrackName.value = trackName
            
            // Start position slider tracker
            startAudioTicker()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startAudioTicker() {
        viewModelScope.launch {
            while (isMediaPlaying.value) {
                mediaPosition.value = mediaPlayer?.currentPosition ?: 0
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    fun toggleAudioPlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isMediaPlaying.value = false
            } else {
                player.start()
                isMediaPlaying.value = true
                startAudioTicker()
            }
        }
    }
    
    fun seekAudioTo(position: Int) {
        mediaPlayer?.seekTo(position)
        mediaPosition.value = position
    }
    
    fun toggleShuffle() {
        isShuffleEnabled.value = !isShuffleEnabled.value
    }
    
    fun toggleRepeat() {
        isRepeatEnabled.value = !isRepeatEnabled.value
        mediaPlayer?.isLooping = isRepeatEnabled.value
    }
    
    fun stopBackgroundAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isMediaPlaying.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteDownloads(downloadIds: Set<Long>) {
        viewModelScope.launch {
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadIds.forEach { id ->
                try {
                    dm.remove(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                repository.deleteDownloadFromDb(id)
            }
        }
    }

    fun renameDownloadFile(downloadId: Long, oldName: String, newName: String): Boolean {
        try {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val oldFile = java.io.File(downloadDir, oldName)
            val newFile = java.io.File(downloadDir, newName)
            if (oldFile.exists() && !newFile.exists()) {
                val success = oldFile.renameTo(newFile)
                if (success) {
                    viewModelScope.launch {
                        repository.updateDownloadFileNameInDb(downloadId, newName)
                    }
                    return true
                }
            } else if (oldFile.exists() && newFile.exists()) {
                return false
            } else {
                viewModelScope.launch {
                    repository.updateDownloadFileNameInDb(downloadId, newName)
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun enforceMobileUrl(url: String): String {
        try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return url
            val newHost = when {
                (host == "youtube.com" || host == "www.youtube.com") && !uri.path.orEmpty().contains("embed") -> "m.youtube.com"
                host == "facebook.com" || host == "www.facebook.com" -> "m.facebook.com"
                host == "twitter.com" || host == "www.twitter.com" -> "mobile.twitter.com"
                (host.contains("wikipedia.org") && !host.contains(".m.wikipedia.org")) -> {
                    if (host == "wikipedia.org") {
                        "m.wikipedia.org"
                    } else {
                        val parts = host.split(".")
                        if (parts.size == 3 && parts[0] != "www" && parts[1] == "wikipedia") {
                            "${parts[0]}.m.wikipedia.org"
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }
            if (newHost != null && newHost != host) {
                var builder = uri.buildUpon().authority(newHost)
                if (newHost == "m.youtube.com" && url.contains("app=desktop")) {
                    builder = builder.clearQuery()
                    uri.queryParameterNames.forEach { name ->
                        if (name == "app") {
                            builder.appendQueryParameter(name, "m")
                        } else {
                            uri.getQueryParameters(name).forEach { value ->
                                builder.appendQueryParameter(name, value)
                            }
                        }
                    }
                }
                return builder.build().toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return url
    }

    private fun enforceDesktopUrl(url: String): String {
        try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return url
            val newHost = when {
                host == "m.youtube.com" -> "www.youtube.com"
                host == "m.facebook.com" -> "www.facebook.com"
                host == "mobile.twitter.com" -> "www.twitter.com"
                host.contains(".m.wikipedia.org") -> host.replace(".m.wikipedia.org", ".wikipedia.org")
                else -> null
            }
            if (newHost != null && newHost != host) {
                return uri.buildUpon().authority(newHost).build().toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return url
    }

    // com.example.extensionengine.BrowserDelegate implementations
    override fun queryTabs(queryInfo: org.json.JSONObject): org.json.JSONArray {
        val array = org.json.JSONArray()
        val tabs = uiState.value.tabs
        val activeId = uiState.value.activeTabId
        val filterActive = if (queryInfo.has("active")) queryInfo.optBoolean("active") else null
        
        tabs.forEach { tab ->
            val isActive = tab.id == activeId
            if (filterActive != null && isActive != filterActive) {
                return@forEach
            }
            val obj = org.json.JSONObject()
            val intId = com.example.extensionengine.TabIdMapper.getIntId(tab.id)
            obj.put("id", intId)
            obj.put("url", tab.url)
            obj.put("title", tab.title)
            obj.put("active", isActive)
            array.put(obj)
        }
        return array
    }

    override fun createTab(url: String, active: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            addNewTab(url = url)
        }
    }

    override fun removeTab(tabId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            closeTab(tabId)
        }
    }

    override fun reloadTab(tabId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val activeId = uiState.value.activeTabId
            if (tabId == activeId || tabId.isBlank()) {
                reload()
            }
        }
    }

    override fun updateTab(tabId: String, url: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val activeId = uiState.value.activeTabId
            if (tabId == activeId || tabId.isBlank()) {
                navigateTo(url)
            }
        }
    }

    override fun showNotification(title: String, message: String) {
        android.widget.Toast.makeText(getApplication(), "[$title] $message", android.widget.Toast.LENGTH_LONG).show()
    }

    override fun downloadFile(url: String, filename: String?) {
        try {
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (filename != null) {
                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                }
            }
            dm.enqueue(req)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getActiveTabId(): String? {
        return uiState.value.activeTabId
    }

    override fun executeScriptOnTab(tabId: String, code: String, callback: (String?) -> Unit) {
        val targetId = if (tabId.isBlank()) uiState.value.activeTabId else tabId
        if (targetId != null) {
            val webView = webViewMap[targetId]
            if (webView != null) {
                webView.post {
                    webView.evaluateJavascript(code) { res ->
                        callback(res)
                    }
                }
                return
            }
        }
        callback(null)
    }

    override fun onCleared() {
        super.onCleared()
        stopBackgroundAudio()
        try {
            extensionManager.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
