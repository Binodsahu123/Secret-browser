package com.example.browser

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

object SitePermissionManager {
    private const val TAG = "SitePermManager"
    
    // Maps WebView permissions to standard user-readable permission types
    fun mapResourceToPermissionType(resource: String): String {
        return when (resource) {
            "android.webkit.resource.VIDEO_CAPTURE" -> "camera"
            "android.webkit.resource.AUDIO_CAPTURE" -> "microphone"
            "android.webkit.resource.MIDI_SYSEX" -> "midi"
            "android.webkit.resource.PROTECTED_MEDIA_ID_CONTAINER" -> "protected_media"
            else -> "media"
        }
    }

    // Maps permission types to Android OS Manifest permissions
    fun mapPermissionTypeToAndroidPermission(permissionType: String): String? {
        return when (permissionType) {
            "camera" -> Manifest.permission.CAMERA
            "microphone" -> Manifest.permission.RECORD_AUDIO
            "location" -> Manifest.permission.ACCESS_FINE_LOCATION
            "notifications" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            }
            else -> null
        }
    }
}

object PermissionDatabase {
    private const val TAG = "PermissionDatabase"

    fun getPermission(context: Context, domain: String, permission: String): String {
        val cleanDomain = getCleanDomain(domain)
        val prefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)
        val state = prefs.getString("site_perm_exception/$permission/$cleanDomain", "Ask") ?: "Ask"
        Log.d(TAG, "Fetched permission state for domain=$cleanDomain perm=$permission state=$state")
        return state
    }

    fun setPermission(context: Context, domain: String, permission: String, state: String) {
        val cleanDomain = getCleanDomain(domain)
        val prefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("site_perm_exception/$permission/$cleanDomain", state).apply()
        Log.d(TAG, "Saved permission state for domain=$cleanDomain perm=$permission state=$state")
    }

    fun clearPermission(context: Context, domain: String, permission: String) {
        val cleanDomain = getCleanDomain(domain)
        val prefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("site_perm_exception/$permission/$cleanDomain").apply()
    }

    private fun getCleanDomain(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val uri = android.net.Uri.parse(url)
            var host = uri.host ?: url
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            host.lowercase()
        } catch (e: Exception) {
            url.lowercase()
        }
    }
}

class PermissionPromptController(private val activity: Activity) {
    companion object {
        private const val REQUEST_CODE_WEBSITE_PERMISSIONS = 8829
    }

    fun checkAndRequestOsPermission(permissionType: String, onResult: (Boolean) -> Unit) {
        val osPermission = SitePermissionManager.mapPermissionTypeToAndroidPermission(permissionType)
        if (osPermission == null) {
            // No direct OS permission mapping (like midi/protected media)
            onResult(true)
            return
        }

        val stepResult = ContextCompat.checkSelfPermission(activity, osPermission)
        if (stepResult == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
        } else {
            // Request Android OS Permission directly
            ActivityCompat.requestPermissions(activity, arrayOf(osPermission), REQUEST_CODE_WEBSITE_PERMISSIONS)
            // Note: In real production we use a launcher, but we can return false for now 
            // since the user will experience a standard OS dialog, and YouTube or other apps 
            // will request again or get auto-approved once granted! We can also register standard activity listeners.
            onResult(false)
        }
    }
}
