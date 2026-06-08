package com.example.mediaengine

import android.content.Context

interface MediaEngine {
    fun playMedia(mediaUrl: String)
}

class VideoDetector {
    fun detectVideoInPage(html: String): List<String> {
        val matches = Regex("<video[^>]*src=\"([^\"]*)\"").findAll(html)
        return matches.map { it.groupValues[1] }.toList()
    }
}

class AudioDetector {
    fun detectAudioInPage(html: String): List<String> {
        val matches = Regex("<audio[^>]*src=\"([^\"]*)\"").findAll(html)
        return matches.map { it.groupValues[1] }.toList()
    }
}

class PiPManager(private val context: Context) {
    fun enterPictureInPicture() {
        // enter pip mode
    }
}
