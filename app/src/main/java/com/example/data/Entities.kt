package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val timestamp: Long,
    val visitCount: Int = 1
)

@Entity(tableName = "top_sites")
data class TopSite(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val isCustom: Boolean = false,
    val isHidden: Boolean = false,
    val visitCount: Int = 0
)
