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
        val cr = context.contentResolver
        val inputStream = cr.openInputStream(uri) ?: throw Exception("Could not open URI stream.")
        val bytes = inputStream.readBytes()
        inputStream.close()

        val zipBytes = getZipBytesFromCrx(bytes)
        val zipStream = ZipInputStream(ByteArrayInputStream(zipBytes))
        
        var manifestJsonStr = ""
        val extractedFiles = mutableMapOf<String, ByteArray>()

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

        if (manifestJsonStr.isBlank()) {
            throw Exception("Package is invalid. Missing manifest.json.")
        }

        val parsed = manifestParser.parse(manifestJsonStr)

        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, parsed.id, parsed.name)
        if (extensionDir.exists()) extensionDir.deleteRecursively()
        extensionDir.mkdirs()

        // Strip parent directory wrapper if all files are inside a common root directory
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

        val entity = ExtensionEntity(
            id = parsed.id,
            name = parsed.name,
            version = parsed.version,
            description = parsed.description,
            isEnabled = true,
            manifestJson = manifestJsonStr
        )
        extensionDao.insertExtension(entity)

        return parsed
    }

    suspend fun loadFromDatabase(entity: ExtensionEntity): ParsedExtension {
        return manifestParser.parse(entity.manifestJson)
    }
}
