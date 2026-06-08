package com.example.browserengine

import android.content.Context
import android.webkit.WebView
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class WebViewPool(private val context: Context) {
    private val idlePool: Queue<WebView> = ConcurrentLinkedQueue()
    private val maxIdleCount = 4

    fun warmUp(count: Int) {
        // Prepare pre-instantiated WebViews asynchronously or on UI state demand
        val limit = count.coerceAtMost(maxIdleCount)
        for (i in 0 until limit) {
            if (idlePool.size < maxIdleCount) {
                try {
                    val webView = createNewEmptyWebView()
                    idlePool.offer(webView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun acquireWebView(): WebView {
        val cached = idlePool.poll()
        if (cached != null) {
            return cached
        }
        return createNewEmptyWebView()
    }

    fun releaseWebView(webView: WebView) {
        try {
            webView.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
            }
            if (idlePool.size < maxIdleCount) {
                idlePool.offer(webView)
            } else {
                webView.destroy()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNewEmptyWebView(): WebView {
        return WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
    }
}
