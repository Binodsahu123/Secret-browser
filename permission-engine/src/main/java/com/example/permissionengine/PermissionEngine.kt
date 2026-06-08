package com.example.permissionengine

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

interface PermissionEngine {
    fun getPermissionState(origin: String, permission: String): String // "Allow", "Block", "Ask"
    fun setPermissionState(origin: String, permission: String, state: String)
    fun clearPermissionState(origin: String, permission: String)
    fun getAllPermissions(): Map<String, Map<String, String>>
}

class PermissionEngineImpl(private val context: Context) : PermissionEngine {
    
    // Thread-safe memory cache of site permission states: Map<Origin, Map<PermissionId, State>>
    private val permissionsCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private val prefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)

    init {
        try {
            val allPrefs = prefs.all
            for ((key, value) in allPrefs) {
                if (key.startsWith("site_perm_exception/")) {
                    // Format: site_perm_exception/{permission}/{origin}
                    val parts = key.removePrefix("site_perm_exception/").split("/", limit = 2)
                    if (parts.size == 2) {
                        val permId = parts[0]
                        val origin = parts[1]
                        val state = value as? String ?: "Ask"
                        permissionsCache.getOrPut(origin) { ConcurrentHashMap() }[permId] = state
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getPermissionState(origin: String, permission: String): String {
        val cleanOrigin = cleanOrigin(origin)
        return permissionsCache[cleanOrigin]?.get(permission) ?: "Ask"
    }

    override fun setPermissionState(origin: String, permission: String, state: String) {
        val cleanOrigin = cleanOrigin(origin)
        permissionsCache.getOrPut(cleanOrigin) { ConcurrentHashMap() }[permission] = state
        prefs.edit().putString("site_perm_exception/$permission/$cleanOrigin", state).apply()
    }

    override fun clearPermissionState(origin: String, permission: String) {
        val cleanOrigin = cleanOrigin(origin)
        permissionsCache[cleanOrigin]?.remove(permission)
        if (permissionsCache[cleanOrigin]?.isEmpty() == true) {
            permissionsCache.remove(cleanOrigin)
        }
        prefs.edit().remove("site_perm_exception/$permission/$cleanOrigin").apply()
    }

    override fun getAllPermissions(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        for ((origin, permMap) in permissionsCache) {
            result[origin] = permMap.toMap()
        }
        return result
    }

    private fun cleanOrigin(origin: String): String {
        var clean = origin.trim().lowercase()
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1)
        }
        return clean
    }
}
