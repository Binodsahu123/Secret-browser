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
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

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
    val blockedAdsCount: Int = 0
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
    val recentlyClosedTabs: List<TabState> = emptyList()
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

    // Favicon LRU cache (max 50 entries)
    private val faviconCache = LruCache<String, Bitmap>(50)

    // OkHttp 50MB disk cache
    private val okHttpClient: OkHttpClient

    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSites: StateFlow<List<TopSite>> = repository.getMergedTopSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var ttsEngine: android.speech.tts.TextToSpeech? = null

    init {
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

        // Add initial tab
        addNewTab()
        loadArticlesForCategory("For You", false)
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
                readerModeActive = false
            )
        }

        // Lazy preloading of adjacent tabs
        cleanupOlderWebViews()
        preloadAdjacentTabs()
    }

    fun selectTab(tabId: String) {
        val currentState = _uiState.value
        val oldTabId = currentState.activeTabId
        if (oldTabId == tabId) return

        // 1. Capture screen of previous active WebView before switching away
        val prevWebView = webViewMap[oldTabId]
        if (prevWebView != null) {
            val bmp = captureWebViewScreenshot(prevWebView)
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map {
                        if (it.id == oldTabId) it.copy(screenshot = bmp ?: it.screenshot) else it
                    }
                )
            }
            prevWebView.onPause()
        }

        // 2. Select new tab
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
                findInPageActive = false
            )
        }

        // 3. Resume the newly active tab WebView
        val activeWebView = webViewMap[tabId]
        activeWebView?.onResume()

        preloadAdjacentTabs()
    }

    fun closeTab(tabId: String) {
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

        val webView = WebView(context).apply {
            id = View.generateViewId()
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

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                try {
                    val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        setDescription("Downloading file via SwiftBrowser...")
                        setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype))
                    }
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    dm.enqueue(request)
                    android.widget.Toast.makeText(context, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
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
                    val urlStr = request?.url?.toString()
                    val currentDocUrl = view?.url ?: _uiState.value.tabs.find { it.id == tabId }?.url
                    if (com.example.engine.AdBlocker.shouldBlock(urlStr, currentDocUrl)) {
                        incrementBlockedAdsCount(tabId)
                        return com.example.engine.AdBlocker.createEmptyResponse()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    updateTabState(tabId) {
                        it.copy(
                            url = url ?: it.url,
                            isLoading = true,
                            progress = 10,
                            favicon = favicon ?: it.favicon,
                            readerModeAvailable = false,
                            blockedAdsCount = 0
                        )
                    }
                    if (_uiState.value.activeTabId == tabId) {
                        val isTabIncognito = _uiState.value.tabs.find { it.id == tabId }?.isIncognito == true
                        _uiState.update { 
                            it.copy(
                                currentInputUrl = if (url == "orion://newtab" || url == "orion://newtab-incognito") "" else (url ?: "")
                            ) 
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val title = view?.title ?: ""
                    val isTabIncognito = _uiState.value.tabs.find { it.id == tabId }?.isIncognito == true
                    updateTabState(tabId) {
                        it.copy(
                            url = url ?: it.url,
                            title = if (url == "orion://newtab" || url == "orion://newtab-incognito") {
                                if (isTabIncognito) "Incognito Tab" else "New Tab"
                            } else {
                                title.ifBlank { url ?: "Loaded" }
                            },
                            isLoading = false,
                            progress = 100,
                            canGoBack = view?.canGoBack() ?: false,
                            canGoForward = view?.canGoForward() ?: false
                        )
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

                    // Check for Reader Mode
                    if (url != null && !url.startsWith("orion://")) {
                        detectReaderModeAvailability(view)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    val isMainFrame = request?.isForMainFrame ?: false
                    if (isMainFrame) {
                        val failingUrl = request?.url?.toString() ?: ""
                        val description = error?.description?.toString() ?: "Connect failed"
                        val errorType = when {
                            description.contains("disconnected", true) || description.contains("offline", true) || error?.errorCode == ERROR_CONNECT -> "offline"
                            error?.errorCode == ERROR_TIMEOUT -> "timeout"
                            else -> "offline"
                        }
                        loadErrorHtml(view, errorType, failingUrl)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val isMainFrame = request?.isForMainFrame ?: false
                    if (isMainFrame && errorResponse?.statusCode == 404) {
                        val failingUrl = request?.url?.toString() ?: ""
                        loadErrorHtml(view, "404", failingUrl)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    // Custom HTML secure connection error
                    val failingUrl = error?.url ?: ""
                    loadErrorHtml(view, "ssl", failingUrl)
                    handler?.cancel() // Cancel securely
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("orion://retry")) {
                        val queryUrl = request.url.getQueryParameter("url")
                        if (!queryUrl.isNullOrBlank()) {
                            view?.loadUrl(queryUrl)
                        } else {
                            view?.loadUrl("orion://newtab")
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
                    return false
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
                        it.copy(title = if (currentUrl == "orion://newtab") "New Tab" else (titleStr ?: it.title).ifBlank { currentUrl })
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
        if (urlToLoad != "orion://newtab") {
            webView.loadUrl(urlToLoad)
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
            userAgentString = if (isDesktop) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
        }

        if (hwEnabled) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun updateTabState(tabId: String, update: (TabState) -> TabState) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map {
                    if (it.id == tabId) update(it) else it
                }
            )
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
                currentInputUrl = if (formatted == "orion://newtab") "" else formatted,
                readerModeActive = false,
                isTabSwitcherOpen = false,
                isSearchFocused = false
            )
        }

        val webView = webViewMap[activeId]
        if (webView != null) {
            if (formatted == "orion://newtab") {
                webView.loadUrl("about:blank")
            } else {
                webView.loadUrl(formatted)
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
        CookieManager.getInstance().removeAllCookies(null)
        val wv = WebView(context)
        wv.clearCache(true)
        // Also clear OkHttp cache
        try {
            okHttpClient.cache?.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun formatUrlOrSearch(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == "orion://newtab") return "orion://newtab"

        if (trimmed == "about:blank" || trimmed.startsWith("orion://")) {
            return trimmed
        }

        val hasSpace = trimmed.contains(" ")
        val hasScheme = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        val hasDot = trimmed.contains(".") && trimmed.substringAfterLast(".").count() in 2..4

        return if (!hasSpace && (hasScheme || hasDot)) {
            if (hasScheme) trimmed else "https://$trimmed"
        } else {
            val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
            "https://www.google.com/search?q=$encodedQuery"
        }
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
    }

    fun setSearchFocused(focused: Boolean) {
        _uiState.update { it.copy(isSearchFocused = focused) }
    }

    fun toggleDesktopMode() {
        val activeId = _uiState.value.activeTabId
        if (activeId.isEmpty()) return
        val currentTab = _uiState.value.tabs.find { it.id == activeId } ?: return
        val newDesktopState = !currentTab.isDesktopMode

        updateTabState(activeId) {
            it.copy(isDesktopMode = newDesktopState)
        }

        val webView = webViewMap[activeId]
        if (webView != null) {
            applyWebViewSettings(
                webView = webView,
                jsEnabled = _uiState.value.isJavaScriptEnabled,
                hwEnabled = _uiState.value.isHardwareAccelerationEnabled,
                isDesktop = newDesktopState
            )
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
