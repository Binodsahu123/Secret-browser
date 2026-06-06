package com.example.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView

class ExtensionManager(
    private val context: Context,
    private val delegate: BrowserDelegate?
) {
    val engine = ExtensionEngine(context, delegate)

    fun setupWebView(webView: WebView) {
        engine.setupWebView(webView)
    }

    fun injectContentScripts(webView: WebView, url: String) {
        engine.injectContentScripts(webView, url)
    }

    suspend fun installExtension(uri: Uri): ParsedExtension {
        return engine.installExtension(uri)
    }

    suspend fun uninstallExtension(id: String) {
        engine.uninstallExtension(id)
    }

    suspend fun toggleExtension(id: String, enabled: Boolean) {
        engine.toggleExtension(id, enabled)
    }

    fun shutdown() {
        engine.shutdown()
    }
}
