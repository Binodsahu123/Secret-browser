package com.example.browser

import android.webkit.WebView

object TabStateManager {
    fun suspendInactiveTabs(
        activeTabId: String,
        tabs: List<TabState>,
        webViewMap: Map<String, WebView>
    ): List<TabState> {
        return tabs.map { tab ->
            if (tab.id != activeTabId && !tab.isWebViewDestroyed) {
                webViewMap[tab.id]?.let { webView ->
                    try {
                        webView.onPause()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                tab
            } else {
                tab
            }
        }
    }
}
