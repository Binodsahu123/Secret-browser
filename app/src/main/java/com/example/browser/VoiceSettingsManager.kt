package com.example.browser

import android.content.Context

class VoiceSettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("orion_voice_asst_v2", Context.MODE_PRIVATE)

    var isVoiceAssistantEnabled: Boolean
        get() = prefs.getBoolean("voice_assistant_enabled", true)
        set(value) = prefs.edit().putBoolean("voice_assistant_enabled", value).apply()

    var activeLanguageCode: String
        get() = prefs.getString("orion_voice_language", "en-US") ?: "en-US"
        set(value) = prefs.edit().putString("orion_voice_language", value).apply()

    var isPassiveWakeWordEnabled: Boolean
        get() = prefs.getBoolean("orion_passive_wakeword", true)
        set(value) = prefs.edit().putBoolean("orion_passive_wakeword", value).apply()
}
