package com.example.browser

import android.webkit.WebView

object TabStateManager {
    private const val TAG = "TabStateManager"

    // Suspend inactive tabs when active webviews exceed a conservative limit, freeing system RAM
    fun suspendInactiveTabs(
        activeTabId: String,
        tabs: List<TabState>,
        webViewMap: MutableMap<String, WebView>
    ): List<TabState> {
        val maxActiveTabs = 4
        val activeTabsInMap = webViewMap.keys.toList()
        
        if (tabs.size <= maxActiveTabs && activeTabsInMap.size <= maxActiveTabs) {
            return tabs
        }

        // Sort inactive tabs by last active time ascending (oldest first)
        val sortedInactiveTabs = tabs
            .filter { it.id != activeTabId && !it.isWebViewDestroyed && webViewMap.containsKey(it.id) }
            .sortedBy { it.lastActiveTime }

        val currentActiveCount = webViewMap.size
        val numToSuspend = (currentActiveCount - maxActiveTabs).coerceAtLeast(0)
        if (numToSuspend <= 0) return tabs

        val suspendedIds = sortedInactiveTabs.take(numToSuspend).map { it.id }.toSet()
        if (suspendedIds.isEmpty()) return tabs

        android.util.Log.i(TAG, "Suspending $numToSuspend inactive background tabs to resolve memory density. Target IDs: $suspendedIds")

        // Tear down the background webviews completely to free substantial native heap allocation
        suspendedIds.forEach { id ->
            val webView = webViewMap.remove(id)
            webView?.let {
                try {
                    // Save WebView state to file before shredding it!
                    saveWebViewStateToFile(it.context, id, it)
                    
                    it.stopLoading()
                    it.clearHistory()
                    it.removeAllViews()
                    it.destroy()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Tear down error during suspension of WebView $id: ${e.localizedMessage}")
                }
            }
        }

        // Return updated list of tabs with isWebViewDestroyed flag turned on
        return tabs.map { tab ->
            if (suspendedIds.contains(tab.id)) {
                tab.copy(isWebViewDestroyed = true)
            } else {
                tab
            }
        }
    }

    fun saveWebViewStateToFile(context: android.content.Context, tabId: String, webView: WebView) {
        try {
            val bundle = android.os.Bundle()
            webView.saveState(bundle)
            val file = java.io.File(context.cacheDir, "webview_state_$tabId.bin")
            val parcel = android.os.Parcel.obtain()
            bundle.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            file.writeBytes(bytes)
            android.util.Log.i("TabStateManager", "Saved state file for tab $tabId: ${file.length()} bytes")
        } catch (e: Exception) {
            android.util.Log.e("TabStateManager", "Failed to save state file for tab $tabId", e)
        }
    }

    fun restoreWebViewStateFromFile(context: android.content.Context, tabId: String, webView: WebView): Boolean {
        try {
            val file = java.io.File(context.cacheDir, "webview_state_$tabId.bin")
            if (!file.exists()) return false
            val bytes = file.readBytes()
            val parcel = android.os.Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val bundle = android.os.Bundle()
            bundle.readFromParcel(parcel)
            parcel.recycle()
            webView.restoreState(bundle)
            android.util.Log.i("TabStateManager", "Restored state file for tab $tabId successfully")
            return true
        } catch (e: Exception) {
            android.util.Log.e("TabStateManager", "Failed to restore state file for tab $tabId", e)
            return false
        }
    }
}

object AdaptiveLoadingEngine {
    private const val TAG = "AdaptiveLoading"

    // Preloader AI concurrency controls based on dynamic live JVM heap pressure ratio
    fun getPreloadConcurrencyLimit(): Int {
        val report = MemoryLeakDetector.generateMemoryReport()
        val rt = Runtime.getRuntime()
        val usedRatio = report.usedHeapMemoryBytes.toDouble() / rt.maxMemory().toDouble()
        
        return when {
            usedRatio > 0.85 -> 0  // Suspend background loads entirely
            usedRatio > 0.70 -> 1  // Ultra defensive: single background stream
            usedRatio > 0.50 -> 2  // Conservative
            else -> 4             // Standard full execution
        }
    }

    fun shouldRunSystemGC(): Boolean {
        val report = MemoryLeakDetector.generateMemoryReport()
        return report.isCriticalState
    }
}
