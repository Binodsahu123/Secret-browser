package com.example.browser

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object AudioConversionEngine {
    private const val TAG = "AudioConversionEngine"

    interface ConversionCallback {
        fun onProgress(percentage: Int)
        fun onSuccess(outputFile: File)
        fun onError(error: String)
    }

    suspend fun downloadAndConvertToMp3(
        context: Context,
        audioUrl: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        outputFileName: String,
        callback: ConversionCallback,
        parentJob: Job? = null
    ) = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.raw")
        val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val finalMp3File = File(publicDownloadsDir, outputFileName.replace(".m4a", ".mp3").replace(".webm", ".mp3"))

        try {
            if (parentJob?.isCancelled == true) return@withContext

            Log.d(TAG, "Downloading source audio track...")
            callback.onProgress(10)

            downloadFile(audioUrl, tempFile, { progress ->
                callback.onProgress(10 + (progress * 0.6).toInt()) // 10% to 70%
            }, parentJob)

            if (parentJob?.isCancelled == true) throw Exception("Cancelled")

            callback.onProgress(75)
            Log.d(TAG, "Injecting ID3 Metatag information & Thumbnail coverart...")
            
            convertToMp3WithMetadata(
                tempFile,
                finalMp3File,
                title,
                artist,
                thumbnailUrl
            )

            if (parentJob?.isCancelled == true) throw Exception("Cancelled")

            callback.onProgress(100)
            callback.onSuccess(finalMp3File)

        } catch (e: Exception) {
            Log.e(TAG, "MP3 processing failed: ${e.message}", e)
            callback.onError(e.message ?: "Audio conversion failed")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun downloadFile(
        urlStr: String,
        destination: File,
        progressCallback: (Int) -> Unit,
        parentJob: Job?
    ) {
        val url = URL(urlStr)
        val connection = url.openConnection()
        connection.connect()
        val fileLength = connection.contentLength
        val input = BufferedInputStream(connection.getInputStream())
        val output = FileOutputStream(destination)

        val data = ByteArray(4096)
        var total: Long = 0
        var count: Int
        while (input.read(data).also { count = it } != -1) {
            if (parentJob?.isCancelled == true) {
                output.close()
                input.close()
                throw Exception("Download Cancelled")
            }
            total += count
            if (fileLength > 0) {
                progressCallback((total * 100 / fileLength).toInt())
            }
            output.write(data, 0, count)
        }
        output.flush()
        output.close()
        input.close()
    }

    private fun convertToMp3WithMetadata(
        sourceFile: File,
        destFile: File,
        title: String,
        artist: String,
        thumbnailUrl: String?
    ) {
        val fos = FileOutputStream(destFile)

        // Prepend ID3v2.3 tag
        val id3Bytes = buildId3Tag(title, artist, thumbnailUrl)
        fos.write(id3Bytes)

        // Stream raw audio to destination
        sourceFile.inputStream().use { fis ->
            val buf = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buf).also { bytesRead = it } != -1) {
                fos.write(buf, 0, bytesRead)
            }
        }

        fos.flush()
        fos.close()
    }

    private fun buildId3Tag(title: String, artist: String, thumbnailUrl: String?): ByteArray {
        val tagHeader = "ID3".toByteArray()
        val version = byteArrayOf(0x03, 0x00)
        val flags = byteArrayOf(0x00)

        val albumArtBytes = if (!thumbnailUrl.isNullOrEmpty()) {
            try {
                val url = URL(thumbnailUrl)
                val conn = url.openConnection()
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.getInputStream().use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val frames = mutableListOf<ByteArray>()
        
        frames.add(createTextFrame("TIT2", title))
        frames.add(createTextFrame("TPE1", artist))
        
        if (albumArtBytes != null) {
            frames.add(createPictureFrame(albumArtBytes))
        }

        val framesTotalSize = frames.sumOf { it.size }
        val sizeBytes = encodeSynchsafeInt(framesTotalSize)

        val tagBytes = ByteArray(10 + framesTotalSize)
        System.arraycopy(tagHeader, 0, tagBytes, 0, 3)
        System.arraycopy(version, 0, tagBytes, 3, 2)
        System.arraycopy(flags, 0, tagBytes, 5, 1)
        System.arraycopy(sizeBytes, 0, tagBytes, 6, 4)

        var cursor = 10
        for (f in frames) {
            System.arraycopy(f, 0, tagBytes, cursor, f.size)
            cursor += f.size
        }

        return tagBytes
    }

    private fun createTextFrame(frameId: String, text: String): ByteArray {
        val header = frameId.toByteArray()
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val sizeBytes = encodeSynchsafeInt(textBytes.size + 1)

        val frame = ByteArray(10 + 1 + textBytes.size)
        System.arraycopy(header, 0, frame, 0, 4)
        System.arraycopy(sizeBytes, 0, frame, 4, 4)
        frame[8] = 0x00
        frame[9] = 0x00
        frame[10] = 0x03 // UTF-8 Encoding
        System.arraycopy(textBytes, 0, frame, 11, textBytes.size)

        return frame
    }

    private fun createPictureFrame(artBytes: ByteArray): ByteArray {
        val header = "APIC".toByteArray()
        val mimeBytes = "image/jpeg".toByteArray(Charsets.US_ASCII)
        val descBytes = "Cover".toByteArray(Charsets.UTF_8)
        
        val totalSize = 1 + mimeBytes.size + 1 + 1 + descBytes.size + 1 + artBytes.size
        val sizeBytes = encodeSynchsafeInt(totalSize)

        val frame = ByteArray(10 + totalSize)
        System.arraycopy(header, 0, frame, 0, 4)
        System.arraycopy(sizeBytes, 0, frame, 4, 4)
        frame[8] = 0x00
        frame[9] = 0x00

        var cursor = 10
        frame[cursor++] = 0x03
        System.arraycopy(mimeBytes, 0, frame, cursor, mimeBytes.size)
        cursor += mimeBytes.size
        frame[cursor++] = 0x00
        frame[cursor++] = 0x03 // Coverart Image
        System.arraycopy(descBytes, 0, frame, cursor, descBytes.size)
        cursor += descBytes.size
        frame[cursor++] = 0x00
        System.arraycopy(artBytes, 0, frame, cursor, artBytes.size)

        return frame
    }

    private fun encodeSynchsafeInt(value: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = ((value shr 21) and 0x7F).toByte()
        b[1] = ((value shr 14) and 0x7F).toByte()
        b[2] = ((value shr 7) and 0x7F).toByte()
        b[3] = (value and 0x7F).toByte()
        return b
    }
}
