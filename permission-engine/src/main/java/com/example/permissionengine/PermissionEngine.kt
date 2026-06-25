package com.example.permissionengine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

interface PermissionEngine {
    fun getPermissionState(origin: String, permission: String): String // "Allow", "Block", "Ask"
    fun setPermissionState(origin: String, permission: String, state: String)
    fun clearPermissionState(origin: String, permission: String)
    fun getAllPermissions(): Map<String, Map<String, String>>
}

class PermissionEngineImpl(private val context: Context) : PermissionEngine {
    
    private val memoryStore = OriginPermissionStore()
    private val db = PermissionDatabase.getDatabase(context)
    private val dao = db.permissionDao()
    private val repository = SitePermissionRepository(dao)
    
    val manager = PermissionManager(context, repository, memoryStore)
    val bridge = WebViewPermissionBridge(manager)

    override fun getPermissionState(origin: String, permission: String): String {
        val mappedPerm = mapLegacyPermission(permission)
        val cached = memoryStore.getMemoryState(origin, mappedPerm) ?: "ASK"
        return when (cached) {
            "ALLOW_ALWAYS" -> "Allow"
            "ALLOW_ONCE" -> "Allow"
            "BLOCK" -> "Block"
            else -> "Ask"
        }
    }

    override fun setPermissionState(origin: String, permission: String, state: String) {
        val mappedPerm = mapLegacyPermission(permission)
        val mappedState = when (state.lowercase()) {
            "allow" -> "ALLOW_ALWAYS"
            "block" -> "BLOCK"
            else -> "ASK"
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            if (mappedState == "ASK") {
                repository.deletePermission(origin, mappedPerm)
                memoryStore.clearMemoryState(origin, mappedPerm)
            } else {
                manager.savePermissionState(origin, mappedPerm, mappedState)
            }
        }
    }

    override fun clearPermissionState(origin: String, permission: String) {
        val mappedPerm = mapLegacyPermission(permission)
        CoroutineScope(Dispatchers.IO).launch {
            repository.deletePermission(origin, mappedPerm)
            memoryStore.clearMemoryState(origin, mappedPerm)
        }
    }

    override fun getAllPermissions(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        val cached = memoryStore.getAllCached()
        for ((origin, permMap) in cached) {
            val innerMap = mutableMapOf<String, String>()
            for ((perm, state) in permMap) {
                val legacyState = when (state) {
                    "ALLOW_ALWAYS" -> "Allow"
                    "ALLOW_ONCE" -> "Allow"
                    "BLOCK" -> "Block"
                    else -> "Ask"
                }
                innerMap[perm.lowercase()] = legacyState
            }
            result[origin] = innerMap
        }
        return result
    }

    private fun mapLegacyPermission(perm: String): String {
        return when (perm.lowercase()) {
            "camera" -> "CAMERA"
            "microphone" -> "MICROPHONE"
            "location" -> "LOCATION"
            "storage" -> "STORAGE"
            "notifications" -> "NOTIFICATIONS"
            else -> perm.uppercase()
        }
    }
}
