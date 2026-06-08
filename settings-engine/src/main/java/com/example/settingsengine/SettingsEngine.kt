package com.example.settingsengine

import android.content.Context

interface SettingsEngine {
    fun getPreference(key: String, defaultValue: String): String
    fun setPreference(key: String, value: String)
}

class SettingsEngineImpl(private val context: Context) : SettingsEngine {
    private val prefs = context.getSharedPreferences("orion_browser_settings_engine", Context.MODE_PRIVATE)

    override fun getPreference(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    override fun setPreference(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

class ThemeManager(private val context: Context) {
    fun getTheme(): String {
        return context.getSharedPreferences("orion_browser_settings_engine", Context.MODE_PRIVATE)
            .getString("app_theme", "System") ?: "System"
    }

    fun setTheme(theme: String) {
        context.getSharedPreferences("orion_browser_settings_engine", Context.MODE_PRIVATE)
            .edit().putString("app_theme", theme).apply()
    }
}

class LanguageManager(private val context: Context) {
    fun getSelectedLanguage(): String {
        return "en"
    }
}
