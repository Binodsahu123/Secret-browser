package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)

    private val _isJavaScriptEnabled = MutableStateFlow(prefs.getBoolean("js_enabled", true))
    val isJavaScriptEnabled: StateFlow<Boolean> = _isJavaScriptEnabled

    private val _isHardwareAccelerationEnabled = MutableStateFlow(prefs.getBoolean("hardware_acc_enabled", true))
    val isHardwareAccelerationEnabled: StateFlow<Boolean> = _isHardwareAccelerationEnabled

    private val _newTabWallpaper = MutableStateFlow(prefs.getString("new_tab_wallpaper", "Frosted Glass") ?: "Frosted Glass")
    val newTabWallpaper: StateFlow<String> = _newTabWallpaper

    private val _readerFontSize = MutableStateFlow(prefs.getInt("reader_font_size", 16))
    val readerFontSize: StateFlow<Int> = _readerFontSize

    fun setJavaScriptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("js_enabled", enabled).apply()
        _isJavaScriptEnabled.value = enabled
    }

    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hardware_acc_enabled", enabled).apply()
        _isHardwareAccelerationEnabled.value = enabled
    }

    fun setNewTabWallpaper(wallpaper: String) {
        prefs.edit().putString("new_tab_wallpaper", wallpaper).apply()
        _newTabWallpaper.value = wallpaper
    }

    fun setReaderFontSize(size: Int) {
        prefs.edit().putInt("reader_font_size", size).apply()
        _readerFontSize.value = size
    }
}
