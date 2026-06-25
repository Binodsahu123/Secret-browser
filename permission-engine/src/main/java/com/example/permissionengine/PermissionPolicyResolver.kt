package com.example.permissionengine

object PermissionPolicyResolver {
    fun isSecureOrigin(origin: String): Boolean {
        val clean = origin.lowercase().trim()
        return clean.startsWith("https://") ||
                clean.startsWith("file://") ||
                clean.startsWith("localhost") ||
                clean.startsWith("http://localhost") ||
                clean.startsWith("http://127.0.0.1")
    }

    fun isAutoGrantResource(resource: String): Boolean {
        return resource == "android.webkit.resource.PROTECTED_MEDIA_ID_CONTAINER" ||
                resource == "android.webkit.resource.MIDI_SYSEX"
    }

    fun mapResourceToPermissionType(resource: String): String {
        return when (resource) {
            "android.webkit.resource.VIDEO_CAPTURE" -> "CAMERA"
            "android.webkit.resource.AUDIO_CAPTURE" -> "MICROPHONE"
            "android.webkit.resource.MIDI_SYSEX" -> "MIDI"
            "android.webkit.resource.PROTECTED_MEDIA_ID_CONTAINER" -> "PROTECTED_MEDIA"
            else -> "MEDIA"
        }
    }
}
