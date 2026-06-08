package com.example.browserengine

import android.content.Context
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

class TabPoolManager(
    private val context: Context,
    val webViewPool: WebViewPool = WebViewPool(context),
    val memoryManager: TabMemoryManager = TabMemoryManager()
) {
    private val tabWebViewMap = ConcurrentHashMap<String, WebView>()

    fun getOrCreateWebViewForTab(tabId: String): WebView {
        memoryManager.recordTabAccess(tabId)
        val existing = tabWebViewMap[tabId]
        if (existing != null) {
            return existing
        }
        
        // Take a warmed up WebView or spin a fresh one up from the pool
        val webView = webViewPool.acquireWebView()
        tabWebViewMap[tabId] = webView
        return webView
    }

    fun containsTabWebView(tabId: String): Boolean {
        return tabWebViewMap.containsKey(tabId)
    }

    fun associateWebViewForTab(tabId: String, webView: WebView) {
        tabWebViewMap[tabId] = webView
        memoryManager.recordTabAccess(tabId)
    }

    fun releaseTabWebView(tabId: String) {
        val webView = tabWebViewMap.remove(tabId)
        if (webView != null) {
            webViewPool.releaseWebView(webView)
        }
        memoryManager.removeTab(tabId)
    }

    fun removeAssociationOnly(tabId: String): WebView? {
        memoryManager.removeTab(tabId)
        return tabWebViewMap.remove(tabId)
    }

    fun getActiveWebView(tabId: String): WebView? {
        return tabWebViewMap[tabId]
    }

    fun getWebViewMap(): Map<String, WebView> {
        return tabWebViewMap.toMap()
    }

    fun trimBackgroundTabs(currentlyKeep: List<String>) {
        val toTrim = memoryManager.getTabsToTrim(tabWebViewMap.keys.toList())
        for (tabId in toTrim) {
            if (tabId !in currentlyKeep) {
                releaseTabWebView(tabId)
            }
        }
    }

    fun clearAll() {
        tabWebViewMap.keys.forEach { tabId ->
            releaseTabWebView(tabId)
        }
        tabWebViewMap.clear()
        memoryManager.clearAll()
    }
}
