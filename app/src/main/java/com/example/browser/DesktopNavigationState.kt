package com.example.browser

import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

object DesktopNavigationState {
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun captureState(tabId: String, webView: WebView) {
        val scrollX = webView.scrollX
        val scrollY = webView.scrollY
        scrollPositions[tabId] = Pair(scrollX, scrollY)
    }

    fun restoreState(tabId: String, webView: WebView) {
        val pos = scrollPositions[tabId] ?: return
        webView.postDelayed({
            webView.scrollTo(pos.first, pos.second)
        }, 300)
    }

    fun clearState(tabId: String) {
        scrollPositions.remove(tabId)
    }
}
