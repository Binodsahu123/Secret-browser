package com.example.data

import android.content.Context
import android.content.SharedPreferences

data class Bookmark(val url: String, val title: String)
data class HistoryItem(val id: Long, val url: String, val title: String, val timestamp: Long)
data class TopSite(val url: String, val title: String, val iconUrl: String? = null, val isCustom: Boolean = false, val isHidden: Boolean = false)
data class DownloadItem(val id: Long, val fileName: String, val url: String, val mimeType: String, val status: String, val timestamp: Long = System.currentTimeMillis()) {
    val downloadId: Long get() = id
}
data class ArticleCacheEntity(val url: String, val title: String, val description: String, val imageUrl: String? = null, val category: String = "", val publishedAt: String = "", val source: String = "", val cachedAt: Long = System.currentTimeMillis())

class PreferenceManager(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("browser_preferences", Context.MODE_PRIVATE)

    val isJavaScriptEnabled: Boolean
        get() = sp.getBoolean("js_enabled", true)

    val isHardwareAccelerationEnabled: Boolean
        get() = sp.getBoolean("hardware_acceleration", true)

    val newTabWallpaper: String
        get() = sp.getString("new_tab_wallpaper", "Frosted Glass") ?: "Frosted Glass"

    val readerFontSize: Int
        get() = sp.getInt("reader_font_size", 16)

    fun getString(key: String, default: String): String {
        return sp.getString(key, default) ?: default
    }

    fun setString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return sp.getBoolean(key, default)
    }

    fun setBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int {
        return sp.getInt(key, default)
    }

    fun setInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
    }

    fun setReaderFontSize(size: Int) {
        sp.edit().putInt("reader_font_size", size).apply()
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        sp.edit().putBoolean("js_enabled", enabled).apply()
    }

    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        sp.edit().putBoolean("hardware_acceleration", enabled).apply()
    }

    fun setNewTabWallpaper(wallpaper: String) {
        sp.edit().putString("new_tab_wallpaper", wallpaper).apply()
    }
}
