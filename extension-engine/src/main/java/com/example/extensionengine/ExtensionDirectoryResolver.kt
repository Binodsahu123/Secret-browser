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

    fun generateExtensionId(name: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray())
        val codeAlphabet = "abcdefghijklmnopqrstuvwxyz"
        val builder = java.lang.StringBuilder()
        for (i in 0 until 32) {
            val index = (bytes[i % bytes.size].toInt() and 0xFF) % 26
            builder.append(codeAlphabet[index])
        }
        return builder.toString()
    }

    fun getExtensionDir(context: Context, id: String, name: String? = null): File {
        val rootDir = getExtensionsRootDir(context)
        
        // 1. Direct match check: if a directory named exactly after 'id' exists, use it instantly.
        val dirById = File(rootDir, id)
        if (dirById.exists() && dirById.isDirectory) {
            return dirById
        }
        
        var resolvedName = name
        if (resolvedName != null) {
            idToNameCache[id] = resolvedName
        } else {
            resolvedName = idToNameCache[id]
        }
        
        if (resolvedName == null) {
            // Also try hardcoded mapping for preloaded/known catalog IDs
            resolvedName = when (id) {
                "ext_grok_automation" -> "Grok Automation"
                "ext_dark_reader" -> "Dark Reader"
                "ext_adblock" -> "AdBlock Plus"
                "ext_metamask" -> "MetaMask Wallet"
                "ext_grok_4" -> "Grok 4.0 AI"
                "ext_cookies" -> "I don't care about cookies"
                "ext_auto_translate" -> "Auto-Translate Extension"
                else -> null
            }
        }
        
        if (resolvedName == null) {
            // Dynamic search of directory if still null
            val subdirs = rootDir.listFiles { f -> f.isDirectory }
            if (subdirs != null) {
                for (subdir in subdirs) {
                    val manifestFile = File(subdir, "manifest.json")
                    if (manifestFile.exists()) {
                        try {
                            val content = manifestFile.readText()
                            val json = org.json.JSONObject(content)
                            val nameInManifest = json.optString("name", "")
                            if (nameInManifest.isNotBlank()) {
                                val calculatedId = generateExtensionId(nameInManifest)
                                if (calculatedId.equals(id, ignoreCase = true) || 
                                    id.equals(nameInManifest, ignoreCase = true) ||
                                    subdir.name.contains(id, ignoreCase = true) ||
                                    id.contains(subdir.name, ignoreCase = true)) {
                                    resolvedName = subdir.name
                                    idToNameCache[id] = resolvedName!!
                                    break
                                }
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
        val dirByName = File(rootDir, cleanName)
        
        // If name-based folder already exists, prioritize it.
        if (dirByName.exists()) {
            return dirByName
        }
        
        // Creation path:
        // For pre-packaged or catalog extensions (prefixed with 'ext_'), prefer name-based.
        // For custom uploaded or downoaded web store extensions, prefer ID-based to completely avoid localized / dynamic translation rename issues.
        return if (id.startsWith("ext_")) {
            if (!dirByName.exists()) {
                dirByName.mkdirs()
            }
            dirByName
        } else {
            if (!dirById.exists()) {
                dirById.mkdirs()
            }
            dirById
        }
    }

    var bootstrapProvider: ((String) -> String)? = null

    fun handleExtensionRequest(context: Context, urlStr: String): android.webkit.WebResourceResponse? {
        try {
            val uri = android.net.Uri.parse(urlStr)
            val scheme = uri.scheme
            if (scheme == "chrome-extension" || scheme == "orion-extension") {
                val extensionId = uri.host ?: ""
                val pathStr = uri.path ?: ""
                if (extensionId.isNotBlank() && pathStr.isNotBlank() && !pathStr.contains("..")) {
                    val extensionDir = getExtensionDir(context, extensionId)
                    val targetFile = File(extensionDir, pathStr.removePrefix("/"))
                    if (targetFile.exists() && targetFile.isFile) {
                        val extension = targetFile.extension.lowercase()
                        val mimeType = when (extension) {
                            "html", "htm" -> "text/html"
                            "js" -> "application/javascript"
                            "css" -> "text/css"
                            "json" -> "application/json"
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif" -> "image/gif"
                            "svg" -> "image/svg+xml"
                            "ico" -> "image/x-icon"
                            "woff" -> "font/woff"
                            "woff2" -> "font/woff2"
                            "ttf" -> "font/ttf"
                            "otf" -> "font/otf"
                            "eot" -> "application/vnd.ms-fontobject"
                            else -> "application/octet-stream"
                        }
                        val encoding = if (extension in listOf("html", "js", "css", "json", "xml")) "UTF-8" else null
                        
                        if (extension == "html" || extension == "htm") {
                            try {
                                val htmlContent = targetFile.readText(Charsets.UTF_8)
                                val provider = bootstrapProvider
                                val modifiedHtml = if (provider != null) {
                                    val bootJs = provider(extensionId)
                                    val scriptTag = "<script>\n$bootJs\n</script>"
                                    if (htmlContent.contains("<head>", ignoreCase = true)) {
                                        htmlContent.replace("<head>", "<head>\n$scriptTag", ignoreCase = true)
                                    } else if (htmlContent.contains("<html>", ignoreCase = true)) {
                                        htmlContent.replace("<html>", "<html>\n$scriptTag", ignoreCase = true)
                                    } else {
                                        "$scriptTag\n$htmlContent"
                                    }
                                } else {
                                    htmlContent
                                }
                                val inputStream = java.io.ByteArrayInputStream(modifiedHtml.toByteArray(Charsets.UTF_8))
                                return android.webkit.WebResourceResponse(mimeType, encoding, inputStream)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        val inputStream = java.io.FileInputStream(targetFile)
                        return android.webkit.WebResourceResponse(mimeType, encoding, inputStream)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
