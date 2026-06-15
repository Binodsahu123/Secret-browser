package com.example.browser

import android.content.Context
import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class BrowserRepository(private val context: Context) {
    private val sp = context.getSharedPreferences("browser_repo_persistent_data", Context.MODE_PRIVATE)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _customShortcuts = MutableStateFlow<List<TopSite>>(emptyList())
    private val _hiddenTopSites = MutableStateFlow<Set<String>>(emptySet())
    private val _mergedTopSites = MutableStateFlow<List<TopSite>>(emptyList())

    private val _articles = MutableStateFlow<Map<String, List<ArticleCacheEntity>>>(emptyMap())

    init {
        loadData()
        updateMergedTopSites()
    }

    private fun loadData() {
        // Bookmarks loading
        val bmJson = sp.getString("bookmarks", "[]") ?: "[]"
        val bmList = mutableListOf<Bookmark>()
        try {
            val arr = JSONArray(bmJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                bmList.add(Bookmark(obj.getString("url"), obj.getString("title")))
            }
        } catch (e: Exception) { e.printStackTrace() }
        _bookmarks.value = bmList

        // History loading
        val histJson = sp.getString("history", "[]") ?: "[]"
        val histList = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(histJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                histList.add(HistoryItem(
                    obj.optLong("id", System.currentTimeMillis()),
                    obj.getString("url"),
                    obj.getString("title"),
                    obj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        _history.value = histList.sortedByDescending { it.timestamp }

        // Custom Shortcuts loading
        val csJson = sp.getString("custom_shortcuts", "[]") ?: "[]"
        val csList = mutableListOf<TopSite>()
        try {
            val arr = JSONArray(csJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                csList.add(TopSite(
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    iconUrl = obj.optString("iconUrl", null),
                    isCustom = true,
                    isHidden = false
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        _customShortcuts.value = csList

        // Hidden Top Sites loading
        val hiddenSet = sp.getStringSet("hidden_top_sites", emptySet()) ?: emptySet()
        _hiddenTopSites.value = hiddenSet

        // Downloads loading
        val dlJson = sp.getString("downloads", "[]") ?: "[]"
        val dlList = mutableListOf<DownloadItem>()
        try {
            val arr = JSONArray(dlJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dlList.add(DownloadItem(
                    id = obj.optLong("id", System.currentTimeMillis()),
                    fileName = obj.getString("fileName"),
                    url = obj.getString("url"),
                    mimeType = obj.getString("mimeType"),
                    status = obj.getString("status")
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        _downloads.value = dlList
    }

    private fun saveData() {
        val editor = sp.edit()
        
        // Save Bookmarks
        val bmArr = JSONArray()
        _bookmarks.value.forEach {
            val obj = JSONObject()
            obj.put("url", it.url)
            obj.put("title", it.title)
            bmArr.put(obj)
        }
        editor.putString("bookmarks", bmArr.toString())

        // Save History
        val histArr = JSONArray()
        _history.value.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("url", it.url)
            obj.put("title", it.title)
            obj.put("timestamp", it.timestamp)
            histArr.put(obj)
        }
        editor.putString("history", histArr.toString())

        // Save Custom Shortcuts
        val csArr = JSONArray()
        _customShortcuts.value.forEach {
            val obj = JSONObject()
            obj.put("url", it.url)
            obj.put("title", it.title)
            obj.put("iconUrl", it.iconUrl ?: "")
            csArr.put(obj)
        }
        editor.putString("custom_shortcuts", csArr.toString())

        // Save Hidden Top Sites
        editor.putStringSet("hidden_top_sites", _hiddenTopSites.value)

        // Save Downloads
        val dlArr = JSONArray()
        _downloads.value.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("fileName", it.fileName)
            obj.put("url", it.url)
            obj.put("mimeType", it.mimeType)
            obj.put("status", it.status)
            dlArr.put(obj)
        }
        editor.putString("downloads", dlArr.toString())

        editor.apply()
        updateMergedTopSites()
    }

    private fun updateMergedTopSites() {
        val defaultSites = listOf(
            TopSite("https://www.google.com", "Google"),
            TopSite("https://www.youtube.com", "YouTube"),
            TopSite("https://www.facebook.com", "Facebook"),
            TopSite("https://www.wikipedia.org", "Wikipedia"),
            TopSite("https://www.reddit.com", "Reddit")
        )

        val mergedList = mutableListOf<TopSite>()
        // Add custom shortcuts first
        _customShortcuts.value.forEach {
            if (!_hiddenTopSites.value.contains(it.url)) {
                mergedList.add(it)
            }
        }
        // Add defaults if they are not hidden and not already added as custom
        defaultSites.forEach { default ->
            if (!_hiddenTopSites.value.contains(default.url) && mergedList.none { it.url == default.url }) {
                mergedList.add(default)
            }
        }
        _mergedTopSites.value = mergedList
    }

    fun getMergedTopSites(): StateFlow<List<TopSite>> {
        return _mergedTopSites.asStateFlow()
    }

    suspend fun addHistory(url: String, title: String) {
        if (url.startsWith("orion://")) return
        val item = HistoryItem(
            id = System.currentTimeMillis(),
            url = url,
            title = title,
            timestamp = System.currentTimeMillis()
        )
        val filtered = _history.value.filter { it.url != url }
        _history.value = (listOf(item) + filtered).take(100)
        saveData()
    }

    suspend fun isBookmarked(url: String): Boolean {
        return _bookmarks.value.any { it.url == url }
    }

    suspend fun removeBookmark(url: String) {
        _bookmarks.value = _bookmarks.value.filter { it.url != url }
        saveData()
    }

    suspend fun addBookmark(url: String, title: String) {
        if (_bookmarks.value.any { it.url == url }) return
        val bm = Bookmark(url, title)
        _bookmarks.value = _bookmarks.value + bm
        saveData()
    }

    suspend fun clearHistory() {
        _history.value = emptyList()
        saveData()
    }

    suspend fun deleteHistoryItem(id: Long) {
        _history.value = _history.value.filter { it.id != id }
        saveData()
    }

    suspend fun deleteHistorySince(cutoff: Long) {
        _history.value = _history.value.filter { it.timestamp < cutoff }
        saveData()
    }

    suspend fun addCustomShortcut(url: String, title: String) {
        if (_customShortcuts.value.any { it.url == url }) return
        val topSite = TopSite(
            url = url,
            title = title,
            isCustom = true,
            isHidden = false
        )
        _customShortcuts.value = _customShortcuts.value + topSite
        saveData()
    }

    suspend fun deleteCustomShortcut(url: String) {
        _customShortcuts.value = _customShortcuts.value.filter { it.url != url }
        saveData()
    }

    suspend fun hideTopSite(url: String) {
        _hiddenTopSites.value = _hiddenTopSites.value + url
        saveData()
    }

    suspend fun searchHistory(input: String, limit: Int): List<HistoryItem> {
        return _history.value.filter {
            it.title.contains(input, ignoreCase = true) || it.url.contains(input, ignoreCase = true)
        }.take(limit)
    }

    suspend fun searchBookmarks(input: String, limit: Int): List<Bookmark> {
        return _bookmarks.value.filter {
            it.title.contains(input, ignoreCase = true) || it.url.contains(input, ignoreCase = true)
        }.take(limit)
    }

    fun getArticlesByCategory(category: String): Flow<List<ArticleCacheEntity>> {
        return _articles.map { it[category] ?: emptyList() }
    }

    suspend fun clearArticlesByCategory(category: String) {
        val current = _articles.value.toMutableMap()
        current.remove(category)
        _articles.value = current
    }

    suspend fun saveArticles(articlesList: List<ArticleCacheEntity>) {
        if (articlesList.isEmpty()) return
        val category = articlesList.first().category
        val current = _articles.value.toMutableMap()
        current[category] = articlesList
        _articles.value = current
    }

    suspend fun saveDownloadToDb(downloadId: Long, fileName: String, url: String, mimeType: String, status: String) {
        val item = DownloadItem(downloadId, fileName, url, mimeType, status)
        _downloads.value = _downloads.value.filter { it.id != downloadId } + item
        saveData()
    }

    suspend fun updateDownloadStatusInDb(downloadId: Long, status: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == downloadId) it.copy(status = status) else it
        }
        saveData()
    }

    suspend fun updateDownloadFileNameInDb(downloadId: Long, newName: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == downloadId) it.copy(fileName = newName) else it
        }
        saveData()
    }

    suspend fun deleteDownloadFromDb(downloadId: Long) {
        _downloads.value = _downloads.value.filter { it.id != downloadId }
        saveData()
    }
}
