package com.example.browser

import android.content.Context

class AssistantSettings(context: Context) {
    private val prefs = context.getSharedPreferences("orion_assistant_prefs", Context.MODE_PRIVATE)

    var isAssistantEnabled: Boolean
        get() = prefs.getBoolean("assistant_enabled", true)
        set(value) = prefs.edit().putBoolean("assistant_enabled", value).apply()

    var activeLanguageCode: String
        get() = prefs.getString("active_language_code", "en-US") ?: "en-US"
        set(value) = prefs.edit().putString("active_language_code", value).apply()

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean("wakeword_enabled", true)
        set(value) = prefs.edit().putBoolean("wakeword_enabled", value).apply()
}
