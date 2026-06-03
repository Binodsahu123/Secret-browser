package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.net.URI

class BrowserRepository(private val db: BrowserDatabase) {
    private val bookmarkDao = db.bookmarkDao()
    private val historyDao = db.historyDao()
    private val topSiteDao = db.topSiteDao()
    private val articleDao = db.articleDao()

    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    val history: Flow<List<HistoryItem>> = historyDao.getAllHistory()

    fun getArticlesByCategory(category: String): Flow<List<ArticleCacheEntity>> {
        return articleDao.getArticlesByCategory(category)
    }

    suspend fun saveArticles(articles: List<ArticleCacheEntity>) {
        articleDao.insertArticles(articles)
    }

    suspend fun clearArticlesByCategory(category: String) {
        articleDao.deleteArticlesByCategory(category)
    }

    suspend fun addBookmark(url: String, title: String) {
        if (url.isBlank()) return
        val existing = bookmarkDao.getBookmarkByUrl(url)
        if (existing == null) {
            bookmarkDao.insertBookmark(Bookmark(url = url, title = title))
        }
    }

    suspend fun removeBookmark(url: String) {
        bookmarkDao.deleteBookmarkByUrl(url)
    }

    suspend fun isBookmarked(url: String): Boolean {
        return bookmarkDao.getBookmarkByUrl(url) != null
    }

    fun getRecentHistory(limit: Int): Flow<List<HistoryItem>> {
        return historyDao.getRecentHistory(limit)
    }

    suspend fun addHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("about:")) return
        val existing = historyDao.getHistoryByUrl(url)
        if (existing != null) {
            historyDao.updateHistory(
                existing.copy(
                    timestamp = System.currentTimeMillis(),
                    visitCount = existing.visitCount + 1
                )
            )
        } else {
            historyDao.insertHistory(
                HistoryItem(
                    url = url,
                    title = title.ifBlank { url },
                    timestamp = System.currentTimeMillis(),
                    visitCount = 1
                )
            )
        }
    }

    suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }

    suspend fun deleteHistoryItem(id: Int) {
        historyDao.deleteHistoryItem(id)
    }

    suspend fun deleteHistorySince(timestamp: Long) {
        historyDao.deleteHistorySince(timestamp)
    }

    // Custom Top Sites and hidden states
    suspend fun addCustomShortcut(url: String, title: String) {
        if (url.isBlank()) return
        val existing = topSiteDao.getTopSiteByUrl(url)
        if (existing != null) {
            topSiteDao.updateTopSite(existing.copy(title = title, isCustom = true, isHidden = false))
        } else {
            topSiteDao.insertTopSite(TopSite(url = url, title = title, isCustom = true))
        }
    }

    suspend fun hideTopSite(url: String) {
        if (url.isBlank()) return
        val existing = topSiteDao.getTopSiteByUrl(url)
        if (existing != null) {
            topSiteDao.updateTopSite(existing.copy(isHidden = true))
        } else {
            topSiteDao.insertTopSite(TopSite(url = url, title = "", isCustom = false, isHidden = true))
        }
    }

    suspend fun deleteCustomShortcut(url: String) {
        val existing = topSiteDao.getTopSiteByUrl(url)
        if (existing != null && existing.isCustom) {
            topSiteDao.deleteTopSite(existing)
        }
    }

    fun getMergedTopSites(): Flow<List<TopSite>> {
        return combine(topSiteDao.getAllTopSites(), historyDao.getAllHistory()) { customSites, historyItems ->
            val customActive = customSites.filter { it.isCustom && !it.isHidden }
            val hiddenUrls = customSites.filter { it.isHidden }.map { it.url.lowercase().trim() }.toSet()

            val historyGrouped = historyItems
                .filter { item ->
                    val urlLower = item.url.lowercase().trim()
                    !hiddenUrls.contains(urlLower) &&
                            customActive.none { it.url.lowercase().trim() == urlLower }
                }
                .groupBy { it.url }
                .map { (url, items) ->
                    val totalVisits = items.sumOf { it.visitCount }
                    val title = items.firstOrNull()?.title ?: getDomainName(url)
                    TopSite(url = url, title = title, isCustom = false, isHidden = false, visitCount = totalVisits)
                }
                .sortedByDescending { it.visitCount }

            val combined = (customActive + historyGrouped).take(8)

            if (combined.size < 8) {
                val defaultSites = listOf(
                    TopSite(url = "https://www.google.com", title = "Google", isCustom = false),
                    TopSite(url = "https://www.wikipedia.org", title = "Wikipedia", isCustom = false),
                    TopSite(url = "https://github.com", title = "GitHub", isCustom = false),
                    TopSite(url = "https://stackoverflow.com", title = "StackOverflow", isCustom = false),
                    TopSite(url = "https://www.reddit.com", title = "Reddit", isCustom = false),
                    TopSite(url = "https://www.youtube.com", title = "YouTube", isCustom = false),
                    TopSite(url = "https://news.google.com", title = "Google News", isCustom = false),
                    TopSite(url = "https://www.bbc.com", title = "BBC", isCustom = false)
                )
                val filled = combined.toMutableList()
                for (defaultSite in defaultSites) {
                    if (filled.size >= 8) break
                    val defaultUrlLower = defaultSite.url.lowercase().trim()
                    val alreadyExists = filled.any { it.url.lowercase().trim() == defaultUrlLower }
                    val isHidden = hiddenUrls.contains(defaultUrlLower)
                    if (!alreadyExists && !isHidden) {
                        filled.add(defaultSite)
                    }
                }
                filled
            } else {
                combined
            }
        }
    }

    private fun getDomainName(url: String): String {
        return try {
            val uri = URI(url)
            val domain = uri.host ?: ""
            if (domain.startsWith("www.")) domain.substring(4) else domain
        } catch (e: Exception) {
            url
        }
    }
}
