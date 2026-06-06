package com.example.extensionengine

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ExtensionDirectoryResolver {
    private val idToNameCache = ConcurrentHashMap<String, String>()

    fun cacheIdAndName(id: String, name: String) {
        idToNameCache[id] = name
    }

    fun getExtensionsRootDir(context: Context): File {
        val extFilesDir = context.getExternalFilesDir(null)
        val dir = if (extFilesDir != null) {
            // Under Android/data/package_name/extensions/ (next to files/)
            File(extFilesDir.parentFile, "extensions")
        } else {
            File(context.filesDir, "extensions")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getExtensionDir(context: Context, id: String, name: String? = null): File {
        val rootDir = getExtensionsRootDir(context)
        
        var resolvedName = name
        if (resolvedName != null) {
            idToNameCache[id] = resolvedName
        } else {
            resolvedName = idToNameCache[id]
        }
        
        // Dynamic search of directory if still null
        if (resolvedName == null) {
            // First look if there is any sub-folder in getExtensionsRootDir matching
            val subdirs = rootDir.listFiles { f -> f.isDirectory }
            if (subdirs != null) {
                for (subdir in subdirs) {
                    val manifestFile = File(subdir, "manifest.json")
                    if (manifestFile.exists()) {
                        try {
                            val content = manifestFile.readText()
                            if (content.contains("\"name\"") && subdir.name.contains(id, ignoreCase = true)) {
                                resolvedName = subdir.name
                                break
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
        
        if (resolvedName == null) {
            resolvedName = id
        }
        
        val cleanName = resolvedName.replace("[^a-zA-Z0-9 _.-]".toRegex(), "_").trim()
        val dir = File(rootDir, cleanName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
