package com.example.translateengine

import android.webkit.WebView

object DomRestoreEngine {

    /**
     * Executes the restore in the WebView in-place, keeping layout and scroll position intact.
     */
    fun restoreOriginal(webView: WebView, callback: (String?) -> Unit = {}) {
        OriginalDomSnapshotManager.restoreFromSnapshot(webView, callback)
    }
}
