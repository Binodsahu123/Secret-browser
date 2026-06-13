package com.example.browser

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.media.AudioManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.example.downloadengine.DownloadDatabase
import com.example.downloadengine.DownloadItem
import com.example.downloadengine.DownloadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.media.MediaMuxer
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

class YTProAndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val downloadEngine: com.example.downloadengine.DownloadEngine
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeStreams = java.util.concurrent.ConcurrentHashMap<String, FileOutputStream>()
    private val activePorts = java.util.concurrent.ConcurrentHashMap<String, WebMessagePort>()

    @JavascriptInterface
    fun getAllCookies(url: String): String {
        return CookieManager.getInstance().getCookie(url) ?: ""
    }

    @JavascriptInterface
    fun showToast(msg: String) {
        handler.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun hasStoragePermission(): Boolean {
        return true
    }

    @JavascriptInterface
    fun isWebViewSupported(): Boolean {
        return true
    }

    @JavascriptInterface
    fun getInfo(): String {
        return "3.98"
    }

    @JavascriptInterface
    fun getVolume(): Float {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        return if (max > 0) current / max else 0f
    }

    @JavascriptInterface
    fun setVolume(vol: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (vol * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    @JavascriptInterface
    fun getBrightness(): Float {
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
            current / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    @JavascriptInterface
    fun setBrightness(brt: Float) {
        handler.post {
            val activity = context as? Activity
            if (activity != null) {
                val layoutParams = activity.window.attributes
                layoutParams.screenBrightness = brt.coerceIn(0f, 1f)
                activity.window.attributes = layoutParams
            }
        }
    }

    @JavascriptInterface
    fun oplink(url: String) {
        handler.post {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open external: $url", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun downvid(title: String, url: String, mimeType: String) {
        scope.launch {
            try {
                downloadEngine.startDownload(url, title, mimeType)
                handler.post {
                    Toast.makeText(context, "Added file to download queue: $title", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun setBgPlay(enabled: Boolean) {
        Log.d("YTProBridge", "setBgPlay enabled: $enabled")
    }

    @JavascriptInterface
    fun pipvid(orientation: String) {
        handler.post {
            (context as? Activity)?.let { activity ->
                YouTubePipController.enterPip(activity)
            }
        }
    }

    @JavascriptInterface
    fun fullScreen(enabled: Boolean) {
        handler.post {
            val activity = context as? Activity
            if (activity != null) {
                if (enabled) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    @JavascriptInterface
    fun requestBinaryPort(fileName: String) {
        handler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val channels = webView.createWebMessageChannel()
                    val javaPort = channels[0]
                    val jsPort = channels[1]

                    activePorts[fileName]?.close()
                    activePorts[fileName] = javaPort

                    val folder = DownloadScheduler.getDownloadDirectory(context, "Videos")
                    val file = File(folder, fileName)
                    if (file.exists()) {
                        file.delete()
                    }
                    val fos = FileOutputStream(file)
                    activeStreams[fileName] = fos

                    val downloadId = System.currentTimeMillis() + (fileName.hashCode().toLong() and 0x00FFFFFFL)
                    val db = DownloadDatabase.getDatabase(context)
                    val category = if (fileName.contains("_audio")) "Audio" else "Videos"
                    val mime = if (fileName.endsWith(".mp4")) "video/mp4" else if (fileName.endsWith(".webm")) "video/webm" else "audio/mpeg"

                    val initialItem = DownloadItem(
                        id = downloadId,
                        title = fileName,
                        url = "https://www.youtube.com/watch",
                        mimeType = mime,
                        status = "RUNNING",
                        progress = 0,
                        downloadedSize = 0L,
                        totalSize = 0L,
                        speed = "Downloading...",
                        category = category,
                        filePath = file.absolutePath
                    )
                    scope.launch {
                        db.downloadDao().insertDownload(initialItem)
                    }

                    var lastUpdateBytes = 0L
                    var lastUpdateTime = 0L

                    javaPort.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                        override fun onMessage(port: WebMessagePort?, message: WebMessage?) {
                            val data = message?.data
                            if (data == "END") {
                                Log.d("YTProBridge", "Received end of stream for $fileName")
                                cleanupStream(fileName, file, downloadId)
                                javaPort.close()
                                activePorts.remove(fileName)
                            } else if (data != null) {
                                try {
                                    val bytes = Base64.decode(data, Base64.DEFAULT)
                                    fos.write(bytes)

                                    val currentLength = file.length()
                                    val now = System.currentTimeMillis()
                                    if (currentLength - lastUpdateBytes > 1024 * 512 || now - lastUpdateTime > 1000) {
                                        lastUpdateBytes = currentLength
                                        lastUpdateTime = now
                                        scope.launch {
                                            val updated = initialItem.copy(
                                                status = "RUNNING",
                                                downloadedSize = currentLength,
                                                speed = "Downloading..."
                                            )
                                            db.downloadDao().insertDownload(updated)
                                        }
                                    }
                                } catch (e: Exception) {
                                    try {
                                        fos.write(data.toByteArray())
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                    }
                                }
                            }
                        }
                    })

                    webView.postWebMessage(
                        WebMessage("PORT_FOR:$fileName", arrayOf(jsPort)),
                        Uri.parse("https://m.youtube.com")
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun cleanupStream(fileName: String, file: File, downloadId: Long) {
        scope.launch {
            try {
                val fos = activeStreams[fileName]
                if (fos != null) {
                    fos.flush()
                    try {
                        fos.getFD().sync()
                    } catch (e: Exception) {}
                    fos.close()
                    activeStreams.remove(fileName)
                }

                val db = DownloadDatabase.getDatabase(context)
                val item = DownloadItem(
                    id = downloadId,
                    title = fileName,
                    url = "https://www.youtube.com/watch",
                    mimeType = if (fileName.endsWith(".mp4")) "video/mp4" else if (fileName.endsWith(".webm")) "video/webm" else "audio/mpeg",
                    status = "COMPLETED",
                    progress = 100,
                    downloadedSize = file.length(),
                    totalSize = file.length(),
                    speed = "Completed",
                    category = if (fileName.contains("_audio")) "Audio" else "Videos",
                    filePath = file.absolutePath
                )
                db.downloadDao().insertDownload(item)
                Log.d("YTProBridge", "Piped file stream completed and recorded: ${file.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun muxVideoAudio(videoFileName: String, audioFileName: String, outFileName: String) {
        scope.launch {
            try {
                val folder = DownloadScheduler.getDownloadDirectory(context, "Videos")
                val videoFile = File(folder, videoFileName)
                val audioFile = File(folder, audioFileName)
                val outFile = File(folder, outFileName)

                if (videoFile.exists() && audioFile.exists()) {
                    performMuxing(videoFile, audioFile, outFile)
                    Log.d("YTProBridge", "Successfully muxed $videoFileName and $audioFileName into $outFileName")
                    
                    val db = DownloadDatabase.getDatabase(context)
                    val item = DownloadItem(
                        id = System.currentTimeMillis(),
                        title = outFileName,
                        url = "https://www.youtube.com/watch",
                        mimeType = "video/mp4",
                        status = "COMPLETED",
                        progress = 100,
                        downloadedSize = outFile.length(),
                        totalSize = outFile.length(),
                        speed = "Completed",
                        category = "Videos",
                        filePath = outFile.absolutePath
                    )
                    db.downloadDao().insertDownload(item)

                    videoFile.delete()
                    audioFile.delete()

                    handler.post {
                        Toast.makeText(context, "Muxed video merged completely!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("YTProBridge", "Muxing inputs missing: video=${videoFile.exists()}, audio=${audioFile.exists()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun performMuxing(videoFile: File, audioFile: File, outFile: File) {
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.absolutePath)
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioFile.absolutePath)

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIdx = -1
        var videoSourceTrack = -1
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoSourceTrack = i
                videoTrackIdx = muxer.addTrack(format)
                break
            }
        }

        var audioTrackIdx = -1
        var audioSourceTrack = -1
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioSourceTrack = i
                audioTrackIdx = muxer.addTrack(format)
                break
            }
        }

        muxer.start()

        val bufferSize = 1024 * 1024
        val byteBuf = ByteBuffer.allocate(bufferSize)
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        if (videoSourceTrack >= 0 && videoTrackIdx >= 0) {
            videoExtractor.selectTrack(videoSourceTrack)
            while (true) {
                byteBuf.clear()
                val sampleSize = videoExtractor.readSampleData(byteBuf, 0)
                if (sampleSize < 0) {
                    break
                }
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIdx, byteBuf, bufferInfo)
                videoExtractor.advance()
            }
        }

        if (audioSourceTrack >= 0 && audioTrackIdx >= 0) {
            audioExtractor.selectTrack(audioSourceTrack)
            while (true) {
                byteBuf.clear()
                val sampleSize = audioExtractor.readSampleData(byteBuf, 0)
                if (sampleSize < 0) {
                    break
                }
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackIdx, byteBuf, bufferInfo)
                audioExtractor.advance()
            }
        }

        try {
            muxer.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    @JavascriptInterface
    fun getSNlM0e(securedCookies: String) {
        scope.launch {
            try {
                val url = URL("https://gemini.google.com/app")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Cookie", securedCookies)
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val regex = "\"SNlM0e\":\"(.*?)\"".toRegex()
                val match = regex.find(html)
                val token = match?.groupValues?.get(1) ?: ""
                
                handler.post {
                    webView.evaluateJavascript("if (typeof callbackSNlM0e === 'function') callbackSNlM0e.resolve('$token');", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    webView.evaluateJavascript("if (typeof callbackSNlM0e === 'function') callbackSNlM0e.resolve('');", null)
                }
            }
        }
    }

    @JavascriptInterface
    fun GeminiClient(endpoint: String, headersJson: String, body: String) {
        scope.launch {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 20000
                conn.readTimeout = 20000

                val headers = JSONObject(headersJson)
                val keys = headers.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = headers.getString(key)
                    conn.setRequestProperty(key, value)
                }

                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
                
                val responseText = if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP error ${conn.responseCode}"
                }
                conn.disconnect()

                val escapedResponse = JSONObject()
                escapedResponse.put("stream", responseText)
                val finalPayload = escapedResponse.toString()

                handler.post {
                    webView.evaluateJavascript("if (typeof callbackGeminiClient === 'function') callbackGeminiClient.resolve($finalPayload);", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val failPayload = JSONObject().apply { put("stream", "Error: ${e.message}") }.toString()
                handler.post {
                    webView.evaluateJavascript("if (typeof callbackGeminiClient === 'function') callbackGeminiClient.resolve($failPayload);", null)
                }
            }
        }
    }
}
