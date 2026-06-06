package com.example.extensionengine

import java.io.File

class PopupManager {

    /**
     * Resolves the absolute local file:/// URI to launch an extension action popup.
     */
    fun getPopupUrl(context: android.content.Context, extensionId: String, defaultPopupPath: String): String? {
        if (defaultPopupPath.isBlank()) return null
        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
        val popupFile = File(extensionDir, defaultPopupPath)
        if (popupFile.exists()) {
            return "file://${popupFile.absolutePath}"
        }
        // Fallback to checking the filename in root sandbox in case of flat-structured zips
        val filename = defaultPopupPath.substringAfterLast("/")
        val alternateFile = File(extensionDir, filename)
        if (alternateFile.exists()) {
            return "file://${alternateFile.absolutePath}"
        }
        return null
    }
}
