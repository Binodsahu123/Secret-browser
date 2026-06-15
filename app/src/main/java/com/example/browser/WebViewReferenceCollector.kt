package com.example.browser

import android.webkit.WebView
import java.lang.ref.WeakReference

object WebViewReferenceCollector {
    private val activeWebViews = mutableListOf<WeakReference<WebView>>()

    fun register(webView: WebView) {
        activeWebViews.add(WeakReference(webView))
        activeWebViews.removeAll { it.get() == null }
    }
}
