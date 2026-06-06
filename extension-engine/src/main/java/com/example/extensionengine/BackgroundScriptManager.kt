package com.example.extensionengine

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

class BackgroundScriptManager(
    private val context: Context,
    private val scriptInjector: ScriptInjector,
    private val messageBus: MessageBus
) {

    private val backgroundWebViews = mutableMapOf<String, WebView>()
    private var runtimeBridgeProvider: (() -> RuntimeBridge)? = null

    fun setRuntimeBridgeProvider(provider: () -> RuntimeBridge) {
        this.runtimeBridgeProvider = provider
    }

    /**
     * Instantiates an invisible WebView instance acting as the sandbox execution run-loop
     * for Chrome extension background workers/pages.
     */
    fun startBackgroundWorker(ext: ParsedExtension, bootstrapScript: String) {
        if (ext.backgroundScripts.isEmpty()) return
        if (backgroundWebViews.containsKey(ext.id)) return

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val wv = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true

                    val bridge = runtimeBridgeProvider?.invoke()
                    if (bridge != null) {
                        addJavascriptInterface(bridge, "OrionExtensionBridge")
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            // Load API bootstrap rules
                            evaluateJavascript(bootstrapScript, null)

                            // Load actual background JS scripts
                            val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)
                            for (scriptFile in ext.backgroundScripts) {
                                try {
                                    val code = readExtensionFile(extensionDir, scriptFile)
                                    if (code.isNotBlank()) {
                                        evaluateJavascript("(function() {\n $code \n})();", null)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                backgroundWebViews[ext.id] = wv
                wv.loadUrl("about:blank")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopBackgroundWorker(extensionId: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            backgroundWebViews.remove(extensionId)?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.destroy()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getBackgroundWebView(extensionId: String): WebView? = backgroundWebViews[extensionId]

    fun stopAll() {
        val keys = backgroundWebViews.keys.toList()
        keys.forEach { stopBackgroundWorker(it) }
    }

    private fun readExtensionFile(extensionDir: File, relativePath: String): String {
        val cleanPath = relativePath.removePrefix("./").removePrefix("/")
        val file = File(extensionDir, cleanPath)
        if (file.exists()) {
            return file.readText()
        }
        val filenameOnly = relativePath.substringAfterLast("/")
        val fallbackFile = File(extensionDir, filenameOnly)
        if (fallbackFile.exists()) {
            return fallbackFile.readText()
        }
        return ""
    }
}
