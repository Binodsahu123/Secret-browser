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
    val hasLoadedSuccessfully: Boolean = false
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
    val areToolbarsVisible: Boolean = true
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
    private val prefs: PreferenceManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

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
        com.example.engine.AdBlocker.init(application)
        
        // OkHttp Cache Setup (50MB)
        val cacheSize = 50 * 1024 * 1024L
        val cacheDirectory = File(application.cacheDir, "http_cache_orion")
        val httpCache = Cache(cacheDirectory, cacheSize)
        okHttpClient = OkHttpClient.Builder()
            .cache(httpCache)
            .build()

        _uiState.update {
            it.copy(
                globalAdBlockEnabled = com.example.engine.AdBlocker.globalAdBlockEnabled,
                globalTrackersEnabled = com.example.engine.AdBlocker.globalTrackersEnabled,
                youtubeAdSkipEnabled = com.example.engine.AdBlocker.youtubeAdSkipEnabled,
                adblockWhitelist = com.example.engine.AdBlocker.whitelistedSites.toSet(),
                adblockBlacklist = com.example.engine.AdBlocker.blockedSites.toSet()
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
    fun addNewTab(url: String = "orion://newtab", isIncognito: Boolean = false) {
        val tabId = UUID.randomUUID().toString()
        val formatted = if (url == "orion://newtab" && isIncognito) "orion://newtab-incognito" else formatUrlOrSearch(url)
        val newTab = TabState(
            id = tabId,
            url = formatted,
            title = if (isIncognito) "Incognito Tab" else if (formatted == "orion://newtab") "New Tab" else "Loading...",
            lastActiveTime = System.currentTimeMillis(),
            isIncognito = isIncognito
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
        if (oldTabId == tabId) return

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
                areToolbarsVisible = true
            )
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
                    
                    restoredTabs.add(
                        TabState(
                            id = id,
                            url = url,
                            title = title,
                            isDesktopMode = isDesktopMode,
                            isIncognito = false
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

        val webView = object : WebView(context) {
            private var startX = 0f
            private var startY = 0f
            private val swipeThreshold = 150f
            private val yThreshold = 100f

            override fun onWindowVisibilityChanged(visibility: Int) {
                if (visibility == View.GONE || visibility == View.INVISIBLE) {
                    super.onWindowVisibilityChanged(View.VISIBLE)
                } else {
                    super.onWindowVisibilityChanged(visibility)
                }
            }

            private var startClickTime = 0L
            private var startClickX = 0f
            private var startClickY = 0f

            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                val deltaY = t - oldt
                if (Math.abs(deltaY) > 8) {
                    if (deltaY > 0) {
                        setToolbarsVisible(false)
                    } else if (deltaY < 0) {
                        setToolbarsVisible(true)
                    }
                }
            }

            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        startClickTime = System.currentTimeMillis()
                        startClickX = event.x
                        startClickY = event.y
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val diffX = event.x - startX
                        val diffY = event.y - startY
                        
                        val clickDuration = System.currentTimeMillis() - startClickTime
                        val clickDistX = event.x - startClickX
                        val clickDistY = event.y - startClickY
                        
                        val isClick = clickDuration < 300 && Math.abs(clickDistX) < 15f && Math.abs(clickDistY) < 15f
                        
                        if (isClick) {
                            toggleToolbarsVisible()
                        } else if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > swipeThreshold && Math.abs(diffY) < yThreshold) {
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
        }.apply {
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
                        val currentDocUrl = view?.url ?: _uiState.value.tabs.find { it.id == tabId }?.url
                        if (com.example.engine.AdBlocker.shouldBlock(urlStr, currentDocUrl)) {
                            incrementBlockedAdsCount(tabId)
                            com.example.engine.AdBlocker.createEmptyResponse()
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
                                view.settings.userAgentString = if (isDesktop) {
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Safari/537.36"
                                } else {
                                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
                                }
                            }
                        }
                        val currentTabState = _uiState.value.tabs.find { it.id == tabId }
                        val isNewTabState = currentTabState?.url == "orion://newtab" || currentTabState?.url == "orion://newtab-incognito"
                        val finalUrl = if (url == "about:blank" && isNewTabState) {
                            currentTabState?.url ?: ""
                        } else {
                            url ?: (currentTabState?.url ?: "")
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
                                    currentInputUrl = if (finalUrl == "orion://newtab" || finalUrl == "orion://newtab-incognito") "" else finalUrl
                                ) 
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        super.onPageFinished(view, url)
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
                        val isNewTabState = currentTabState?.url == "orion://newtab" || currentTabState?.url == "orion://newtab-incognito"
                        val finalUrl = if (url == "about:blank" && isNewTabState) {
                            currentTabState?.url ?: ""
                        } else {
                            url ?: (currentTabState?.url ?: "")
                        }
                        val title = view?.title ?: ""
                        val isTabIncognito = _uiState.value.tabs.find { it.id == tabId }?.isIncognito == true
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

                        // Inject dynamic global scroll and click capture listener in JavaScript
                        view?.evaluateJavascript("""
                            (function() {
                                if (window._orionListenersAdded) return;
                                window._orionListenersAdded = true;
                                var isVisible = true;
                                
                                document.addEventListener('scroll', function(e) {
                                    var currentScrollY = 0;
                                    var prevScroll = e.target._lastScroll || 0;
                                    if (e.target === document || e.target === window || !e.target) {
                                        currentScrollY = window.scrollY;
                                    } else {
                                        if (typeof e.target.scrollTop === 'number') {
                                            currentScrollY = e.target.scrollTop;
                                        } else {
                                            return;
                                        }
                                    }
                                    
                                    var diff = currentScrollY - prevScroll;
                                    e.target._lastScroll = currentScrollY;
                                    
                                    if (Math.abs(diff) > 25) {
                                        if (diff > 0 && isVisible) {
                                            isVisible = false;
                                            try { OrionJS.setToolbarsVisible(false); } catch(e){}
                                        } else if (diff < 0 && !isVisible) {
                                            isVisible = true;
                                            try { OrionJS.setToolbarsVisible(true); } catch(e){}
                                        }
                                    }
                                }, { capture: true, passive: true });

                                document.addEventListener('click', function(e) {
                                    var target = e.target;
                                    if (!target) return;
                                    var tag = target.tagName ? target.tagName.toLowerCase() : '';
                                    if (tag !== 'input' && tag !== 'textarea' && tag !== 'a' && tag !== 'button' && tag !== 'video' && 
                                        !target.closest('button') && !target.closest('a') && !target.closest('input')) {
                                        try { OrionJS.toggleToolbars(); } catch(e){}
                                    }
                                }, { capture: true, passive: true });
                            })();
                        """.trimIndent(), null)

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

                        if (url != null && url != "orion://newtab" && url != "orion://newtab-incognito" && !url.startsWith("orion://") && !isTabIncognito) {
                            viewModelScope.launch {
                                repository.addHistory(url, title)
                            }
                        }

                        if (url != null && url.contains("youtube.com") && com.example.engine.AdBlocker.youtubeAdSkipEnabled) {
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
                    } catch (e: Throwable) {
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
                            val description = error?.description?.toString() ?: "Connect failed"
                            val errorType = when {
                                description.contains("disconnected", true) || description.contains("offline", true) || error?.errorCode == ERROR_CONNECT -> "offline"
                                error?.errorCode == ERROR_TIMEOUT -> "timeout"
                                else -> "offline"
                            }
                            loadErrorHtml(view, errorType, failingUrl)
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
                        loadErrorHtml(view, "ssl", failingUrl)
                        handler?.cancel()
                    } catch (e: Throwable) {
                        e.printStackTrace()
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
                    callback?.invoke(origin, true, false)
                }

                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val contextLocal = view!!.context
                    val newWebView = WebView(contextLocal)
                    
                    val newTabId = java.util.UUID.randomUUID().toString()
                    val isTabIncognito = _uiState.value.tabs.find { it.id == tabId }?.isIncognito == true
                    
                    applyWebViewSettings(
                        newWebView,
                        _uiState.value.isJavaScriptEnabled,
                        _uiState.value.isHardwareAccelerationEnabled,
                        false
                    )
                    
                    val newTab = TabState(
                        id = newTabId,
                        url = "about:blank",
                        title = "Loading...",
                        isIncognito = isTabIncognito
                    )
                    
                    webViewMap[newTabId] = newWebView
                    
                    _uiState.update { state ->
                        state.copy(
                            tabs = state.tabs + newTab,
                            activeTabId = newTabId
                        )
                    }
                    
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
            val activeId = _uiState.value.activeTabId
            webViewMap.forEach { (id, view) ->
                if (view == webView) {
                    updateTabState(id) { it.copy(readerModeAvailable = isAvailable) }
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
                var title = document.title || "";
                var author = "";
                var authorEl = document.querySelector("[class*='author'], [id*='author'], rel='author'");
                if (authorEl) author = authorEl.innerText;
                
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
                    var toRemove = clone.querySelectorAll("script, style, nav, footer, header, form, iframe, .sidebar, .ads, [class*='ads'], [id*='ads']");
                    toRemove.forEach(el => el.remove());
                    extractedHtml = clone.innerHTML;
                } else {
                    var paras = Array.from(document.querySelectorAll('p')).map(p => p.outerHTML);
                    extractedHtml = paras.join("");
                }
                
                return JSON.stringify({
                    title: title,
                    author: author,
                    html: extractedHtml
                });
            })()
            """.trimIndent()
        ) { extractedJson ->
            try {
                // The returned string from evaluateJavascript is double-escaped JSON string inside quotes
                val unescaped = extractedJson?.replace("\\\"", "\"")?.trim('\"') ?: ""
                val regexTitle = "\"title\":\"(.*?)\"".toRegex()
                val regexAuthor = "\"author\":\"(.*?)\"".toRegex()
                val regexHtml = "\"html\":\"(.*)\"".toRegex()

                val extractedTitle = regexTitle.find(unescaped)?.groups?.get(1)?.value ?: "Article"
                val extractedAuthor = regexAuthor.find(unescaped)?.groups?.get(1)?.value ?: ""
                val extractedHtml = regexHtml.find(unescaped)?.groups?.get(1)?.value
                    ?.replace("\\n", "\n")
                    ?.replace("\\t", "\t")
                    ?.replace("\\/", "/") ?: "No text content found."

                _uiState.update {
                    it.copy(
                        readerModeTitle = extractedTitle,
                        readerModeAuthor = extractedAuthor.ifBlank { null },
                        readerModeContent = extractedHtml,
                        readerModeActive = true
                    )
                }
            } catch (e: Exception) {
                // Fallback direct text if regex fails
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
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        
        webView.post {
            try {
                webView.loadUrl(trimmed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
        
        // 1. If settings overlay is open
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

        // 7. If current web tab can go back in WebView history
        val activeId = currentState.activeTabId
        val webView = webViewMap[activeId]
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
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
        if (currentUrl.isEmpty() || currentUrl.startsWith("orion://") || currentUrl.startsWith("about:")) return

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

        webView.reload()
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
        com.example.engine.AdBlocker.globalAdBlockEnabled = enabled
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update { it.copy(globalAdBlockEnabled = enabled) }
    }

    fun setGlobalTrackersEnabled(enabled: Boolean) {
        com.example.engine.AdBlocker.globalTrackersEnabled = enabled
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update { it.copy(globalTrackersEnabled = enabled) }
    }

    fun setYoutubeAdSkipEnabled(enabled: Boolean) {
        com.example.engine.AdBlocker.youtubeAdSkipEnabled = enabled
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update { it.copy(youtubeAdSkipEnabled = enabled) }
    }

    fun toggleAdBlockForSite(url: String) {
        val domain = com.example.engine.AdBlocker.getDomainName(url) ?: return
        if (com.example.engine.AdBlocker.whitelistedSites.contains(domain)) {
            com.example.engine.AdBlocker.whitelistedSites.remove(domain)
        } else {
            com.example.engine.AdBlocker.whitelistedSites.add(domain)
        }
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(
                adblockWhitelist = com.example.engine.AdBlocker.whitelistedSites.toSet()
            )
        }
    }

    fun addWhitelistedSite(domain: String) {
        if (domain.isBlank()) return
        val clean = domain.trim().lowercase().removePrefix("www.")
        com.example.engine.AdBlocker.whitelistedSites.add(clean)
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockWhitelist = com.example.engine.AdBlocker.whitelistedSites.toSet())
        }
    }

    fun removeWhitelistedSite(domain: String) {
        com.example.engine.AdBlocker.whitelistedSites.remove(domain)
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockWhitelist = com.example.engine.AdBlocker.whitelistedSites.toSet())
        }
    }

    fun addBlockedSite(domain: String) {
        if (domain.isBlank()) return
        val clean = domain.trim().lowercase().removePrefix("www.")
        com.example.engine.AdBlocker.blockedSites.add(clean)
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockBlacklist = com.example.engine.AdBlocker.blockedSites.toSet())
        }
    }

    fun removeBlockedSite(domain: String) {
        com.example.engine.AdBlocker.blockedSites.remove(domain)
        com.example.engine.AdBlocker.savePreferences(getApplication())
        _uiState.update {
            it.copy(adblockBlacklist = com.example.engine.AdBlocker.blockedSites.toSet())
        }
    }

    fun updateAdBlockerRulesList() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isFeedLoading = true) }
                val size = com.example.engine.AdBlocker.downloadBlocklists(getApplication())
                android.widget.Toast.makeText(getApplication(), "Blocklists updated! $size rules downloaded.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication(), "Failed to update blocklists: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isFeedLoading = false) }
            }
        }
    }

    // TTS Control Methods
    fun startListeningToPageText() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        webView.evaluateJavascript("document.body.innerText") { result ->
            val cleanResult = result?.trim()
                ?.removePrefix("\"")?.removeSuffix("\"")
                ?.replace("\\n", " ")?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\") ?: ""
            if (cleanResult.isNotBlank() && cleanResult != "null") {
                _uiState.update { it.copy(isTtsActive = true, isTtsPlaying = true) }
                ttsEngine?.setSpeechRate(_uiState.value.ttsSpeed)
                ttsEngine?.speak(cleanResult.take(6000), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "swift_tts")
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
            startListeningToPageText()
        }
    }

    fun stopTtsPlayback() {
        ttsEngine?.stop()
        _uiState.update { it.copy(isTtsActive = false, isTtsPlaying = false) }
    }

    fun setTtsSpeechRate(speed: Float) {
        _uiState.update { it.copy(ttsSpeed = speed) }
        ttsEngine?.setSpeechRate(speed)
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

    override fun onCleared() {
        super.onCleared()
        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
