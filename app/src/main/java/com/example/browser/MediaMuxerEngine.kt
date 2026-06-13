package com.example.browser

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer

object MediaMuxerEngine {
    private const val TAG = "MediaMuxerEngine"

    interface MuxingCallback {
        fun onProgress(percentage: Int)
        fun onSuccess(outputFile: File)
        fun onError(error: String)
    }

    suspend fun downloadAndMux(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        outputFileName: String,
        callback: MuxingCallback,
        parentJob: Job? = null
    ) = withContext(Dispatchers.IO) {
        val tempVideoFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        val tempAudioFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.mp3")
        val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val finalOutputFile = File(publicDownloadsDir, outputFileName)

        try {
            if (parentJob?.isCancelled == true) return@withContext

            Log.d(TAG, "Starting streams download for muxing...")
            
            // Download Video Stream
            callback.onProgress(10)
            downloadFile(videoUrl, tempVideoFile, { progress ->
                callback.onProgress(10 + (progress * 0.4).toInt()) // 10% to 50%
            }, parentJob)

            if (parentJob?.isCancelled == true) throw Exception("Cancelled")

            // Download Audio Stream
            downloadFile(audioUrl, tempAudioFile, { progress ->
                callback.onProgress(50 + (progress * 0.3).toInt()) // 50% to 80%
            }, parentJob)

            if (parentJob?.isCancelled == true) throw Exception("Cancelled")

            callback.onProgress(85)
            Log.d(TAG, "Muxing downloaded streams...")
            mux(tempVideoFile.absolutePath, tempAudioFile.absolutePath, finalOutputFile.absolutePath)

            if (parentJob?.isCancelled == true) throw Exception("Cancelled")

            callback.onProgress(100)
            callback.onSuccess(finalOutputFile)

        } catch (e: Exception) {
            Log.e(TAG, "Muxing pipeline failed: ${e.message}", e)
            callback.onError(e.message ?: "Unknown error during download or muxing")
        } finally {
            // Auto clean temporary files
            if (tempVideoFile.exists()) tempVideoFile.delete()
            if (tempAudioFile.exists()) tempAudioFile.delete()
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

    private fun mux(videoPath: String, audioPath: String, outputPath: String) {
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoPath)
        
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioPath)

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var videoSourceTrack = -1
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoSourceTrack = i
                videoTrackIndex = muxer.addTrack(format)
                break
            }
        }

        var audioTrackIndex = -1
        var audioSourceTrack = -1
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioSourceTrack = i
                audioTrackIndex = muxer.addTrack(format)
                break
            }
        }

        muxer.start()

        if (videoSourceTrack >= 0 && videoTrackIndex >= 0) {
            videoExtractor.selectTrack(videoSourceTrack)
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }
        }

        if (audioSourceTrack >= 0 && audioTrackIndex >= 0) {
            audioExtractor.selectTrack(audioSourceTrack)
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
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
}
