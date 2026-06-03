package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getHistoryByUrl(url: String): HistoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(historyItem: HistoryItem)

    @Update
    suspend fun updateHistory(historyItem: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Int)
}

@Dao
interface TopSiteDao {
    @Query("SELECT * FROM top_sites")
    fun getAllTopSites(): Flow<List<TopSite>>

    @Query("SELECT * FROM top_sites WHERE url = :url LIMIT 1")
    suspend fun getTopSiteByUrl(url: String): TopSite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopSite(topSite: TopSite)

    @Update
    suspend fun updateTopSite(topSite: TopSite)

    @Delete
    suspend fun deleteTopSite(topSite: TopSite)
}
