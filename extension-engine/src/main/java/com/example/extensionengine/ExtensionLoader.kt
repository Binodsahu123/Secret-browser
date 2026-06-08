package com.example.extensionengine

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ExtensionLoader(
    private val context: Context,
    private val manifestParser: ManifestParser,
    private val database: ExtensionDatabase
) {

    private val extensionDao = database.extensionDao()

    /**
     * Unpacks, extracts, parses and indexes a ZIP or CRX extension archive.
     */
    private fun getZipBytesFromCrx(crxBytes: ByteArray): ByteArray {
        if (crxBytes.size < 12) {
            return crxBytes
        }
        // Check magic "Cr24" (0x43 0x72 0x32 0x34)
        if (crxBytes[0] == 0x43.toByte() && crxBytes[1] == 0x72.toByte() && 
            crxBytes[2] == 0x32.toByte() && crxBytes[3] == 0x34.toByte()) {
            
            val version = (crxBytes[4].toInt() and 0xFF) or
                          ((crxBytes[5].toInt() and 0xFF) shl 8) or
                          ((crxBytes[6].toInt() and 0xFF) shl 16) or
                          ((crxBytes[7].toInt() and 0xFF) shl 24)
                          
            if (version == 2) {
                val publicKeyLen = (crxBytes[8].toInt() and 0xFF) or
                                   ((crxBytes[9].toInt() and 0xFF) shl 8) or
                                   ((crxBytes[10].toInt() and 0xFF) shl 16) or
                                   ((crxBytes[11].toInt() and 0xFF) shl 24)
                                   
                val signatureLen = (crxBytes[12].toInt() and 0xFF) or
                                   ((crxBytes[13].toInt() and 0xFF) shl 8) or
                                   ((crxBytes[14].toInt() and 0xFF) shl 16) or
                                   ((crxBytes[15].toInt() and 0xFF) shl 24)
                                   
                val zipStart = 16 + publicKeyLen + signatureLen
                if (zipStart <= crxBytes.size) {
                    return crxBytes.copyOfRange(zipStart, crxBytes.size)
                }
            } else if (version == 3) {
                val headerLen = (crxBytes[8].toInt() and 0xFF) or
                                ((crxBytes[9].toInt() and 0xFF) shl 8) or
                                ((crxBytes[10].toInt() and 0xFF) shl 16) or
                                ((crxBytes[11].toInt() and 0xFF) shl 24)
                                
                val zipStart = 12 + headerLen
                if (zipStart <= crxBytes.size) {
                    return crxBytes.copyOfRange(zipStart, crxBytes.size)
                }
            }
        }
        
        // Check if already raw ZIP "PK\u0003\u0004"
        if (crxBytes[0] == 0x50.toByte() && crxBytes[1] == 0x4B.toByte() &&
            crxBytes[2] == 0x03.toByte() && crxBytes[3] == 0x04.toByte()) {
            return crxBytes
        }
        
        // Fallback: Signature searching inside file
        var zipOffset = -1
        for (i in 0 until crxBytes.size - 4) {
            if (crxBytes[i] == 0x50.toByte() &&
                crxBytes[i+1] == 0x4B.toByte() &&
                crxBytes[i+2] == 0x03.toByte() &&
                crxBytes[i+3] == 0x04.toByte()
            ) {
                zipOffset = i
                break
            }
        }
        if (zipOffset != -1) {
            return crxBytes.copyOfRange(zipOffset, crxBytes.size)
        }
        
        return crxBytes
    }

    suspend fun loadAndInstallFromZip(uri: Uri): ParsedExtension {
        val stepDownload = org.json.JSONObject().put("status", "SUCCESS")
        val stepSave = org.json.JSONObject().put("status", "PENDING")
        val stepVerifySize = org.json.JSONObject().put("status", "PENDING")
        val stepParseHeader = org.json.JSONObject().put("status", "PENDING")
        val stepExtract = org.json.JSONObject().put("status", "PENDING")
        val stepCreateDir = org.json.JSONObject().put("status", "PENDING")
        val stepVerifyFiles = org.json.JSONObject().put("status", "PENDING")
        val stepManifestExists = org.json.JSONObject().put("status", "PENDING")
        val stepPopupExists = org.json.JSONObject().put("status", "PENDING")
        
        var parsedExtensionId = "failed_temp_install"
        var resolvedInstallPath = ""
        var resolvedManifestPath = ""
        var resolvedPopupPath = ""
        var fileCountOnDisk = 0
        val filesScanList = mutableListOf<String>()

        val writeAuditJson = {
            try {
                val auditObj = org.json.JSONObject()
                auditObj.put("extensionId", parsedExtensionId)
                auditObj.put("installPath", resolvedInstallPath)
                auditObj.put("manifestPath", resolvedManifestPath)
                auditObj.put("popupPath", resolvedPopupPath)
                auditObj.put("fileCount", fileCountOnDisk)
                
                val stepsObj = org.json.JSONObject()
                stepsObj.put("download_crx", stepDownload)
                stepsObj.put("save_crx", stepSave)
                stepsObj.put("verify_crx_size", stepVerifySize)
                stepsObj.put("parse_crx_header", stepParseHeader)
                stepsObj.put("extract_payload", stepExtract)
                stepsObj.put("create_ext_dir", stepCreateDir)
                stepsObj.put("verify_files_exist", stepVerifyFiles)
                stepsObj.put("verify_manifest_exists", stepManifestExists)
                stepsObj.put("verify_popup_exists", stepPopupExists)
                auditObj.put("steps", stepsObj)
                
                val scanArr = org.json.JSONArray()
                filesScanList.forEach { scanArr.put(it) }
                auditObj.put("directoryScan", scanArr)
                
                val auditFile = File(context.cacheDir, "install_pipeline_audit_${parsedExtensionId}.json")
                auditFile.writeText(auditObj.toString(4))
                
                // Also write a global last_install_audit.json for convenient general capture
                val globalAuditFile = File(context.cacheDir, "last_install_audit.json")
                globalAuditFile.writeText(auditObj.toString(4))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Step 1: Download CRX
        val isWebStore = uri.toString().contains("temp_webstore_") || uri.path?.contains("temp_webstore_") == true
        stepDownload.put("status", "SUCCESS")
        stepDownload.put("detail", if (isWebStore) "Downloaded successfully from Chrome Web Store" else "Loaded via Local Archive Upload")
        stepDownload.put("path", uri.path ?: uri.toString())

        // Step 2: Save CRX
        val cr = context.contentResolver
        val inputStream = try {
            cr.openInputStream(uri)
        } catch (e: Exception) {
            stepSave.put("status", "FAILURE")
            stepSave.put("detail", "Failed to open input stream: ${e.localizedMessage}")
            stepSave.put("path", uri.toString())
            writeAuditJson()
            throw e
        }
        
        if (inputStream == null) {
            stepSave.put("status", "FAILURE")
            stepSave.put("detail", "Input stream is null")
            stepSave.put("path", uri.toString())
            writeAuditJson()
            throw Exception("Could not open URI stream.")
        }
        
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            stepSave.put("status", "FAILURE")
            stepSave.put("detail", "Failed to read stream bytes: ${e.localizedMessage}")
            stepSave.put("path", uri.toString())
            writeAuditJson()
            throw e
        } finally {
            try { inputStream.close() } catch(e: Exception) {}
        }
        
        stepSave.put("status", "SUCCESS")
        stepSave.put("detail", "Successfully decoded and cached archive stream in buffer")
        stepSave.put("path", uri.path ?: "InMemory")

        // Step 3: Verify CRX size
        val size = bytes.size
        if (size > 0) {
            stepVerifySize.put("status", "SUCCESS")
            stepVerifySize.put("detail", "Verified archive file size: $size bytes")
            stepVerifySize.put("path", uri.path ?: "InMemory")
        } else {
            stepVerifySize.put("status", "FAILURE")
            stepVerifySize.put("detail", "Zero-length empty archive file detected")
            stepVerifySize.put("path", uri.path ?: "InMemory")
            writeAuditJson()
            throw Exception("Downloaded file is empty (CRX size verification failed).")
        }

        // Step 4: Parse CRX header
        var isCrx = false
        var crxVersion = 0
        var isRawZip = false
        if (bytes.size >= 12 && bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte() &&
            bytes[2] == 0x32.toByte() && bytes[3] == 0x34.toByte()) {
            isCrx = true
            crxVersion = (bytes[4].toInt() and 0xFF) or
                          ((bytes[5].toInt() and 0xFF) shl 8) or
                          ((bytes[6].toInt() and 0xFF) shl 16) or
                          ((bytes[7].toInt() and 0xFF) shl 24)
            stepParseHeader.put("status", "SUCCESS")
            stepParseHeader.put("detail", "Validated Chrome Extension (CRX) container format. Version: $crxVersion")
            stepParseHeader.put("path", uri.path ?: "InMemory")
        } else if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                   bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            isRawZip = true
            stepParseHeader.put("status", "SUCCESS")
            stepParseHeader.put("detail", "Validated direct standard PKZIP archive container.")
            stepParseHeader.put("path", uri.path ?: "InMemory")
        } else {
            // Check fallback zip index before failing to parse
            var zipOffset = -1
            for (i in 0 until bytes.size - 4) {
                if (bytes[i] == 0x50.toByte() && bytes[i+1] == 0x4B.toByte() &&
                    bytes[i+2] == 0x03.toByte() && bytes[i+3] == 0x04.toByte()
                ) {
                    zipOffset = i
                    break
                }
            }
            if (zipOffset != -1) {
                stepParseHeader.put("status", "SUCCESS")
                stepParseHeader.put("detail", "Found PKZIP payload starting at offset $zipOffset")
                stepParseHeader.put("path", uri.path ?: "InMemory")
            } else {
                stepParseHeader.put("status", "FAILURE")
                stepParseHeader.put("detail", "CRX header parser failed. File is neither a standard CRX nor a valid ZIP archive.")
                stepParseHeader.put("path", uri.path ?: "InMemory")
                writeAuditJson()
                throw Exception("Stream is not a valid ZIP or CRX (CRX header parse failed).")
            }
        }

        // Step 5: Extract payload
        val zipBytes = try {
            getZipBytesFromCrx(bytes)
        } catch (e: Exception) {
            stepExtract.put("status", "FAILURE")
            stepExtract.put("detail", "CRX extraction failed payload byte decoding: ${e.localizedMessage}")
            stepExtract.put("path", "")
            writeAuditJson()
            throw e
        }
        
        val extractedFiles = mutableMapOf<String, ByteArray>()
        var manifestJsonStr = ""
        
        try {
            val zipStream = ZipInputStream(ByteArrayInputStream(zipBytes))
            var entry = zipStream.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    val bos = ByteArrayOutputStream()
                    val buffer = ByteArray(2048)
                    var count = zipStream.read(buffer)
                    while (count != -1) {
                        bos.write(buffer, 0, count)
                        count = zipStream.read(buffer)
                    }
                    val fileData = bos.toByteArray()
                    extractedFiles[entryName] = fileData

                    val filenameOnly = entryName.substringAfterLast("/")
                    if (filenameOnly.equals("manifest.json", ignoreCase = true)) {
                        manifestJsonStr = String(fileData, Charsets.UTF_8)
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
        } catch (e: Exception) {
            stepExtract.put("status", "FAILURE")
            stepExtract.put("detail", "Extractor loop crashed parsing ZIP sections: ${e.localizedMessage}")
            stepExtract.put("path", "")
            writeAuditJson()
            throw e
        }
        
        if (extractedFiles.isEmpty()) {
            stepExtract.put("status", "FAILURE")
            stepExtract.put("detail", "CRX extraction failed: Payload contains zero files and directories")
            stepExtract.put("path", "")
            writeAuditJson()
            throw Exception("CRX extraction failed. Payload has 0 files.")
        } else {
            stepExtract.put("status", "SUCCESS")
            stepExtract.put("detail", "Successfully read and extracted ${extractedFiles.size} keys from zip structure")
            stepExtract.put("path", "")
        }

        // Step 6: Create extension directory
        if (manifestJsonStr.isBlank()) {
            stepManifestExists.put("status", "FAILURE")
            stepManifestExists.put("detail", "Manifest parser failed. Missing manifest.json in package.")
            stepManifestExists.put("path", "")
            writeAuditJson()
            throw Exception("Package is invalid. Missing manifest.json.")
        }

        val parsed = try {
            manifestParser.parse(manifestJsonStr)
        } catch (e: Exception) {
            stepCreateDir.put("status", "FAILURE")
            stepCreateDir.put("detail", "Manifest parser failed: ${e.localizedMessage}")
            stepCreateDir.put("path", "")
            writeAuditJson()
            throw e
        }

        parsedExtensionId = parsed.id

        val extensionDir = try {
            ExtensionDirectoryResolver.getExtensionDir(context, parsed.id, parsed.name)
        } catch (e: Exception) {
            stepCreateDir.put("status", "FAILURE")
            stepCreateDir.put("detail", "Failed resolving file path context: ${e.localizedMessage}")
            stepCreateDir.put("path", "")
            writeAuditJson()
            throw e
        }

        resolvedInstallPath = extensionDir.absolutePath

        try {
            if (extensionDir.exists()) extensionDir.deleteRecursively()
            extensionDir.mkdirs()
        } catch (e: Exception) {
            stepCreateDir.put("status", "FAILURE")
            stepCreateDir.put("detail", "Failed creating folders on disk filesystem: ${e.localizedMessage}")
            stepCreateDir.put("path", resolvedInstallPath)
            writeAuditJson()
            throw e
        }

        if (extensionDir.exists() && extensionDir.isDirectory) {
            stepCreateDir.put("status", "SUCCESS")
            stepCreateDir.put("detail", "Successfully provisioned sandbox directory wrapper")
            stepCreateDir.put("path", resolvedInstallPath)
        } else {
            stepCreateDir.put("status", "FAILURE")
            stepCreateDir.put("detail", "Folder creation request returned success but directory is missing on filesystem")
            stepCreateDir.put("path", resolvedInstallPath)
            writeAuditJson()
            throw Exception("Directory mapping failed. Directory was not created.")
        }

        // Step 7: Verify files exist (Write extracted files to filesystem & scan)
        val keys = extractedFiles.keys.toList()
        var commonPrefix = ""
        if (keys.isNotEmpty()) {
            val firstPath = keys.first()
            val firstSlash = firstPath.indexOf('/')
            if (firstSlash != -1) {
                val candidatePrefix = firstPath.substring(0, firstSlash + 1)
                val allShare = keys.all { it.startsWith(candidatePrefix) }
                if (allShare) {
                    commonPrefix = candidatePrefix
                }
            }
        }

        try {
            extractedFiles.forEach { (relativePath, data) ->
                val cleanPath = if (commonPrefix.isNotEmpty() && relativePath.startsWith(commonPrefix)) {
                    relativePath.substring(commonPrefix.length)
                } else {
                    relativePath
                }
                if (cleanPath.isNotEmpty()) {
                    val destFile = File(extensionDir, cleanPath)
                    destFile.parentFile?.mkdirs()
                    destFile.writeBytes(data)
                }
            }
        } catch (e: Exception) {
            stepVerifyFiles.put("status", "FAILURE")
            stepVerifyFiles.put("detail", "Failed writing extracted payload bytes to file path: ${e.localizedMessage}")
            stepVerifyFiles.put("path", resolvedInstallPath)
            writeAuditJson()
            throw e
        }

        // Perform real scan on disk
        val diskFiles = mutableListOf<File>()
        fun scan(dir: File) {
            val children = dir.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) {
                        scan(child)
                    } else {
                        diskFiles.add(child)
                    }
                }
            }
        }
        scan(extensionDir)

        fileCountOnDisk = diskFiles.size
        diskFiles.forEach { f ->
            filesScanList.add(f.relativeTo(extensionDir).path)
        }

        if (fileCountOnDisk > 0) {
            stepVerifyFiles.put("status", "SUCCESS")
            stepVerifyFiles.put("detail", "Verified $fileCountOnDisk files correctly exist on filesystem store partition")
            stepVerifyFiles.put("path", resolvedInstallPath)
        } else {
            stepVerifyFiles.put("status", "FAILURE")
            stepVerifyFiles.put("detail", "Directory mapping failed. Extracted payload files has count 0 on storage partition.")
            stepVerifyFiles.put("path", resolvedInstallPath)
            writeAuditJson()
            throw Exception("Directory mapping failed. No files on disk.")
        }

        // Step 8: Verify manifest.json exists
        val manifestFile = File(extensionDir, "manifest.json")
        resolvedManifestPath = manifestFile.absolutePath
        if (manifestFile.exists() && manifestFile.isFile) {
            stepManifestExists.put("status", "SUCCESS")
            stepManifestExists.put("detail", "Validated manifest.json is active and readable")
            stepManifestExists.put("path", resolvedManifestPath)
        } else {
            stepManifestExists.put("status", "FAILURE")
            stepManifestExists.put("detail", "manifest.json not found in targeted extension directory mapping.")
            stepManifestExists.put("path", resolvedManifestPath)
            writeAuditJson()
            throw Exception("Directory mapping failed. manifest.json missing on disk.")
        }

        // Step 9: Verify popup files exist
        val resolvedPopupPathValue = resolvePopupPath(extensionDir, manifestJsonStr)
        resolvedPopupPath = if (resolvedPopupPathValue.isNotBlank()) {
            File(extensionDir, resolvedPopupPathValue).absolutePath
        } else {
            ""
        }

        if (resolvedPopupPathValue.isNotBlank()) {
            val popupFile = File(extensionDir, resolvedPopupPathValue)
            if (popupFile.exists() && popupFile.isFile) {
                stepPopupExists.put("status", "SUCCESS")
                stepPopupExists.put("detail", "Validated popup resource HTML element exists at: $resolvedPopupPathValue")
                stepPopupExists.put("path", resolvedPopupPath)
            } else {
                stepPopupExists.put("status", "FAILURE")
                stepPopupExists.put("detail", "Popup file defined in manifest ($resolvedPopupPathValue) was NOT found in extraction directory!")
                stepPopupExists.put("path", resolvedPopupPath)
            }
        } else {
            stepPopupExists.put("status", "SUCCESS")
            stepPopupExists.put("detail", "No browser popup interface is requested. (Running inside content or background loop).")
            stepPopupExists.put("path", "")
        }

        // Now that files are unzipped on disk, we resolve per-extension localized metadata
        val resolvedName = resolveLocaleString(extensionDir, parsed.name)
        
        var rawShortName = ""
        try {
            val root = org.json.JSONObject(manifestJsonStr)
            rawShortName = root.optString("short_name", "")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val resolvedShortName = if (rawShortName.isNotBlank()) {
            resolveLocaleString(extensionDir, rawShortName)
        } else {
            resolvedName
        }

        val resolvedDescription = resolveLocaleString(extensionDir, parsed.description)
        val resolvedIconPath = resolveIconPath(extensionDir, manifestJsonStr)
        val resolvedPopupPathFinal = resolvePopupPath(extensionDir, manifestJsonStr)
        val resolvedBackgroundPath = resolveBackgroundPath(manifestJsonStr)
        val manifestPath = File(extensionDir, "manifest.json").absolutePath
        val installPath = extensionDir.absolutePath

        val entity = ExtensionEntity(
            extensionId = parsed.id,
            name = resolvedName,
            shortName = resolvedShortName,
            version = parsed.version,
            description = resolvedDescription,
            iconPath = resolvedIconPath,
            installPath = installPath,
            popupPath = resolvedPopupPathFinal,
            manifestPath = manifestPath,
            backgroundPath = resolvedBackgroundPath,
            enabledState = true,
            manifestJson = manifestJsonStr
        )
        extensionDao.insertExtension(entity)

        // Write complete success report
        writeAuditJson()

        return parsed.copy(
            name = resolvedName,
            shortName = resolvedShortName,
            description = resolvedDescription,
            iconPath = resolvedIconPath,
            installPath = installPath,
            popupPath = resolvedPopupPathFinal,
            manifestPath = manifestPath,
            backgroundPath = resolvedBackgroundPath,
            isEnabled = true
        )
    }

    suspend fun loadFromDatabase(entity: ExtensionEntity): ParsedExtension {
        val parsed = manifestParser.parse(entity.manifestJson)
        return parsed.copy(
            id = entity.extensionId,
            name = entity.name,
            shortName = entity.shortName,
            version = entity.version,
            description = entity.description,
            iconPath = entity.iconPath,
            installPath = entity.installPath,
            popupPath = entity.popupPath,
            manifestPath = entity.manifestPath,
            backgroundPath = entity.backgroundPath,
            isEnabled = entity.enabledState
        )
    }

    private fun resolveLocaleString(extensionDir: File, manifestValue: String): String {
        if (!manifestValue.startsWith("__MSG_") || !manifestValue.endsWith("__")) {
            return manifestValue
        }
        val key = manifestValue.removePrefix("__MSG_").removeSuffix("__")
        val localesDir = File(extensionDir, "_locales")
        if (!localesDir.exists() || !localesDir.isDirectory) {
            return manifestValue
        }

        val currentLocale = java.util.Locale.getDefault()
        val langCode = currentLocale.language
        val country = currentLocale.country
        val fullCode = if (country.isNotBlank()) "${langCode}_$country" else langCode

        val candidateDirs = listOf(
            fullCode,
            fullCode.replace("_", "-"),
            langCode,
            "en",
            "en-US",
            "en_US"
        )

        var messagesFile: File? = null
        for (cand in candidateDirs) {
            val f = File(localesDir, cand + "/messages.json")
            if (f.exists() && f.isFile) {
                messagesFile = f
                break
            }
        }

        if (messagesFile == null) {
            val subfolders = localesDir.listFiles { f -> f.isDirectory }
            if (subfolders != null) {
                for (sub in subfolders) {
                    val f = File(sub, "messages.json")
                    if (f.exists() && f.isFile) {
                        messagesFile = f
                        break
                    }
                }
            }
        }

        if (messagesFile != null) {
            try {
                val json = org.json.JSONObject(messagesFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(key, ignoreCase = true)) {
                        val obj = json.optJSONObject(k)
                        val message = obj?.optString("message", "") ?: ""
                        if (message.isNotBlank()) {
                            return message
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return manifestValue
    }

    private fun resolveIconPath(extensionDir: File, manifestJsonStr: String): String {
        try {
            val json = org.json.JSONObject(manifestJsonStr)
            val iconsObj = json.optJSONObject("icons")
            if (iconsObj != null) {
                val sizes = listOf("128", "96", "64", "48", "32", "16", "256", "512")
                for (size in sizes) {
                    val path = iconsObj.optString(size, "")
                    if (path.isNotBlank()) {
                        val clean = path.removePrefix("./").removePrefix("/")
                        if (File(extensionDir, clean).exists()) {
                            return clean
                        }
                    }
                }
                val keys = iconsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val path = iconsObj.optString(key, "")
                    if (path.isNotBlank()) {
                        val clean = path.removePrefix("./").removePrefix("/")
                        if (File(extensionDir, clean).exists()) {
                            return clean
                        }
                    }
                }
            }

            val actions = listOf("action", "browser_action", "page_action")
            for (actionKey in actions) {
                val actionObj = json.optJSONObject(actionKey) ?: continue
                val defaultIconValue = actionObj.opt("default_icon") ?: continue
                if (defaultIconValue is org.json.JSONObject) {
                    val sizes = listOf("128", "96", "48", "32", "16")
                    for (size in sizes) {
                        val path = defaultIconValue.optString(size, "")
                        if (path.isNotBlank()) {
                            val clean = path.removePrefix("./").removePrefix("/")
                            if (File(extensionDir, clean).exists()) {
                                return clean
                            }
                        }
                    }
                    val keys = defaultIconValue.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val path = defaultIconValue.optString(key, "")
                        if (path.isNotBlank()) {
                            val clean = path.removePrefix("./").removePrefix("/")
                            if (File(extensionDir, clean).exists()) {
                                return clean
                            }
                        }
                    }
                } else if (defaultIconValue is String) {
                    if (defaultIconValue.isNotBlank()) {
                        val clean = defaultIconValue.removePrefix("./").removePrefix("/")
                        if (File(extensionDir, clean).exists()) {
                            return clean
                        }
                    }
                }
            }

            val commons = listOf(
                "icon128.png", "icon48.png", "icon16.png", "icon.png",
                "logo.png", "icon.svg", "images/icon-48.png", "images/icon-128.png",
                "icons/icon128.png", "icons/icon48.png", "icons/icon.png"
            )
            for (com in commons) {
                if (File(extensionDir, com).exists()) {
                    return com
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun resolvePopupPath(extensionDir: File, manifestJsonStr: String): String {
        try {
            val json = org.json.JSONObject(manifestJsonStr)
            val actions = listOf("action", "browser_action", "page_action")
            for (actionKey in actions) {
                val actionObj = json.optJSONObject(actionKey) ?: continue
                val defaultPopup = actionObj.optString("default_popup", "")
                if (defaultPopup.isNotBlank()) {
                    val clean = defaultPopup.removePrefix("./").removePrefix("/")
                    if (File(extensionDir, clean).exists()) {
                        return clean
                    }
                }
            }
            val fallbacks = listOf("popup.html", "index.html", "options.html")
            for (f in fallbacks) {
                if (File(extensionDir, f).exists()) {
                    return f
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun resolveBackgroundPath(manifestJsonStr: String): String {
        try {
            val root = org.json.JSONObject(manifestJsonStr)
            val backgroundObj = root.optJSONObject("background")
            if (backgroundObj != null) {
                val scriptsArray = backgroundObj.optJSONArray("scripts")
                if (scriptsArray != null && scriptsArray.length() > 0) {
                    return scriptsArray.getString(0)
                }
                val serviceWorker = backgroundObj.optString("service_worker", "")
                if (serviceWorker.isNotBlank()) {
                    return serviceWorker
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
