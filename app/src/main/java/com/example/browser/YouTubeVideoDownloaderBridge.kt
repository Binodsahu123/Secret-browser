package com.example.browser

import android.content.Context
import android.webkit.JavascriptInterface

class YouTubeVideoDownloaderBridge(private val context: Context) {
    @JavascriptInterface
    fun showToast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
