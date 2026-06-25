package com.example.browser

import android.app.ActivityManager
import android.content.Context
import android.webkit.WebView
import kotlin.collections.ArrayDeque

class WebViewPool(
    private val context: Context,
    private val maxSize: Int = calculateMaxPool(context)
) {
    private val pool = ArrayDeque<WebView>()
    private val lock = Any()

    companion object {
        fun calculateMaxPool(context: Context): Int {
            val am = context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val ramGB = info.totalMem / (1024L * 1024L * 1024L)
            return when {
                ramGB >= 6 -> 5
                ramGB >= 4 -> 4
                ramGB >= 3 -> 3
                else -> 2
            }
        }
    }

    fun acquire(
        applySettings: (WebView) -> Unit
    ): WebView {
        synchronized(lock) {
            val recycled = pool.removeFirstOrNull()
            if (recycled != null) {
                recycled.clearHistory()
                applySettings(recycled)
                return recycled
            }
        }
        return WebView(context).also { 
            applySettings(it) 
        }
    }

    fun recycle(webView: WebView) {
        synchronized(lock) {
            if (pool.size < maxSize) {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                pool.addLast(webView)
            } else {
                webView.destroy()
            }
        }
    }

    fun preWarm(
        count: Int = 2,
        applySettings: (WebView) -> Unit
    ) {
        repeat(count) {
            val wv = WebView(context)
            applySettings(wv)
            wv.loadUrl("about:blank")
            synchronized(lock) { pool.addLast(wv) }
        }
    }

    fun clear() {
        synchronized(lock) {
            pool.forEach { it.destroy() }
            pool.clear()
        }
    }
}
