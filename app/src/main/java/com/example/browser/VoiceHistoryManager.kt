package com.example.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object VoiceHistoryManager {
    private const val PREFS_NAME = "orion_voice_history_prefs"
    private const val KEY_HISTORY = "history_entries"

    fun addEntry(context: Context, text: String, type: String = "command") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = getEntries(context).toMutableList()
        val newEntry = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("text", text)
            put("type", type)
            put("timestamp", System.currentTimeMillis())
        }
        list.add(0, newEntry.toString()) // Newest first
        // Keep last 50
        val limited = list.take(50)
        val array = JSONArray()
        limited.forEach { array.put(JSONObject(it)) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun getEntries(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, "") ?: ""
        if (jsonStr.isEmpty()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i).toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
