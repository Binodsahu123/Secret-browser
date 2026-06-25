package com.example.permissionengine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object AndroidRuntimePermissionManager {
    fun hasPermission(context: Context, androidPermission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun mapToAndroidPermissions(resources: Array<String>): List<String> {
        val list = mutableListOf<String>()
        for (res in resources) {
            when (res) {
                "android.webkit.resource.VIDEO_CAPTURE" -> list.add(Manifest.permission.CAMERA)
                "android.webkit.resource.AUDIO_CAPTURE" -> list.add(Manifest.permission.RECORD_AUDIO)
            }
        }
        return list
    }

    fun mapToAndroidPermission(permissionType: String): String? {
        return when (permissionType.uppercase()) {
            "MICROPHONE" -> Manifest.permission.RECORD_AUDIO
            "CAMERA" -> Manifest.permission.CAMERA
            "LOCATION" -> Manifest.permission.ACCESS_FINE_LOCATION
            else -> null
        }
    }
}
