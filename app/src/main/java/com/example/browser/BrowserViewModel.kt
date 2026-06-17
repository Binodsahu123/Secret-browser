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
import android.util.Log
import com.example.searchengine.VoiceActionType
import com.example.searchengine.VoiceCommandResult
import com.example.searchengine.VoiceSearch
import com.example.searchengine.SearchEngineImpl
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
    val parentTabId: String? = null,
    val showTranslateBar: Boolean = false,
    val isPageTranslated: Boolean = false,
    val translateTargetLang: String = "English",
    val translateTargetLangCode: String = "en",
    val originalTranslationUrl: String = "",
    val autoTranslateEnabled: Boolean = false
)

data class BrowserUiState(
    val tabs: List<TabState> = emptyList(),
    val collapsedGroupIds: Set<String> = emptySet(),
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
    val isDevToolsOpen: Boolean = false,
    val isWebNotificationsOpen: Boolean = false,
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
    val translateTargetLang: String = "English",
    val translateTargetLangCode: String = "en",
    val originalTranslationUrl: String = "",
    val autoTranslateEnabled: Boolean = false,
    val isTabsListView: Boolean = false,
    val isCustomTabOrder: Boolean = true,
    
    // Orion Voice Assistant properties
    val isOrionListening: Boolean = false,
    val orionTranscript: String = "Listening...",
    val orionRmsdB: Float = 0f,
    val isOrionOverlayVisible: Boolean = false,
    val orionErrorMessage: String? = null,
    val historySearchQuery: String = "",
    val orionVoiceHistory: List<VoiceHistoryEntry> = emptyList(),
    val orionVoiceNotes: List<VoiceNote> = emptyList(),
    val orionVoiceActiveLanguage: String = "English",
    val orionVoiceActiveLanguageCode: String = "en-US",
    val orionVoiceAutoDetectLanguage: Boolean = true,
    val orionVoiceChatSessions: List<VoiceChatMessage> = emptyList(),
    val orionVoiceWakeWordEnabled: Boolean = false,
    val orionVoiceActiveMode: String = "Assistant", // "Assistant", "Notes", "Chat", "Saved"
    val orionNoteFormat: String = "Text" // "Text", "Markdown", "Browser"
)

data class VoiceHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val type: String, // "command", "search", "transcript", "chat"
    val timestamp: Long = System.currentTimeMillis()
)

data class VoiceNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalTranscript: String,
    val noteContent: String,
    val format: String, // "Text", "Markdown", "Browser"
    val timestamp: Long = System.currentTimeMillis(),
    val title: String = ""
)

data class VoiceChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user", "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
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

    val normalSessionStore = NormalSessionStore(application, this)
    val incognitoSessionStore = IncognitoSessionStore()
    val normalCookieStore = NormalCookieStore(application)
    val incognitoCookieStore = IncognitoCookieStore()
    val normalCacheStore = NormalCacheStore()
    val incognitoCacheStore = IncognitoCacheStore()
    val normalHistoryStore = NormalHistoryStore(repository)
    val incognitoHistoryStore = IncognitoHistoryStore()

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            translateTargetLang = com.example.translateengine.TranslationPreferenceManager.getTargetLanguageName(application),
            translateTargetLangCode = com.example.translateengine.TranslationPreferenceManager.getTargetLanguageCode(application)
        )
    )
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

    // SwiftBrowser Advanced Professional Download Engine
    val customDownloadEngine = com.example.downloadengine.DownloadManagerImpl(getApplication())
    val detectedMediaCustom = androidx.compose.runtime.mutableStateOf<com.example.mediadetectorengine.DetectedMedia?>(null)
    val showDownloadDialogCustom = androidx.compose.runtime.mutableStateOf(false)

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
    var afterSpeakAction: (() -> Unit)? = null
    var lastVoiceCommandType: String? = null
    var conversationalFollowUpType: String? = null
    lateinit var orionVoiceEngine: com.example.browser.voiceengine.OrionVoiceEngine
    private var activeSessionJob: kotlinx.coroutines.Job? = null
    var sessionEndTime: Long = 0L
    var isActiveSessionRunning = false
    private val sslDomainsToIgnore = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var recognitionRestartJob: kotlinx.coroutines.Job? = null

    init {
        // Route background extension service worker console logs to developer tools inspector
        try {
            extensionManager.engine.backgroundScriptManager.consoleLogCallback = { level, message ->
                viewModelScope.launch(Dispatchers.Main) {
                    val consoleLevel = when (level.uppercase(java.util.Locale.ROOT)) {
                        "ERROR" -> com.example.developertoolsengine.LogLevel.ERROR
                        "WARNING", "WARN" -> com.example.developertoolsengine.LogLevel.WARNING
                        "INFO" -> com.example.developertoolsengine.LogLevel.INFO
                        "DEBUG" -> com.example.developertoolsengine.LogLevel.DEBUG
                        else -> com.example.developertoolsengine.LogLevel.LOG
                    }
                    com.example.developertoolsengine.InspectorEngine.instance.logConsole(
                        consoleLevel,
                        "[Background] $message"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Pre-create and clean WebView Code Cache directories to prevent chromium simple_file_enumerator warnings/errors
        try {
            val cacheDir = application.cacheDir
            val codeCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            
            // Recursively wipe the previous cache to delete stale, corrupted or unreadable cache descriptors safely
            if (codeCacheDir.exists()) {
                codeCacheDir.deleteRecursively()
            }
            
            val wasmDir = java.io.File(codeCacheDir, "wasm")
            val jsDir = java.io.File(codeCacheDir, "js")
            
            // Create directories fresh
            wasmDir.mkdirs()
            jsDir.mkdirs()
            
            // Write a small placeholder file inside each to prevent empty-directory opendir complaints or auto-pruning
            val wasmPlace = java.io.File(wasmDir, ".init")
            wasmPlace.writeText("")
            val jsPlace = java.io.File(jsDir, ".init")
            jsPlace.writeText("")
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

        // Initialize Custom Mobile Stream/Media Network Sniffer Listener
        com.example.mediadetectorengine.NetworkSnifferEngine.registerListener(object : com.example.mediadetectorengine.MediaDetectionListener {
            override fun onMediaDetected(media: com.example.mediadetectorengine.DetectedMedia) {
                // Instantly propagate the sniffed media to the custom view state
                detectedMediaCustom.value = media
            }
        })

        // Observe Inspect Mode state changes and update page highlights
        viewModelScope.launch {
            com.example.developertoolsengine.InspectorEngine.instance.inspectModeEnabled.collect { enabled ->
                val activeId = _uiState.value.activeTabId
                val webView = webViewMap[activeId]
                webView?.post {
                    webView.evaluateJavascript("if (window.setInspectModeActive) { window.setInspectModeActive($enabled); }", null)
                }
            }
        }
        
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
                    setupTtsProgressListener()
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
                        applyWebViewSettings(webView, js, hw, tab.isDesktopMode, tab.isIncognito)
                    }
                }
            }.collect()
        }

        // Register Media Notification Receiver listener to respond dynamically to system/notification media intents
        try {
            com.example.medianotificationengine.MediaNotificationReceiver.onMediaAction = { action ->
                viewModelScope.launch(Dispatchers.Main) {
                    when (action) {
                        "ACTION_PLAY_PAUSE" -> {
                            toggleAudioPlayback()
                        }
                        "ACTION_CLOSE" -> {
                            closeLocalViewer()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add initial tab or restore previous tabs
        restoreTabsState()
        loadArticlesForCategory("For You", false)
        refreshSettings()
        loadOrionVoiceState()

        orionVoiceEngine = com.example.browser.voiceengine.OrionVoiceEngine(application, this)
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

    fun addNewTabInGroup(parentTabId: String, url: String) {
        val parentTab = _uiState.value.tabs.find { it.id == parentTabId } ?: return
        val isIncog = parentTab.isIncognito
        val parentGroup = parentTab.groupName
        val parentColor = parentTab.groupColor ?: 0xFF818CF8
        
        val tabId = UUID.randomUUID().toString()
        val formatted = if (url == "orion://newtab" && isIncog) "orion://newtab-incognito" else formatUrlOrSearch(url)
        
        // If parent has no group, create a fresh group name and color
        val finalGroupName: String
        val finalGroupColor: Long
        if (parentGroup.isNullOrBlank()) {
            val existingGroupCount = _uiState.value.tabs.mapNotNull { it.groupName }.distinct().size
            finalGroupName = "Group ${existingGroupCount + 1}"
            finalGroupColor = listOf(0xFFF87171, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFF472B6, 0xFF2DD4BF, 0xFFFB7185).random()
        } else {
            finalGroupName = parentGroup
            finalGroupColor = parentColor
        }
        
        val newTab = TabState(
            id = tabId,
            url = formatted,
            title = if (isIncog) "Incognito Tab" else if (formatted == "orion://newtab") "New Tab" else "Loading...",
            lastActiveTime = System.currentTimeMillis(),
            isIncognito = isIncog,
            parentTabId = parentTabId,
            groupId = finalGroupName.lowercase().trim(),
            groupName = finalGroupName,
            groupColor = finalGroupColor
        )
        
        _uiState.update { state ->
            val updatedTabs = state.tabs.map {
                if (it.id == parentTabId && parentGroup.isNullOrBlank()) {
                    it.copy(
                        groupId = finalGroupName.lowercase().trim(),
                        groupName = finalGroupName,
                        groupColor = finalGroupColor
                    )
                } else {
                    it
                }
            } + newTab
            state.copy(
                tabs = updatedTabs,
                activeTabId = tabId,
                currentInputUrl = if (formatted == "orion://newtab" || formatted == "orion://newtab-incognito") "" else formatted,
                isTabSwitcherOpen = false,
                readerModeActive = false,
                areToolbarsVisible = true
            )
        }
        
        cleanupOlderWebViews()
        preloadAdjacentTabs()
        saveTabsState()
    }

    fun renameGroup(groupId: String, newName: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map {
                if (it.groupId == groupId) {
                    it.copy(
                        groupId = newName.lowercase().trim(),
                        groupName = newName
                    )
                } else {
                    it
                }
            }
            state.copy(tabs = updatedTabs)
        }
        saveTabsState()
    }

    fun changeGroupColor(groupId: String, newColor: Long) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map {
                if (it.groupId == groupId) {
                    it.copy(groupColor = newColor)
                } else {
                    it
                }
            }
            state.copy(tabs = updatedTabs)
        }
        saveTabsState()
    }

    fun collapseGroup(groupId: String) {
        _uiState.update { state ->
            state.copy(collapsedGroupIds = state.collapsedGroupIds + groupId)
        }
        saveTabsState()
    }

    fun expandGroup(groupId: String) {
        _uiState.update { state ->
            state.copy(collapsedGroupIds = state.collapsedGroupIds - groupId)
        }
        saveTabsState()
    }

    fun deleteGroup(groupId: String) {
        val tabsInGroup = _uiState.value.tabs.filter { it.groupId == groupId }
        tabsInGroup.forEach { destroyTabWebView(it.id) }
        
        _uiState.update { state ->
            val updatedTabs = state.tabs.filter { it.groupId != groupId }
            val newActiveGroup = state.collapsedGroupIds - groupId
            val remainingIds = updatedTabs.map { it.id }
            val newActiveTabId = if (remainingIds.contains(state.activeTabId)) {
                state.activeTabId
            } else {
                remainingIds.firstOrNull() ?: ""
            }
            state.copy(
                tabs = updatedTabs,
                collapsedGroupIds = newActiveGroup,
                activeTabId = newActiveTabId
            )
        }
        saveTabsState()
        
        // reopen empty tab if all closed
        if (_uiState.value.tabs.isEmpty()) {
            addNewTab()
        }
    }

    fun addNewTabInGroupById(groupId: String, isIncognito: Boolean = false) {
        val matchTab = _uiState.value.tabs.find { it.groupId == groupId }
        val finalGroupName = matchTab?.groupName ?: "Group"
        val finalGroupColor = matchTab?.groupColor ?: 0xFF818CF8
        
        val tabId = UUID.randomUUID().toString()
        val formatted = if (isIncognito) "orion://newtab-incognito" else "orion://newtab"
        val newTab = TabState(
            id = tabId,
            url = formatted,
            title = if (isIncognito) "Incognito Tab" else "New Tab",
            lastActiveTime = System.currentTimeMillis(),
            isIncognito = isIncognito,
            groupId = groupId,
            groupName = finalGroupName,
            groupColor = finalGroupColor
        )
        
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = tabId,
                currentInputUrl = "",
                isTabSwitcherOpen = false,
                readerModeActive = false,
                areToolbarsVisible = true
            )
        }
        cleanupOlderWebViews()
        preloadAdjacentTabs()
        saveTabsState()
    }

    fun createNewTabGroup(groupName: String, color: Long, isIncognito: Boolean = false) {
        val tabId = UUID.randomUUID().toString()
        val formatted = if (isIncognito) "orion://newtab-incognito" else "orion://newtab"
        val newTab = TabState(
            id = tabId,
            url = formatted,
            title = if (isIncognito) "Incognito Tab" else "New Tab",
            lastActiveTime = System.currentTimeMillis(),
            isIncognito = isIncognito,
            groupId = groupName.lowercase().trim(),
            groupName = groupName,
            groupColor = color
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = tabId,
                currentInputUrl = "",
                isTabSwitcherOpen = false,
                readerModeActive = false,
                areToolbarsVisible = true
            )
        }
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
            val activeTab = updatedTabs.find { id -> id.id == tabId }
            state.copy(
                tabs = updatedTabs,
                activeTabId = tabId,
                currentInputUrl = if (activeTab?.url == "orion://newtab") "" else (activeTab?.url ?: ""),
                isTabSwitcherOpen = false,
                readerModeActive = false,
                findInPageActive = false,
                areToolbarsVisible = true,
                showTranslateBar = activeTab?.showTranslateBar ?: false,
                isPageTranslated = activeTab?.isPageTranslated ?: false,
                translateTargetLang = activeTab?.translateTargetLang ?: "Hindi",
                translateTargetLangCode = activeTab?.translateTargetLangCode ?: "hi",
                originalTranslationUrl = activeTab?.originalTranslationUrl ?: ""
            )
        }
        val targetTab = _uiState.value.tabs.find { it.id == tabId }
        val targetState = if (targetTab?.isPageTranslated == true) {
            com.example.translateengine.TranslationState.Translated
        } else if (targetTab?.showTranslateBar == true) {
            com.example.translateengine.TranslationState.Visible
        } else {
            com.example.translateengine.TranslationState.Hidden
        }
        translateManager.stateManager.transitionTo(targetState)

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

        // 4. Suspend inactive background tabs in TabStateManager to reclaim substantial RAM
        val suspendedTabsList = TabStateManager.suspendInactiveTabs(tabId, _uiState.value.tabs, webViewMap)
        if (suspendedTabsList != _uiState.value.tabs) {
            _uiState.update { it.copy(tabs = suspendedTabsList) }
        }

        preloadAdjacentTabs()
    }

    fun closeTab(tabId: String) {
        tabHistory.remove(tabId)
        val currentState = _uiState.value
        val tabs = currentState.tabs
        val tabToClose = tabs.find { it.id == tabId }
        val isIncog = tabToClose?.isIncognito == true

        if (tabs.size <= 1) {
            // If it's the last tab, close then reopen an empty new tab
            destroyTabWebView(tabId)
            if (isIncog) {
                incognitoCookieStore.clearCookies()
                incognitoCacheStore.clearAllIncognitoCache(emptyList())
                incognitoSessionStore.clearSession()
            }
            _uiState.update { state ->
                state.copy(tabs = emptyList(), activeTabId = "")
            }
            addNewTab()
            return
        }

        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        val updatedTabs = tabs.filter { it.id != tabId }
        
        // If it was incognito, and no other incognito tabs are left, do complete wipe
        if (isIncog && !updatedTabs.any { it.isIncognito }) {
            incognitoCookieStore.clearCookies()
            incognitoCacheStore.clearAllIncognitoCache(emptyList())
            incognitoSessionStore.clearSession()
        }

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

        var dataUri: android.net.Uri? = null
        if (action == android.content.Intent.ACTION_VIEW) {
            dataUri = intent.data
        } else if (action == android.content.Intent.ACTION_SEND) {
            dataUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        }

        if (dataUri != null) {
            val scheme = dataUri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                addNewTab(url = dataUri.toString())
                return
            }

            var mime = type ?: context.contentResolver.getType(dataUri) ?: ""
            var name = "External_File"

            // Query name from ContentResolver
            try {
                context.contentResolver.query(dataUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val retrievedName = cursor.getString(nameIndex)
                        if (!retrievedName.isNullOrBlank()) {
                            name = retrievedName
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Deduce mime type from file extension if empty or indeterminate
            if (mime.isEmpty() || mime == "*/*") {
                val ext = name.substringAfterLast(".").lowercase()
                mime = when (ext) {
                    "mp4", "mkv", "3gp", "avi", "webm" -> "video/*"
                    "mp3", "wav", "ogg", "aac", "m4a", "flac" -> "audio/*"
                    "pdf" -> "application/pdf"
                    "html", "htm" -> "text/html"
                    else -> mime.takeIf { it.isNotEmpty() } ?: "*/*"
                }
            }

            // Add standard extensions if not present to ensure target systems classify them appropriately
            if (!name.contains(".") && mime.isNotEmpty()) {
                val ext = when {
                    mime.contains("video/") -> "mp4"
                    mime.contains("audio/") -> "mp3"
                    mime.contains("pdf") -> "pdf"
                    mime.contains("html") -> "html"
                    else -> ""
                }
                if (ext.isNotEmpty()) {
                    name = "$name.$ext"
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

            val safeName = name.replace("/", "_").replace("\\", "_")
            _uiState.update {
                it.copy(
                    activeViewerFile = ActiveViewerFile(
                        filePath = dataUri.toString(),
                        fileName = safeName,
                        mimeType = mime
                    )
                )
            }
            return
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

        if (intent.getBooleanExtra("WAKE_WORD_TRIGGERED", false)) {
            val command = intent.getStringExtra("COMMAND_PAYLOAD")
            _uiState.update { it.copy(isOrionOverlayVisible = true) }
            if (command.isNullOrBlank()) {
                playVoiceAssistantResponseSpoken("Yes, I am listening")
                viewModelScope.launch {
                    kotlinx.coroutines.delay(650L)
                    startOrionVoiceListening(context)
                }
            } else {
                _uiState.update { it.copy(orionTranscript = command, isOrionListening = false, orionRmsdB = 0f) }
                viewModelScope.launch {
                    val hasWake = command.lowercase().contains("orion")
                    val type = if (hasWake) "command" else "search"
                    val historyItem = VoiceHistoryEntry(text = command, type = type)
                    _uiState.update { it.copy(orionVoiceHistory = it.orionVoiceHistory + historyItem) }
                    saveOrionVoiceState()
                    try {
                        val actionRouter = ActionRouter(this@BrowserViewModel)
                        val commandRouter = CommandRouter(getApplication(), actionRouter)
                        TranscriptManager.updateTranscript(command)
                        commandRouter.routeCommand(command)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val parsed = com.example.searchengine.VoiceSearch.parseVoiceCommand(command)
                        executeVoiceCommandResult(parsed)
                    }
                }
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
                    put("groupId", tab.groupId)
                    put("groupName", tab.groupName)
                    put("groupColor", tab.groupColor)
                    put("lastActiveTime", tab.lastActiveTime)
                }
                jsonArray.put(obj)
            }
            
            val collapsedArray = org.json.JSONArray()
            currentState.collapsedGroupIds.forEach { collapsedArray.put(it) }
            
            val prefs = getApplication<Application>().getSharedPreferences("orion_tab_state", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("saved_tabs_list", jsonArray.toString())
                putString("active_tab_id", currentState.activeTabId)
                putString("collapsed_groups_list", collapsedArray.toString())
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreTabsState() {
        val prefs = getApplication<Application>().getSharedPreferences("orion_tab_state", Context.MODE_PRIVATE)
        val isListViewSaved = prefs.getBoolean("is_tabs_list_view", false)
        val isCustomOrderSaved = prefs.getBoolean("is_custom_tab_order", true)
        
        val collapsedGroups = mutableSetOf<String>()
        val collapsedStr = prefs.getString("collapsed_groups_list", null)
        if (!collapsedStr.isNullOrEmpty()) {
            try {
                val collapsedArray = org.json.JSONArray(collapsedStr)
                for (i in 0 until collapsedArray.length()) {
                    collapsedGroups.add(collapsedArray.getString(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _uiState.update { state ->
            state.copy(
                isTabsListView = isListViewSaved,
                isCustomTabOrder = isCustomOrderSaved,
                collapsedGroupIds = collapsedGroups
            )
        }

        try {
            val savedListStr = prefs.getString("saved_tabs_list", null)
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
                    val groupId = if (obj.has("groupId")) obj.optString("groupId", null) else null
                    val groupName = if (obj.has("groupName")) obj.optString("groupName", null) else null
                    val groupColor = if (obj.has("groupColor")) obj.optLong("groupColor") else null
                    val lastActiveTime = obj.optLong("lastActiveTime", System.currentTimeMillis())
                    
                    restoredTabs.add(
                        TabState(
                            id = id,
                            url = url,
                            title = title,
                            isDesktopMode = isDesktopMode,
                            isIncognito = false,
                            parentTabId = if (parentTabId.isNullOrEmpty() || parentTabId == "null") null else parentTabId,
                            groupId = if (groupId.isNullOrEmpty() || groupId == "null") null else groupId,
                            groupName = if (groupName.isNullOrEmpty() || groupName == "null") null else groupName,
                            groupColor = if (groupColor == 0L) null else groupColor,
                            lastActiveTime = lastActiveTime
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
                            currentInputUrl = if (activeTab?.url == "orion://newtab") "" else (activeTab?.url ?: ""),
                            isTabsListView = isListViewSaved,
                            isCustomTabOrder = isCustomOrderSaved,
                            collapsedGroupIds = collapsedGroups
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

    fun setTabsListView(isList: Boolean) {
        _uiState.update { it.copy(isTabsListView = isList) }
        val prefs = getApplication<Application>().getSharedPreferences("orion_tab_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_tabs_list_view", isList).apply()
    }

    fun setCustomTabOrder(isCustom: Boolean) {
        _uiState.update { it.copy(isCustomTabOrder = isCustom) }
        val prefs = getApplication<Application>().getSharedPreferences("orion_tab_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_custom_tab_order", isCustom).apply()
    }

    fun moveTab(fromTabId: String, toTabId: String) {
        _uiState.update { state ->
            val tabs = state.tabs
            val fromIndex = tabs.indexOfFirst { it.id == fromTabId }
            val toIndex = tabs.indexOfFirst { it.id == toTabId }
            if (fromIndex != -1 && toIndex != -1) {
                val mutableTabs = tabs.toMutableList()
                val item = mutableTabs.removeAt(fromIndex)
                mutableTabs.add(toIndex, item)
                state.copy(tabs = mutableTabs)
            } else {
                state
            }
        }
        saveTabsState()
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
        private var lastSizeChangedTime = 0L
        private var isUserTouching = false

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            lastSizeChangedTime = System.currentTimeMillis()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            if (visibility == View.GONE || visibility == View.INVISIBLE) {
                super.onWindowVisibilityChanged(View.VISIBLE)
            } else {
                super.onWindowVisibilityChanged(visibility)
            }
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            
            // Ignore any scroll events generated during or immediately after layout resizing (cool-down of 800ms)
            // to break the feedback loop where resize triggers a fake scroll, triggering a resize, trigger a fake scroll, etc.
            if (System.currentTimeMillis() - lastSizeChangedTime < 800) {
                return
            }

            val currentY = t
            val diff = currentY - oldt
            
            if (currentY <= 25) {
                setToolbarsVisible(true)
                accumulatedNativeScroll = 0
                return
            }

            // Only respond to scrolls that are initiated by active user touch interactions.
            // This prevents artificial resizing loops and "lapa lap" flickering entirely on focus/clicks.
            if (!isUserTouching) {
                return
            }
            
            if ((diff > 0 && accumulatedNativeScroll < 0) || (diff < 0 && accumulatedNativeScroll > 0)) {
                accumulatedNativeScroll = 0
            }
            
            accumulatedNativeScroll += diff
            
            // Large thresholds to achieve slow, deliberate, and high-quality Chrome-like toolbar transitions.
            if (accumulatedNativeScroll > 240) {
                setToolbarsVisible(false)
                accumulatedNativeScroll = 0
            } else if (accumulatedNativeScroll < -140) {
                setToolbarsVisible(true)
                accumulatedNativeScroll = 0
            }
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isUserTouching = true
                    startX = event.x
                    startY = event.y
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    isUserTouching = true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isUserTouching = false
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

        // Segment memory leak trackers
        com.example.browser.LeakProneComponentTracker.trackContext(context)

        val currentTab = _uiState.value.tabs.find { it.id == tabId }

        val webView = BrowserWebView(context, tabId).apply {
            // Track living WebView reference for leak checks
            com.example.browser.WebViewReferenceCollector.register(this)
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
                currentTab?.isDesktopMode ?: false,
                currentTab?.isIncognito ?: false
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
                            if (request != null) {
                                if (urlStr != null) {
                                    val method = request.method ?: "GET"
                                    val headers = request.requestHeaders ?: emptyMap()
                                    com.example.browser.RequestInterceptorEngine.interceptAndRecord(urlStr, method, headers, context)
                                }
                                com.example.mediadetectorengine.RequestInterceptorEngine.interceptAndSniff(request)
                                val netReq = com.example.developertoolsengine.NetworkRequest(
                                    id = java.util.UUID.randomUUID().toString(),
                                    url = request.url?.toString() ?: "",
                                    method = request.method ?: "GET",
                                    statusCode = 200,
                                    startTime = System.currentTimeMillis(),
                                    durationMs = 0L,
                                    requestHeaders = request.requestHeaders ?: emptyMap(),
                                    responseHeaders = emptyMap(),
                                    requestBody = "",
                                    responseBody = ""
                                )
                                com.example.developertoolsengine.InspectorEngine.instance.recordNetworkRequest(netReq)
                            }
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
                        detectedMediaCustom.value = null
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
                                hasLoadedSuccessfully = false,
                                isPageTranslated = false,
                                showTranslateBar = false
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
                            
                            // Inject Notification polyfill early
                            com.example.notificationengine.NotificationEngineImpl(context).getJavascriptPolyfill(url) { polyfill ->
                                view.post {
                                    view.evaluateJavascript(polyfill, null)
                                }
                            }
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

                        // Inject Orion Compatibility Engine layers
                        if (view != null && url != null) {
                            com.example.browser.OrionCompatibilityEngine.injectCompatibilityLayer(view, url, context)
                        }

                        // Inject Simulated Chrome/User extensions upon page load complete
                        if (view != null && url != null && !url.startsWith("orion://") && url != "about:blank") {
                            // SwiftBrowser Advanced Professional Media Detection
                            try {
                                view.post {
                                    view.evaluateJavascript(com.example.mediadetectorengine.MediaDetector.MEDIA_EXTRACTION_JS) { result ->
                                        if (!result.isNullOrBlank() && result != "null" && result != "[]") {
                                            viewModelScope.launch(Dispatchers.IO) {
                                                try {
                                                    val detected = com.example.mediadetectorengine.MediaDetector.detectFromUrl(url)
                                                    if (detected != null) {
                                                        val fullMetadata = com.example.mediadetectorengine.MediaMetadataExtractor.extractMetadata(url)
                                                        viewModelScope.launch(Dispatchers.Main) {
                                                            detectedMediaCustom.value = fullMetadata
                                                        }
                                                    } else {
                                                        val cleanResult = result.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                                                        if (cleanResult.contains("url")) {
                                                            val matchUrl = "url\":\"([^\"]+)\"".toRegex().find(cleanResult)?.groups?.get(1)?.value
                                                            if (matchUrl != null && matchUrl.startsWith("http")) {
                                                                val directMetadata = com.example.mediadetectorengine.MediaMetadataExtractor.extractMetadata(matchUrl)
                                                                viewModelScope.launch(Dispatchers.Main) {
                                                                    detectedMediaCustom.value = directMetadata
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        } else {
                                            viewModelScope.launch(Dispatchers.IO) {
                                                val detected = com.example.mediadetectorengine.MediaDetector.detectFromUrl(url)
                                                if (detected != null) {
                                                    val fullMetadata = com.example.mediadetectorengine.MediaMetadataExtractor.extractMetadata(url)
                                                    viewModelScope.launch(Dispatchers.Main) {
                                                        detectedMediaCustom.value = fullMetadata
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // Inject Developer Tools capture scripts
                            try {
                                view.post {
                                    // Inject Console capture
                                    view.evaluateJavascript("""
                                        (function() {
                                            if (window._consoleOverriden) return;
                                            window._consoleOverriden = true;
                                            var levels = ['log', 'info', 'warn', 'error', 'debug'];
                                            levels.forEach(function(level) {
                                                var original = console[level];
                                                console[level] = function() {
                                                    var args = Array.prototype.slice.call(arguments);
                                                    var msg = args.map(function(arg) {
                                                        if (typeof arg === 'object') {
                                                            try { return JSON.stringify(arg); } catch(e) { return String(arg); }
                                                        }
                                                        return String(arg);
                                                    }).join(' ');
                                                    if (original) {
                                                        original.apply(console, arguments);
                                                    }
                                                    if (window.DevToolsBridge) {
                                                        window.DevToolsBridge.sendConsoleLog(level, msg);
                                                    }
                                                };
                                            });
                                        })();
                                    """.trimIndent(), null)

                                    // Inject element inspection base
                                    view.evaluateJavascript("""
                                        (function() {
                                            if (window._inspectModeRegistered) return;
                                            window._inspectModeRegistered = true;
                                            
                                            var highlight = document.createElement('div');
                                            highlight.style.position = 'fixed';
                                            highlight.style.pointerEvents = 'none';
                                            highlight.style.border = '2px solid #6366f1';
                                            highlight.style.backgroundColor = 'rgba(99, 102, 241, 0.15)';
                                            highlight.style.zIndex = '99999999';
                                            highlight.style.display = 'none';
                                            document.body.appendChild(highlight);

                                            var active = false;

                                            window.setInspectModeActive = function(enabled) {
                                                active = enabled;
                                                if (!enabled) {
                                                    highlight.style.display = 'none';
                                                }
                                            };

                                            document.addEventListener('mousemove', function(e) {
                                                if (!active) return;
                                                var target = e.target;
                                                if (target && target !== highlight) {
                                                    var rect = target.getBoundingClientRect();
                                                    highlight.style.left = rect.left + 'px';
                                                    highlight.style.top = rect.top + 'px';
                                                    highlight.style.width = rect.width + 'px';
                                                    highlight.style.height = rect.height + 'px';
                                                    highlight.style.display = 'block';
                                                }
                                            }, { capture: true, passive: true });

                                            document.addEventListener('click', function(e) {
                                                if (!active) return;
                                                e.preventDefault();
                                                e.stopPropagation();
                                                var target = e.target;
                                                if (target && target !== highlight) {
                                                    var htmlStr = target.outerHTML;
                                                    if (htmlStr.length > 5000) {
                                                        htmlStr = htmlStr.substr(0, 4500) + "\n... (trimmed for inspector)";
                                                    }
                                                    if (window.DevToolsBridge) {
                                                        window.DevToolsBridge.onElementInspected(htmlStr);
                                                    }
                                                }
                                            }, { capture: true });
                                        })();
                                    """.trimIndent(), null)

                                    // Automatically trigger Inspect activation state in sync
                                    val activeState = com.example.developertoolsengine.InspectorEngine.instance.inspectModeEnabled.value
                                    view.evaluateJavascript("if (window.setInspectModeActive) { window.setInspectModeActive($activeState); }", null)

                                    // Scan full HTML DOM Tree
                                    view.evaluateJavascript("""
                                        (function() {
                                            if (window.DevToolsBridge) {
                                                var rootHtml = document.documentElement.outerHTML;
                                                window.DevToolsBridge.updateFullDOM(rootHtml);
                                            }
                                        })();
                                    """.trimIndent(), null)

                                    // Scan Storages
                                    view.evaluateJavascript("""
                                        (function() {
                                            if (!window.DevToolsBridge) return;
                                            try {
                                                for (var i = 0; i < localStorage.length; i++) {
                                                    var k = localStorage.key(i);
                                                    window.DevToolsBridge.sendStorageData("LocalStorage", k, localStorage.getItem(k));
                                                }
                                            } catch(e){}
                                            try {
                                                for (var i = 0; i < sessionStorage.length; i++) {
                                                    var k = sessionStorage.key(i);
                                                    window.DevToolsBridge.sendStorageData("SessionStorage", k, sessionStorage.getItem(k));
                                                }
                                            } catch(e){}
                                            try {
                                                if (window.indexedDB && window.indexedDB.databases) {
                                                    window.indexedDB.databases().then(function(dbs) {
                                                        dbs.forEach(function(dbInfo) {
                                                            window.DevToolsBridge.sendStorageData("IndexedDB", dbInfo.name, "Version " + dbInfo.version);
                                                        });
                                                    }).catch(function(e){});
                                                } else {
                                                    window.DevToolsBridge.sendStorageData("IndexedDB", "Database API", "Connected / Active");
                                                }
                                            } catch(e){}
                                        })();
                                    """.trimIndent(), null)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            // Inject Notification polyfill to re-enforce state
                            com.example.notificationengine.NotificationEngineImpl(context).getJavascriptPolyfill(url) { polyfill ->
                                view.post {
                                    view.evaluateJavascript(polyfill, null)
                                }
                            }
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
                        if (url != null && !url.contains("translate.google.com") && !url.startsWith("orion://") && url != "about:blank" && url != "orion://newtab" && url != "orion://newtab-incognito") {
                            val host = com.example.adblockengine.AdBlocker.getDomainName(url) ?: ""
                            
                            // Initialize page started navigation tracking
                            com.example.translateengine.TranslationNavigationManager.handlePageStarted(getApplication(), tabId, url)

                            // Clear any stale snapshots for this tab context since a new page is loaded
                            com.example.translateengine.OriginalPageSnapshotManager.clearSnapshot(tabId)

                            val autoTranslateLangCode = com.example.translateengine.TranslationNavigationManager.shouldAutoTranslate(getApplication(), url)
                            if (autoTranslateLangCode != null) {
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
                                val autoTranslateLangName = languagesMap[autoTranslateLangCode] ?: "English"
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    executeGoogleTranslation(autoTranslateLangCode, autoTranslateLangName)
                                }
                            } else {
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
                                                val isNeverSite = translateManager.settings.getNeverTranslateSites().contains(host)
                                                val isNeverLang = translateManager.settings.getNeverTranslateLanguages().contains(detectedLang)
                                                
                                                if (detectedLang.isNotEmpty() && detectedLang != "unknown" && detectedLang != targetLangCode) {
                                                    // Check auto translate first
                                                    val autoTranslate = false // translateManager.settings.isAutoTranslateEnabled() || 
                                                                        translateManager.settings.getAlwaysTranslateSites().contains(host)
                                                    
                                                    if (autoTranslate && !isNeverSite && !isNeverLang) {
                                                        // Auto translate
                                                        val targetLangName = _uiState.value.translateTargetLang
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            executeGoogleTranslation(targetLangCode, targetLangName)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
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

                        if (url != null && url != "orion://newtab" && url != "orion://newtab-incognito" && !url.startsWith("orion://")) {
                            viewModelScope.launch {
                                if (isTabIncognito) {
                                    incognitoHistoryStore.addHistory(url, title)
                                } else {
                                    normalHistoryStore.addHistory(url, title)
                                }
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
                            if (view != null && url != "about:blank") {
                                PreloadAIEngine.preloadPage(getApplication(), tabId, view, url, AISettingsManager(getApplication()))
                            }
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
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val level = when (it.messageLevel()) {
                            android.webkit.ConsoleMessage.MessageLevel.TIP -> com.example.developertoolsengine.LogLevel.INFO
                            android.webkit.ConsoleMessage.MessageLevel.LOG -> com.example.developertoolsengine.LogLevel.LOG
                            android.webkit.ConsoleMessage.MessageLevel.WARNING -> com.example.developertoolsengine.LogLevel.WARNING
                            android.webkit.ConsoleMessage.MessageLevel.ERROR -> com.example.developertoolsengine.LogLevel.ERROR
                            android.webkit.ConsoleMessage.MessageLevel.DEBUG -> com.example.developertoolsengine.LogLevel.DEBUG
                            else -> com.example.developertoolsengine.LogLevel.LOG
                        }
                        com.example.developertoolsengine.InspectorEngine.instance.logConsole(
                            level,
                            "${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }

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
                        FullscreenController.onEnterFullscreen(view.context)
                    }
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    val state = _fullscreenState.value
                    if (state != null) {
                        FullscreenController.onExitFullscreen(state.view.context)
                        state.callback.onCustomViewHidden()
                    }
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
            
            // Register native Web Notification Bridge
            addJavascriptInterface(
                com.example.notificationengine.AndroidNotificationBridge(context) { show, origin, webTitle ->
                    if (show) {
                        showNotificationPermissionDialog(origin)
                    }
                },
                "AndroidNotificationBridge"
            )

            // Register Developer Tools dynamic instrumentation bridge
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun updateFullDOM(html: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        com.example.developertoolsengine.InspectorEngine.instance.updateFullDOM(html)
                    }
                }

                @android.webkit.JavascriptInterface
                fun onElementInspected(html: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        com.example.developertoolsengine.InspectorEngine.instance.updateHighlightedElement(html)
                    }
                }

                @android.webkit.JavascriptInterface
                fun sendConsoleLog(level: String, message: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        val consoleLevel = when (level.lowercase(java.util.Locale.ROOT)) {
                            "error" -> com.example.developertoolsengine.LogLevel.ERROR
                            "warning", "warn" -> com.example.developertoolsengine.LogLevel.WARNING
                            "info" -> com.example.developertoolsengine.LogLevel.INFO
                            "debug" -> com.example.developertoolsengine.LogLevel.DEBUG
                            else -> com.example.developertoolsengine.LogLevel.LOG
                        }
                        com.example.developertoolsengine.InspectorEngine.instance.logConsole(consoleLevel, message)
                    }
                }

                @android.webkit.JavascriptInterface
                fun sendStorageData(type: String, key: String, value: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        val entry = com.example.developertoolsengine.StorageEntry(key = key, value = value, type = type)
                        val currentList = com.example.developertoolsengine.InspectorEngine.instance.storageEntries.value
                        if (!currentList.contains(entry)) {
                            com.example.developertoolsengine.InspectorEngine.instance.setStorageEntries(currentList + entry)
                        }
                    }
                }
            }, "DevToolsBridge")
        }

        applyWebViewSettings(
            webView,
            _uiState.value.isJavaScriptEnabled,
            _uiState.value.isHardwareAccelerationEnabled,
            currentTab?.isDesktopMode ?: false,
            currentTab?.isIncognito ?: false
        )

        // If the URL is already present in TabState, load it initially
        val urlToLoad = currentTab?.url ?: "orion://newtab"
        if (urlToLoad != "orion://newtab" && urlToLoad != "orion://newtab-incognito") {
            safeLoadUrl(webView, urlToLoad)
        }

        webViewMap[tabId] = webView
        
        // Reset the isWebViewDestroyed flag since it is now reconstructed and active
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map {
                if (it.id == tabId) it.copy(isWebViewDestroyed = false) else it
            })
        }
        return webView
    }

    private fun applyWebViewSettings(webView: WebView, jsEnabled: Boolean, hwEnabled: Boolean, isDesktop: Boolean = false, isIncognito: Boolean = false) {
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

        if (isIncognito) {
            incognitoCookieStore.setupCookies(webView)
            incognitoCacheStore.setupCache(webView)
        } else {
            normalCookieStore.acceptCookies(webView)
            normalCacheStore.setupCache(webView)
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
        if (formatted == "orion://downloads" || formatted == "swiftbrowser://downloads" || formatted.contains("downloads")) {
            setDownloadsOpen(true)
            return
        }
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
        
        // Register in notification engine database
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val store = com.example.notificationengine.WebsitePermissionStore(getApplication())
                val hostName = com.example.notificationengine.NotificationRegistry.getHostDomain(origin)
                store.setPermission(
                    websiteUrl = origin,
                    websiteName = hostName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() },
                    permission = if (allowed) "ALLOW" else "BLOCK"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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

    fun getLocalFallbackExtensions(): List<com.example.browser.ExtensionMeta> {
        return listOf(
            com.example.browser.ExtensionMeta(
                id = "cjpalhdlnbpafiamejdnhcphjbkeiame",
                name = "uBlock Origin",
                description = "An efficient wide-spectrum content blocker. Easy on CPU and memory.",
                version = "v1.58.0",
                size = "3.1 MB",
                provider = "Raymond Hill (gorhill)",
                lastUpdated = "2026-05-10",
                permissionDescription = "Block network advertisements, modify stylesheet files, secure privacy layers.",
                defaultInstalled = false,
                iconPath = ""
            ),
            com.example.browser.ExtensionMeta(
                id = "dhdgffkkbafomglifgghicadnoocndbo",
                name = "Tampermonkey",
                description = "The world's most popular userscript manager. Customize webpage behaviors dynamically.",
                version = "v5.1.1",
                size = "1.8 MB",
                provider = "Jan Biniok",
                lastUpdated = "2026-04-18",
                permissionDescription = "Inject user scripts, capture browser tabs, control active page actions.",
                defaultInstalled = false,
                iconPath = ""
            ),
            com.example.browser.ExtensionMeta(
                id = "gighmmpiobklfepjocnamgkkbiglidom",
                name = "AdBlock",
                description = "The native ad blocker to clean websites and secure privacy.",
                version = "v5.19.0",
                size = "4.2 MB",
                provider = "getadblock.com",
                lastUpdated = "2026-05-20",
                permissionDescription = "Block network ads, modify stylesheet styles.",
                defaultInstalled = false,
                iconPath = ""
            ),
            com.example.browser.ExtensionMeta(
                id = "eimadpmoofgohgcoofbllgndgaghgffg",
                name = "Dark Reader",
                description = "Dark mode for every website. Take care of your eyes, use dark reader for night and daily browsing.",
                version = "v4.9.82",
                size = "1.2 MB",
                provider = "darkreader.org",
                lastUpdated = "2026-06-02",
                permissionDescription = "Invert website colors, inject custom stylesheet stylesheets.",
                defaultInstalled = false,
                iconPath = ""
            ),
            com.example.browser.ExtensionMeta(
                id = "kbfnbcaeplbcioakkpcpgfkobkghlhen",
                name = "Grammarly",
                description = "Improve your writing with Grammarly's AI-powered communication assistant.",
                version = "v14.12.0",
                size = "14.2 MB",
                provider = "Grammarly Inc.",
                lastUpdated = "2026-06-01",
                permissionDescription = "Read and parse text inputs, check layout errors.",
                defaultInstalled = false,
                iconPath = ""
            ),
            com.example.browser.ExtensionMeta(
                id = "mpbjkejclgdegidofafiongckaokgajg",
                name = "Buster: Captcha Solver",
                description = "Solve difficult captchas easily by completing voice challenges.",
                version = "v2.8.1",
                size = "420 KB",
                provider = "Armin Sebastian",
                lastUpdated = "2026-05-01",
                permissionDescription = "Read captchas, simulate speech playback, click audio solvers.",
                defaultInstalled = false,
                iconPath = ""
            )
        )
    }

    fun searchChromeWebStore(query: String, onResult: (List<com.example.browser.ExtensionMeta>) -> Unit) {
        if (query.isBlank()) {
            onResult(emptyList())
            return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val results = mutableListOf<com.example.browser.ExtensionMeta>()
            
            // 1. Filter standard fallbacks first to populate instantly
            val lower = query.lowercase().trim()
            val matches = getLocalFallbackExtensions().filter {
                it.name.contains(lower, ignoreCase = true) ||
                it.description.contains(lower, ignoreCase = true) ||
                it.id.contains(lower, ignoreCase = true)
            }
            results.addAll(matches)
            
            // 2. Call Gemini API to query online CWS recommendations dynamically if API key is present
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isNotBlank() && apiKey != "placeholder_gemini_key") {
                try {
                    val prompt = """
                        You are an expert Google Chrome Web Store Search Engine.
                        The user is searching for extensions with query: "$query".
                        Search your database / knowledge for the most accurate and real 2 to 4 Google Chrome extensions that match this query.
                        IMPORTANT: For each extension, you MUST provide the real 32-letter lowercase Extension ID from the Chrome Web Store (e.g. 'cjpalhdlnbpafiamejdnhcphjbkeiame' for uBlock Origin, 'dhdgffkkbafomglifgghicadnoocndbo' for Tampermonkey, 'gighmmpiobklfepjocnamgkkbiglidom' for Adblock, etc.). The ID must be exactly the 32 letters long so downloading CRX works.
                        
                        Return a valid JSON array only, containing objects with exactly these keys:
                        - "id": (32-character lowercase CWS id)
                        - "name": (Name of extension)
                        - "description": (Brief description)
                        - "version": (Estimated version like "v1.4.3")
                        - "size": (Estimated size like "2.4 MB")
                        - "provider": (Author/publisher)
                        - "lastUpdated": (Like "2026-05-18")
                        - "permissionDescription": (Brief summary of permissions needed)
                        
                        Do not wrap the response in ```json ``` markdown code blocks. Returns plain text JSON. If nothing is found, return [].
                    """.trimIndent()
                    
                    val part = org.json.JSONObject().put("text", prompt)
                    val content = org.json.JSONObject().put("parts", org.json.JSONArray().put(part))
                    val bodyObj = org.json.JSONObject().put("contents", org.json.JSONArray().put(content))
                    
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val req = okhttp3.Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent")
                        .post(bodyObj.toString().toRequestBody(mediaType))
                        .addHeader("x-goog-api-key", apiKey)
                        .build()
                        
                    okHttpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val resBody = response.body?.string() ?: ""
                            val responseJson = org.json.JSONObject(resBody)
                            var rawText = responseJson.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                                .trim()
                            
                            // Clean up potential markdown wrapper
                            if (rawText.startsWith("```json")) {
                                rawText = rawText.substringAfter("```json").substringBeforeLast("```").trim()
                            } else if (rawText.startsWith("```")) {
                                rawText = rawText.substringAfter("```").substringBeforeLast("```").trim()
                            }
                            
                            if (rawText.startsWith("[")) {
                                val jsonArray = org.json.JSONArray(rawText)
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val id = obj.optString("id").trim()
                                    if (id.length == 32 && results.none { it.id == id }) {
                                        results.add(
                                            com.example.browser.ExtensionMeta(
                                                id = id,
                                                name = obj.optString("name"),
                                                description = obj.optString("description"),
                                                version = obj.optString("version", "v1.0.0"),
                                                size = obj.optString("size", "310 KB"),
                                                provider = obj.optString("provider", "Chrome Web Store Developer"),
                                                lastUpdated = obj.optString("lastUpdated", "Recently"),
                                                permissionDescription = obj.optString("permissionDescription", "Read webpage documents & modify stylesheets"),
                                                defaultInstalled = false,
                                                iconPath = "https://clients2.googleusercontent.com/crx/blobs/legacy/apid/${id}/extension_128_0.png"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // If empty, generate a clean dynamic recommendation card matching user query
            if (results.isEmpty()) {
                val mockIdByQuery = lower.replace("[^a-z]".toRegex(), "")
                val paddedId = (mockIdByQuery + "abcdefghijklmnopqrstuvwxyz").take(32)
                results.add(
                    com.example.browser.ExtensionMeta(
                        id = paddedId,
                        name = "${query.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} Extension",
                        description = "A dynamic Chromium extension to optimize, secure, and inject scripts dynamically on '$query' layouts.",
                        version = "v1.8.2",
                        size = "240 KB",
                        provider = "${query.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} Author",
                        lastUpdated = "2026-06-11",
                        permissionDescription = "Read and modify layouts on active documents, manage secure local scripts.",
                        defaultInstalled = false,
                        iconPath = "https://clients2.googleusercontent.com/crx/blobs/legacy/apid/${paddedId}/extension_128_0.png"
                    )
                )
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(results)
            }
        }
    }

    // Dynamic checks
    fun clearWebViewCache(context: Context) {
        // Clear all web cache
        try {
            val activeId = _uiState.value.activeTabId
            val allInactiveIds = _uiState.value.tabs.filter { it.id != activeId && !it.isWebViewDestroyed }.map { it.id }.toSet()
            if (allInactiveIds.isNotEmpty()) {
                allInactiveIds.forEach { id ->
                    val webView = webViewMap.remove(id)
                    webView?.let {
                        try {
                            it.stopLoading()
                            it.clearHistory()
                            it.removeAllViews()
                            it.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                _uiState.update { state ->
                    state.copy(tabs = state.tabs.map {
                        if (allInactiveIds.contains(it.id)) it.copy(isWebViewDestroyed = true) else it
                    })
                }
            }
            MemoryLeakDetector.runSystemGC()

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
        val state = _fullscreenState.value
        if (state != null) {
            FullscreenController.onExitFullscreen(state.view.context)
            state.callback.onCustomViewHidden()
        }
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

        _uiState.update { state ->
            val origUrl = if (!state.isPageTranslated) currentUrl else state.originalTranslationUrl
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        showTranslateBar = true,
                        originalTranslationUrl = origUrl
                    )
                } else tab
            }
            state.copy(
                showTranslateBar = true,
                originalTranslationUrl = origUrl,
                tabs = updatedTabs
            )
        }
    }

    fun dismissTranslateBar() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId]
        val currentUrl = webView?.url ?: ""
        val host = com.example.adblockengine.AdBlocker.getDomainName(currentUrl) ?: ""
        if (host.isNotEmpty()) {
            com.example.translateengine.TranslationSessionManager.disableDomainTranslation(host)
            com.example.translateengine.TranslationPreferenceManager.removePersistedActiveDomain(getApplication(), host)
        }

        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Hidden)
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        showTranslateBar = false,
                        isPageTranslated = false
                    )
                } else tab
            }
            state.copy(
                showTranslateBar = false,
                isPageTranslated = false,
                tabs = updatedTabs
            )
        }
        if (webView != null) {
            com.example.translateengine.DomRestoreEngine.restoreOriginal(webView) { res ->
                android.util.Log.d("BrowserViewModel", "DOM restoration on dismiss finished! Result: $res")
            }
        }
    }

    fun undoTranslation() {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId] ?: return
        val currentUrl = webView.url ?: ""
        val host = com.example.adblockengine.AdBlocker.getDomainName(currentUrl) ?: ""
        if (host.isNotEmpty()) {
            com.example.translateengine.TranslationSessionManager.disableDomainTranslation(host)
            com.example.translateengine.TranslationPreferenceManager.removePersistedActiveDomain(getApplication(), host)
        }

        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Original)
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        isPageTranslated = false,
                        showTranslateBar = true
                    )
                } else tab
            }
            state.copy(
                isPageTranslated = false,
                showTranslateBar = true,
                tabs = updatedTabs
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

        com.example.translateengine.TranslationPreferenceManager.saveTargetLanguage(getApplication(), langCode, langName)
        val host = com.example.adblockengine.AdBlocker.getDomainName(currentUrl) ?: ""
        if (host.isNotEmpty()) {
            com.example.translateengine.TranslationSessionManager.startSession(activeId, host, langCode, langName)
            com.example.translateengine.TranslationPreferenceManager.addPersistedActiveDomain(getApplication(), host)
        }

        val originalUrl = if (!_uiState.value.isPageTranslated) currentUrl else _uiState.value.originalTranslationUrl
        
        translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Translating)
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        isPageTranslated = true,
                        translateTargetLang = langName,
                        translateTargetLangCode = langCode,
                        originalTranslationUrl = originalUrl,
                        showTranslateBar = true
                    )
                } else tab
            }
            state.copy(
                isPageTranslated = true,
                translateTargetLang = langName,
                translateTargetLangCode = langCode,
                originalTranslationUrl = originalUrl,
                showTranslateBar = true,
                tabs = updatedTabs
            )
        }

        val activeTab = _uiState.value.tabs.find { it.id == activeId }
        val isDesktop = activeTab?.isDesktopMode == true

        // Call our advanced background translation manager
        translateManager.translateWebView(webView, langCode, activeId, isDesktop) { count ->
            android.util.Log.d("BrowserViewModel", "Page translation finished! Injected $count nodes.")
            translateManager.stateManager.transitionTo(com.example.translateengine.TranslationState.Translated)
            PreloadAIEngine.preloadTranslatedPage(getApplication(), activeId, webView, currentUrl, AISettingsManager(getApplication()), langCode, langName)
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

    fun setDevToolsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDevToolsOpen = isOpen) }
        if (isOpen) {
            val activeId = _uiState.value.activeTabId
            val activeTab = _uiState.value.tabs.find { it.id == activeId }
            activeTab?.url?.let { url ->
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookiesStr = cookieManager.getCookie(url)
                if (!cookiesStr.isNullOrBlank()) {
                    val pairs = cookiesStr.split(";")
                    val cookieEntries = pairs.map { pair ->
                        val parts = pair.split("=", limit = 2)
                        val key = parts[0].trim()
                        val value = if (parts.size > 1) parts[1].trim() else ""
                        com.example.developertoolsengine.StorageEntry(key = key, value = value, type = "Cookies")
                    }
                    val currentAndOther = com.example.developertoolsengine.InspectorEngine.instance.storageEntries.value.filter { it.type != "Cookies" }
                    com.example.developertoolsengine.InspectorEngine.instance.setStorageEntries(currentAndOther + cookieEntries)
                }
            }
        }
    }

    fun setWebNotificationsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isWebNotificationsOpen = isOpen) }
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
                    if (utteranceId == "orion_speak") {
                        val action = afterSpeakAction
                        afterSpeakAction = null
                        if (action != null) {
                            action.invoke()
                        }
                    } else if (utteranceId?.startsWith("segment_") == true) {
                        playNextTtsSegment()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    if (utteranceId == "orion_speak") {
                        val action = afterSpeakAction
                        afterSpeakAction = null
                        if (action != null) {
                            action.invoke()
                        }
                    }
                }
            }
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
        
        incognitoCookieStore.clearCookies()
        incognitoCacheStore.clearAllIncognitoCache(emptyList())
        incognitoSessionStore.clearSession()

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
    
    private val mediaNotificationEngine by lazy {
        com.example.medianotificationengine.MediaNotificationEngine(getApplication())
    }
    
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
                val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
                    android.net.Uri.parse(filePath)
                } else {
                    android.net.Uri.fromFile(java.io.File(filePath))
                }
                setDataSource(getApplication(), uri)
                prepare()
                start()
                isLooping = isRepeatEnabled.value
            }
            isMediaPlaying.value = true
            mediaDuration.value = mediaPlayer?.duration ?: 0
            mediaTrackName.value = trackName
            
            try {
                mediaNotificationEngine.showPlaybackNotification(trackName, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
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
                try {
                    mediaNotificationEngine.showPlaybackNotification(mediaTrackName.value, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                player.start()
                isMediaPlaying.value = true
                startAudioTicker()
                try {
                    mediaNotificationEngine.showPlaybackNotification(mediaTrackName.value, true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
            try {
                mediaNotificationEngine.clearNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun getActiveWebViewText(onResult: (String) -> Unit) {
        executeScriptOnTab("", FastAIExtractor.JS_EXTRACT_SCRIPT) { res ->
            val text = if (res != null && res != "null") {
                try {
                    org.json.JSONTokener(res).nextValue() as? String ?: res
                } catch (e: Exception) {
                    res
                }
            } else {
                ""
            }
            onResult(text)
        }
    }

    // --- ORION VOICE INTELLIGENCE ENGINE V3 SYSTEM ---
    private var wakeWordJob: kotlinx.coroutines.Job? = null

    fun startOrionVoiceListening(context: android.content.Context) {
        Log.i("OrionVoiceV3", "Entering Direct User-Triggered Voice Listening Mode (Single Turn).")
        isActiveSessionRunning = true
        
        // Coordinated stop of any background engines/services to ensure mic access
        try {
            com.example.browser.voiceengine.OrionHotwordService.stopService(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        activeSessionJob?.cancel()
        activeSessionJob = null

        startActiveSpeechRecognizer(context)
    }

    private fun startActiveSpeechRecognizer(context: android.content.Context) {
        if (!isActiveSessionRunning) return
        stopActiveSpeechRecognizer()

        _uiState.update {
            it.copy(
                isOrionListening = true,
                orionTranscript = "Listening...",
                orionErrorMessage = null,
                isOrionOverlayVisible = true,
                orionRmsdB = 0f
            )
        }

        try {
            val langCode = _uiState.value.orionVoiceActiveLanguageCode
            orionVoiceEngine.speechRecognitionModule.startListening(
                languageCode = langCode,
                onReady = {
                    val promptText = when (_uiState.value.orionVoiceActiveMode) {
                        "Notes" -> "Dictate your note..."
                        "Chat" -> "Orion is listening..."
                        else -> "How can Orion help you?"
                    }
                    _uiState.update { it.copy(orionTranscript = promptText, orionRmsdB = 0.1f) }
                },
                onHearing = {
                    _uiState.update { it.copy(orionTranscript = "Hearing...") }
                },
                onVolumeChanged = { volumeValue ->
                    _uiState.update { it.copy(orionRmsdB = volumeValue) }
                },
                onPartial = { partialText ->
                    _uiState.update { it.copy(orionTranscript = partialText) }
                },
                onResult = { spokenText ->
                    val cleanedText = com.example.browser.voiceengine.TranscriptEngine.cleanLiveTranscript(spokenText)
                    _uiState.update { it.copy(orionTranscript = cleanedText, orionRmsdB = 0f, isOrionListening = false) }

                    // Stop recognition immediately and close microphone
                    stopActiveSpeechRecognizer()

                    // Conversational Follow-up Check
                    if (conversationalFollowUpType == "music") {
                        conversationalFollowUpType = null
                        val historyItem = VoiceHistoryEntry(text = cleanedText, type = "command")
                        _uiState.update { it.copy(orionVoiceHistory = it.orionVoiceHistory + historyItem) }
                        saveOrionVoiceState()
                        
                        val promptMessage = "Opening YouTube and searching for $cleanedText."
                        playVoiceAssistantResponseSpoken(promptMessage) {
                            try {
                                val searchUrl = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(cleanedText, "UTF-8")
                                navigateTo(searchUrl)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            dismissOrionVoiceOverlay()
                        }
                        return@startListening
                    }

                    // Conversational Context Check (YouTube search focus)
                    val activeUrl = _uiState.value.tabs.find { it.id == _uiState.value.activeTabId }?.url ?: ""
                    val isYouTubeContext = lastVoiceCommandType == "OPEN_YOUTUBE" || activeUrl.contains("youtube.com")
                    val lowerSpoken = cleanedText.lowercase()
                    
                    if (isYouTubeContext && (lowerSpoken.startsWith("search ") || lowerSpoken.contains("songs"))) {
                        val queryText = lowerSpoken.removePrefix("search").removePrefix("on youtube").trim()
                        val promptMessage = "Searching $queryText on YouTube."
                        playVoiceAssistantResponseSpoken(promptMessage) {
                            try {
                                val searchUrl = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(queryText, "UTF-8")
                                navigateTo(searchUrl)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            dismissOrionVoiceOverlay()
                        }
                        return@startListening
                    }

                    // Decide parsing and execution flow based on active mode
                    when (_uiState.value.orionVoiceActiveMode) {
                        "Notes" -> {
                            val historyItem = VoiceHistoryEntry(text = cleanedText, type = "transcript")
                            _uiState.update { it.copy(orionVoiceHistory = it.orionVoiceHistory + historyItem) }
                            saveOrionVoiceState()
                            playVoiceAssistantResponseSpoken("Note transcribed")
                            dismissOrionVoiceOverlay()
                        }
                        "Chat" -> {
                            processOrionChatModeResponse(cleanedText)
                            dismissOrionVoiceOverlay()
                        }
                        else -> { // "Assistant" Command execution
                            val hasWake = cleanedText.lowercase().contains("orion")
                            val type = if (hasWake) "command" else "search"
                            val historyItem = VoiceHistoryEntry(text = cleanedText, type = type)
                            _uiState.update { it.copy(orionVoiceHistory = it.orionVoiceHistory + historyItem) }
                            saveOrionVoiceState()

                            try {
                                val actionRouter = ActionRouter(this@BrowserViewModel)
                                val commandRouter = CommandRouter(getApplication(), actionRouter)
                                TranscriptManager.updateTranscript(cleanedText)
                                commandRouter.routeCommand(cleanedText)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // Fallback to existing search engine
                                val parsed = com.example.searchengine.VoiceSearch.parseVoiceCommand(cleanedText)
                                executeVoiceCommandResult(parsed)
                            }
                            dismissOrionVoiceOverlay()
                        }
                    }
                },
                onErrorOccurred = { errorMsg, errorCode ->
                    _uiState.update { it.copy(orionRmsdB = 0f) }
                    stopActiveSpeechRecognizer()
                    _uiState.update { it.copy(orionTranscript = "Error: $errorMsg", isOrionListening = false) }

                    // Dismiss after a standard reading delay (no restarts occurring)
                    recognitionRestartJob?.cancel()
                    recognitionRestartJob = viewModelScope.launch {
                        kotlinx.coroutines.delay(1500L)
                        dismissOrionVoiceOverlay()
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopActiveSpeechRecognizer() {
        if (::orionVoiceEngine.isInitialized) {
            orionVoiceEngine.speechRecognitionModule.stopListening()
        }
    }

    fun stopOrionVoiceListening() {
        isActiveSessionRunning = false
        activeSessionJob?.cancel()
        activeSessionJob = null
        recognitionRestartJob?.cancel()
        recognitionRestartJob = null
        stopActiveSpeechRecognizer()
        
        _uiState.update { it.copy(isOrionListening = false, orionRmsdB = 0f) }
    }

    fun dismissOrionVoiceOverlay() {
        stopOrionVoiceListening()
        ttsEngine?.stop()
        _uiState.update { it.copy(isOrionOverlayVisible = false, orionErrorMessage = null) }
    }

    fun playVoiceAssistantResponseSpoken(text: String, onSpeakDone: (() -> Unit)? = null) {
        afterSpeakAction = onSpeakDone
        val clean = text.replace(Regex("[*#_`]"), " ").take(400)
        val params = android.os.Bundle()
        params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "orion_speak")
        ttsEngine?.speak(clean, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "orion_speak")
    }

    // --- STATE MANAGEMENT AND SERIALIZATION ---
    
    fun setHistorySearchQuery(query: String) {
        _uiState.update { it.copy(historySearchQuery = query) }
    }
    
    fun setOrionVoiceActiveMode(mode: String) {
        _uiState.update { it.copy(orionVoiceActiveMode = mode) }
    }

    fun setOrionVoiceActiveLanguage(name: String, code: String) {
        _uiState.update { it.copy(orionVoiceActiveLanguage = name, orionVoiceActiveLanguageCode = code) }
        saveOrionVoiceState()
    }

    fun setOrionNoteFormat(format: String) {
        _uiState.update { it.copy(orionNoteFormat = format) }
    }

    fun saveCurrentVoiceNote(title: String = "") {
        val transcript = _uiState.value.orionTranscript
        if (transcript.isEmpty() || transcript == "Listening..." || transcript == "Hearing...") return
        
        val format = _uiState.value.orionNoteFormat
        val timestamp = System.currentTimeMillis()
        val finalTitle = if (title.isNotBlank()) title else {
            "Voice Note ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}"
        }
        
        val content = when (format) {
            "Markdown" -> """
                # $finalTitle
                
                *Transcribed via Orion Voice Intelligence V3*
                *Date: ${java.util.Date(timestamp)}*
                
                ---
                
                $transcript
                
            """.trimIndent()
            "Browser" -> """
                Browser Dictation Context Note:
                Source URL: ${_uiState.value.tabs.find { it.id == _uiState.value.activeTabId }?.url ?: "orion://newtab"}
                
                Transcript Content:
                $transcript
            """.trimIndent()
            else -> transcript
        }

        val note = VoiceNote(
            originalTranscript = transcript,
            noteContent = content,
            format = format,
            timestamp = timestamp,
            title = finalTitle
        )

        _uiState.update {
            it.copy(
                orionVoiceNotes = it.orionVoiceNotes + note,
                orionTranscript = "Saved Note: \"$finalTitle\"!"
            )
        }
        playVoiceAssistantResponseSpoken("Note saved as $format note")
        saveOrionVoiceState()
    }

    fun deleteVoiceNote(id: String) {
        _uiState.update { state ->
            state.copy(orionVoiceNotes = state.orionVoiceNotes.filter { it.id != id })
        }
        saveOrionVoiceState()
    }

    fun clearVoiceHistory() {
        _uiState.update { it.copy(orionVoiceHistory = emptyList()) }
        saveOrionVoiceState()
    }

    fun clearVoiceChat() {
        _uiState.update { it.copy(orionVoiceChatSessions = emptyList()) }
        saveOrionVoiceState()
    }

    fun toggleOrionWakeWord() {
        _uiState.update { state ->
            val updated = !state.orionVoiceWakeWordEnabled
            state.copy(orionVoiceWakeWordEnabled = updated)
        }
        saveOrionVoiceState()
    }

    fun processOrionChatModeResponse(spokenText: String) {
        val userItem = VoiceChatMessage(role = "user", text = spokenText)
        val chatHistory = _uiState.value.orionVoiceChatSessions + userItem
        
        _uiState.update { 
            it.copy(
                orionVoiceChatSessions = chatHistory,
                orionTranscript = "Orion thinking...",
                isOrionListening = false
            )
        }
        
        viewModelScope.launch {
            try {
                val isTranslateRequest = spokenText.lowercase().contains("translate") || spokenText.lowercase().contains("anuvad")
                val basePrompt = if (isTranslateRequest) {
                    val previousAssistantMsg = _uiState.value.orionVoiceChatSessions.lastOrNull { it.role == "assistant" }?.text ?: ""
                    "The user wants you to translate standard text. Translate this previous response cleanly into the target requested language:\n\n\"$previousAssistantMsg\"\n\nOtherwise, translate this prompt: \"$spokenText\""
                } else {
                    spokenText
                }

                // Append active page context if requested
                val finalPrompt = if (spokenText.contains("page") || spokenText.contains("site") || spokenText.contains("website") || spokenText.contains("article")) {
                    var pageContent = "No page loaded"
                    val activeId = _uiState.value.activeTabId
                    val webView = webViewMap[activeId]
                    if (webView != null) {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        webView.post {
                            webView.evaluateJavascript("document.body.innerText") { rawText ->
                                pageContent = rawText ?: ""
                                latch.countDown()
                            }
                        }
                        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                    }
                    "Use this webpage background text to contextually answer the prompt:\n$pageContent\n\nPrompt: $basePrompt"
                } else {
                    basePrompt
                }

                val aiResult = AISummaryEngine.chatSession(emptyList(), finalPrompt, AISettingsManager(getApplication()), getApplication())
                val assistantItem = VoiceChatMessage(role = "assistant", text = aiResult)
                
                _uiState.update { 
                    it.copy(
                        orionVoiceChatSessions = chatHistory + assistantItem,
                        orionTranscript = aiResult
                    )
                }
                playVoiceAssistantResponseSpoken(aiResult) {
                    startOrionVoiceListening(getApplication())
                }
                
                // Save history log
                val historyItem = VoiceHistoryEntry(text = spokenText, type = "chat")
                _uiState.update { it.copy(orionVoiceHistory = it.orionVoiceHistory + historyItem) }
                saveOrionVoiceState()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(orionErrorMessage = "Chat response failed: ${e.message}") }
            }
        }
    }

    fun startOrionWakeWordMonitoring(context: android.content.Context) {
        // No-op: background wake-word service monitoring is removed
    }

    private fun saveOrionVoiceState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save Voice Notes
                val notesArray = org.json.JSONArray()
                _uiState.value.orionVoiceNotes.forEach { note ->
                    val obj = org.json.JSONObject()
                    obj.put("id", note.id)
                    obj.put("originalTranscript", note.originalTranscript)
                    obj.put("noteContent", note.noteContent)
                    obj.put("format", note.format)
                    obj.put("timestamp", note.timestamp)
                    obj.put("title", note.title)
                    notesArray.put(obj)
                }
                prefs.setString("orion_voice_notes_v3", notesArray.toString())

                // Save Voice History
                val historyArray = org.json.JSONArray()
                _uiState.value.orionVoiceHistory.forEach { h ->
                    val obj = org.json.JSONObject()
                    obj.put("id", h.id)
                    obj.put("text", h.text)
                    obj.put("type", h.type)
                    obj.put("timestamp", h.timestamp)
                    historyArray.put(obj)
                }
                prefs.setString("orion_voice_history_v3", historyArray.toString())

                // Save Voice Chat Sessions
                val chatArray = org.json.JSONArray()
                _uiState.value.orionVoiceChatSessions.forEach { c ->
                    val obj = org.json.JSONObject()
                    obj.put("id", c.id)
                    obj.put("role", c.role)
                    obj.put("text", c.text)
                    obj.put("timestamp", c.timestamp)
                    chatArray.put(obj)
                }
                prefs.setString("orion_voice_chats_v3", chatArray.toString())
                
                // Save settings
                prefs.setString("orion_voice_lang", _uiState.value.orionVoiceActiveLanguage)
                prefs.setString("orion_voice_lang_code", _uiState.value.orionVoiceActiveLanguageCode)
                prefs.setBoolean("orion_voice_auto_detect", _uiState.value.orionVoiceAutoDetectLanguage)
                prefs.setBoolean("orion_voice_wakeword_enabled", _uiState.value.orionVoiceWakeWordEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadOrionVoiceState() {
        try {
            val notesStr = prefs.getString("orion_voice_notes_v3", "")
            val loadedNotes = mutableListOf<VoiceNote>()
            if (notesStr.isNotEmpty()) {
                val array = org.json.JSONArray(notesStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedNotes.add(VoiceNote(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        originalTranscript = obj.optString("originalTranscript", ""),
                        noteContent = obj.optString("noteContent", ""),
                        format = obj.optString("format", "Text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        title = obj.optString("title", "")
                    ))
                }
            }

            val histStr = prefs.getString("orion_voice_history_v3", "")
            val loadedHist = mutableListOf<VoiceHistoryEntry>()
            if (histStr.isNotEmpty()) {
                val array = org.json.JSONArray(histStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedHist.add(VoiceHistoryEntry(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        type = obj.optString("type", "command"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
            }

            val chatStr = prefs.getString("orion_voice_chats_v3", "")
            val loadedChat = mutableListOf<VoiceChatMessage>()
            if (chatStr.isNotEmpty()) {
                val array = org.json.JSONArray(chatStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedChat.add(VoiceChatMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        role = obj.optString("role", "user"),
                        text = obj.optString("text", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
            }

            _uiState.update {
                it.copy(
                    orionVoiceNotes = loadedNotes,
                    orionVoiceHistory = loadedHist,
                    orionVoiceChatSessions = loadedChat,
                    orionVoiceActiveLanguage = prefs.getString("orion_voice_lang", "English"),
                    orionVoiceActiveLanguageCode = prefs.getString("orion_voice_lang_code", "en-US"),
                    orionVoiceAutoDetectLanguage = prefs.getBoolean("orion_voice_auto_detect", true),
                    orionVoiceWakeWordEnabled = prefs.getBoolean("orion_voice_wakeword_enabled", true)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speakPageContentAloud(text: String) {
        val sentences = text.split(Regex("[.!?\n]"))
            .map { it.trim() }
            .filter { it.length > 5 }
            .take(50)
            
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
        }
    }

    fun extractPageTextForAI(callback: (String) -> Unit) {
        val activeId = _uiState.value.activeTabId
        val webView = webViewMap[activeId]
        if (webView == null) {
            callback("No active web page loaded.")
            return
        }
        webView.evaluateJavascript(WebsiteContextExtractor.JS_EXTRACT_SCRIPT) { res ->
            val context = WebsiteContextExtractor.parseJsonToContext(res)
            callback(context.toFormattedPromptString())
        }
    }

    fun executeVoiceCommandResult(result: com.example.searchengine.VoiceCommandResult) {
        val payload = result.payload
        Log.i("OrionAssistant", "Executing voice action: ${result.actionType} with payload: $payload")
        _uiState.update { it.copy(orionTranscript = "Executing action: ${result.actionType}...") }
        
        viewModelScope.launch {
            when (result.actionType) {
                com.example.searchengine.VoiceActionType.BACK -> {
                    val activeId = _uiState.value.activeTabId
                    webViewMap[activeId]?.let {
                        if (it.canGoBack()) {
                            _uiState.update { state -> state.copy(orionTranscript = "Navigating back...") }
                            playVoiceAssistantResponseSpoken("Going back")
                            it.goBack()
                            dismissOrionVoiceOverlay()
                        } else {
                            _uiState.update { state -> state.copy(orionErrorMessage = "Cannot go back: No history found") }
                            playVoiceAssistantResponseSpoken("Nowhere to go back")
                        }
                    } ?: run {
                        _uiState.update { state -> state.copy(orionErrorMessage = "No active page") }
                    }
                }
                com.example.searchengine.VoiceActionType.FORWARD -> {
                    val activeId = _uiState.value.activeTabId
                    webViewMap[activeId]?.let {
                        if (it.canGoForward()) {
                            _uiState.update { state -> state.copy(orionTranscript = "Navigating forward...") }
                            playVoiceAssistantResponseSpoken("Going forward")
                            it.goForward()
                            dismissOrionVoiceOverlay()
                        } else {
                            _uiState.update { state -> state.copy(orionErrorMessage = "Cannot go forward: No history found") }
                            playVoiceAssistantResponseSpoken("Nowhere to go forward")
                        }
                    } ?: run {
                        _uiState.update { state -> state.copy(orionErrorMessage = "No active page") }
                    }
                }
                com.example.searchengine.VoiceActionType.REFRESH -> {
                    val activeId = _uiState.value.activeTabId
                    playVoiceAssistantResponseSpoken("Refreshing webpage")
                    webViewMap[activeId]?.reload()
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.NEW_TAB -> {
                    playVoiceAssistantResponseSpoken("New tab opened")
                    addNewTab()
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.CLOSE_TAB -> {
                    val activeId = _uiState.value.activeTabId
                    playVoiceAssistantResponseSpoken("Tab closed")
                    if (activeId.isNotEmpty()) {
                        closeTab(activeId)
                    }
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.OPEN_INCOGNITO -> {
                    playVoiceAssistantResponseSpoken("Opening incognito private window")
                    addNewTab(isIncognito = true)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.CLOSE_INCOGNITO -> {
                    playVoiceAssistantResponseSpoken("Closing all private windows")
                    closeAllIncognitoTabs()
                    _uiState.update { it.copy(orionTranscript = "Closed all private tab windows") }
                    kotlinx.coroutines.delay(1200L)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.OPEN_DOWNLOADS -> {
                    playVoiceAssistantResponseSpoken("Opening downloads hub")
                    setDownloadsOpen(true)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.OPEN_HISTORY -> {
                    playVoiceAssistantResponseSpoken("Opening browser history")
                    _uiState.update { it.copy(historySearchQuery = "") }
                    setHistoryOpen(true)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.OPEN_SETTINGS -> {
                    playVoiceAssistantResponseSpoken("Opening settings panel")
                    setSettingsOpen(true)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.OPEN_BOOKMARKS -> {
                    playVoiceAssistantResponseSpoken("Opening saved bookmarks")
                    setBookmarksOpen(true)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.NAVIGATE -> {
                    playVoiceAssistantResponseSpoken("Navigating to " + payload.replace("https://www.", "").replace("https://", "").replace("http://", "").take(25))
                    navigateTo(payload)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.SEARCH -> {
                    playVoiceAssistantResponseSpoken("Searching google for " + payload.take(30))
                    val searchEngine = com.example.searchengine.SearchEngineImpl()
                    val searchUrl = searchEngine.buildSearchUrl(payload, "Google")
                    navigateTo(searchUrl)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.SEARCH_CURRENT_SITE -> {
                    if (payload.isNotBlank()) {
                        _uiState.update { it.copy(findInPageActive = true) }
                        findInPageSearch(payload)
                        playVoiceAssistantResponseSpoken("Searching page entries for " + payload.take(30))
                    }
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.CREATE_TAB_GROUP -> {
                    if (payload.isNotBlank()) {
                        val activeId = _uiState.value.activeTabId
                        if (activeId.isNotEmpty()) {
                            playVoiceAssistantResponseSpoken("Created tab group " + payload)
                            moveTabToGroup(activeId, payload, 0xFF4285F4)
                            _uiState.update { it.copy(orionTranscript = "Created tab group: $payload") }
                            kotlinx.coroutines.delay(1200L)
                        }
                    }
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.SWITCH_TAB_GROUP -> {
                    if (payload.isNotBlank()) {
                        val targetTab = _uiState.value.tabs.find { it.groupName?.equals(payload, ignoreCase = true) == true }
                        if (targetTab != null) {
                            playVoiceAssistantResponseSpoken("Switching tab group to " + payload)
                            selectTab(targetTab.id)
                            _uiState.update { it.copy(orionTranscript = "Switched to tab group: $payload") }
                            kotlinx.coroutines.delay(1200L)
                        } else {
                            playVoiceAssistantResponseSpoken("Group not found")
                            _uiState.update { it.copy(orionErrorMessage = "No active group named '$payload' found") }
                            kotlinx.coroutines.delay(1500L)
                        }
                    }
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.OPEN_LAST_CLOSED_TAB -> {
                    val closed = _uiState.value.recentlyClosedTabs
                    if (closed.isNotEmpty()) {
                        playVoiceAssistantResponseSpoken("Restoring last closed tab")
                        reopenClosedTab(closed.last())
                        _uiState.update { it.copy(orionTranscript = "Reopened last closed tab") }
                        kotlinx.coroutines.delay(1000L)
                    } else {
                        playVoiceAssistantResponseSpoken("No closed tabs to restore")
                        _uiState.update { it.copy(orionErrorMessage = "No recent closed history matches") }
                        kotlinx.coroutines.delay(1500L)
                    }
                    dismissOrionVoiceOverlay()
                }
                
                // AI features
                com.example.searchengine.VoiceActionType.SUMMARIZE_PAGE -> {
                    playVoiceAssistantResponseSpoken("Reading page to compile summary")
                    _uiState.update { it.copy(orionTranscript = "Reading webpage text for summarization...") }
                    extractPageTextForAI { text ->
                        viewModelScope.launch {
                            _uiState.update { it.copy(orionTranscript = "Asking Orion AI to summarize webpage...") }
                            try {
                                val summary = AISummaryEngine.analyzePage(text, AISettingsManager(getApplication()), getApplication())
                                _uiState.update { it.copy(orionTranscript = summary) }
                                playVoiceAssistantResponseSpoken(summary)
                            } catch (e: Exception) {
                                _uiState.update { it.copy(orionErrorMessage = "Failed to run AI summarizer: ${e.message}") }
                            }
                        }
                    }
                }
                com.example.searchengine.VoiceActionType.EXPLAIN_PAGE -> {
                    playVoiceAssistantResponseSpoken("Reading page to compile simple explanation")
                    _uiState.update { it.copy(orionTranscript = "Reading page text for analysis...") }
                    extractPageTextForAI { text ->
                        viewModelScope.launch {
                            _uiState.update { it.copy(orionTranscript = "Asking Orion AI to explain page topics...") }
                            try {
                                val prompt = "Explain the core concepts and lessons from this webpage content simply and clearly for a reader:\n\n$text"
                                val explanation = AISummaryEngine.chatSession(emptyList(), prompt, AISettingsManager(getApplication()), getApplication())
                                _uiState.update { it.copy(orionTranscript = explanation) }
                                playVoiceAssistantResponseSpoken(explanation)
                            } catch (e: Exception) {
                                _uiState.update { it.copy(orionErrorMessage = "Failed to run AI explainer: ${e.message}") }
                            }
                        }
                    }
                }
                com.example.searchengine.VoiceActionType.TRANSLATE_PAGE -> {
                    _uiState.update { it.copy(orionTranscript = "Initiating page translation to " + payload + "...") }
                    val targetCode = when (payload.trim().lowercase()) {
                        "hindi" -> "hi"
                        "tamil" -> "ta"
                        "telugu" -> "te"
                        "marathi" -> "mr"
                        "bengali" -> "bn"
                        "punjabi" -> "pa"
                        "gujarati" -> "gu"
                        "spanish" -> "es"
                        "french" -> "fr"
                        else -> "hi"
                    }
                    playVoiceAssistantResponseSpoken("Translating page to " + payload)
                    translateActivePage(targetCode)
                    kotlinx.coroutines.delay(1500L)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.READ_PAGE_ALOUD -> {
                    playVoiceAssistantResponseSpoken("Reading page content aloud")
                    _uiState.update { it.copy(orionTranscript = "Processing page paragraphs...") }
                    val activeId = _uiState.value.activeTabId
                    val webView = webViewMap[activeId]
                    if (webView != null) {
                        webView.evaluateJavascript("document.body.innerText") { rawText ->
                            val cleanText = rawText?.trim()?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                            if (cleanText.isNotBlank()) {
                                speakPageContentAloud(cleanText)
                            }
                        }
                    }
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.ANALYZE_WEBSITE -> {
                    playVoiceAssistantResponseSpoken("Running technical architecture overview on target page")
                    _uiState.update { it.copy(orionTranscript = "Reading page tags for technical overview...") }
                    extractPageTextForAI { text ->
                        viewModelScope.launch {
                            _uiState.update { it.copy(orionTranscript = "Analyzing performance, usability, and architecture...") }
                            try {
                                val prompt = "Provide a developer metrics and architecture analysis of this webpage content. Outline sections, link densities, and estimated value:\n\n$text"
                                val analysis = AISummaryEngine.chatSession(emptyList(), prompt, AISettingsManager(getApplication()), getApplication())
                                _uiState.update { it.copy(orionTranscript = analysis) }
                                playVoiceAssistantResponseSpoken(analysis)
                            } catch (e: Exception) {
                                _uiState.update { it.copy(orionErrorMessage = "Failed to run AI analyzer: ${e.message}") }
                            }
                        }
                    }
                }
                com.example.searchengine.VoiceActionType.VOICE_SEARCH_HISTORY -> {
                    if (payload.isNotBlank()) {
                        playVoiceAssistantResponseSpoken("Searching browser history logs for " + payload)
                        _uiState.update { it.copy(historySearchQuery = payload) }
                        setHistoryOpen(true)
                    }
                    dismissOrionVoiceOverlay()
                }
                
                // Media features
                com.example.searchengine.VoiceActionType.PLAY_VIDEO -> {
                    val activeId = _uiState.value.activeTabId
                    playVoiceAssistantResponseSpoken("Resuming video media playback")
                    webViewMap[activeId]?.evaluateJavascript("try { document.querySelectorAll('video').forEach(v => v.play()); 'SUCCESS'; } catch(e) { e.message; }") {
                        _uiState.update { it.copy(orionTranscript = "Media play initiated") }
                    }
                    kotlinx.coroutines.delay(1000L)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.PAUSE_VIDEO -> {
                    val activeId = _uiState.value.activeTabId
                    playVoiceAssistantResponseSpoken("Video media playback paused")
                    webViewMap[activeId]?.evaluateJavascript("try { document.querySelectorAll('video').forEach(v => v.pause()); 'SUCCESS'; } catch(e) { e.message; }") {
                        _uiState.update { it.copy(orionTranscript = "Media pause initiated") }
                    }
                    kotlinx.coroutines.delay(1000L)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.CHANGE_PLAYBACK_SPEED -> {
                    val speedVal = payload.toFloatOrNull() ?: 1.5f
                    val activeId = _uiState.value.activeTabId
                    playVoiceAssistantResponseSpoken("Adjusting video speed rate to " + speedVal)
                    webViewMap[activeId]?.evaluateJavascript("try { document.querySelectorAll('video').forEach(v => v.playbackRate = $speedVal); 'SUCCESS'; } catch(e) { e.message; }") {
                        _uiState.update { it.copy(orionTranscript = "Media rate configured to ${speedVal}x") }
                    }
                    kotlinx.coroutines.delay(1200L)
                    dismissOrionVoiceOverlay()
                }
                com.example.searchengine.VoiceActionType.DOWNLOAD_VIDEO -> {
                    val activeId = _uiState.value.activeTabId
                    playVoiceAssistantResponseSpoken("Locating live media streams to execute download")
                    webViewMap[activeId]?.evaluateJavascript("(function() { var v = document.querySelector('video'); return v ? (v.src || v.currentSrc || '') : ''; })()") { rawUrl ->
                        val videoUrl = rawUrl?.trim()?.removeSurrounding("\"") ?: ""
                        if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                            playVoiceAssistantResponseSpoken("Video stream found. Download started.")
                            startDownload(videoUrl, "video_download.mp4", "video/mp4", "")
                            _uiState.update { it.copy(orionTranscript = "Video download scheduled successfully") }
                        } else {
                            playVoiceAssistantResponseSpoken("Could not identify any streaming buffers")
                            _uiState.update { it.copy(orionErrorMessage = "No webpage video tags match active streaming buffers") }
                        }
                    }
                    kotlinx.coroutines.delay(1500L)
                    dismissOrionVoiceOverlay()
                }
            }
        }
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

// Separate Session Engines
class NormalSessionStore(private val context: Context, private val viewModel: BrowserViewModel) {
    fun saveSession(tabs: List<TabState>, activeTabId: String) {
        viewModel.saveTabsState()
    }
    fun restoreSession() {
        viewModel.restoreTabsState()
    }
}

class IncognitoSessionStore {
    private val incognitoTabs = mutableListOf<TabState>()
    fun saveSession(tabs: List<TabState>) {
        // ALWAYS STORES NOTHING PERMANENTLY (as per requirements)
    }
    fun getTabs(): List<TabState> = incognitoTabs.toList()
    fun clearSession() {
        incognitoTabs.clear()
    }
}

class NormalCookieStore(private val context: Context) {
    fun acceptCookies(webView: android.webkit.WebView) {
        try {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun flush() {
        try {
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class IncognitoCookieStore {
    fun setupCookies(webView: android.webkit.WebView) {
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun clearCookies() {
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class NormalCacheStore {
    fun setupCache(webView: android.webkit.WebView) {
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
    }
}

class IncognitoCacheStore {
    fun setupCache(webView: android.webkit.WebView) {
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        webView.settings.databaseEnabled = false
        webView.settings.domStorageEnabled = true
    }
    fun clearCache(webView: android.webkit.WebView) {
        webView.clearCache(true)
        webView.clearFormData()
        webView.clearHistory()
    }
    fun clearAllIncognitoCache(webViews: List<android.webkit.WebView>) {
        webViews.forEach {
            it.clearCache(true)
            it.clearFormData()
            it.clearHistory()
        }
    }
}

class NormalHistoryStore(private val repository: com.example.data.BrowserRepository) {
    suspend fun addHistory(url: String, title: String) {
        repository.addHistory(url, title)
    }
}

class IncognitoHistoryStore {
    suspend fun addHistory(url: String, title: String) {
        // ALWAYS STORES NOTHING PERMANENTLY (as per requirements)
    }
}
