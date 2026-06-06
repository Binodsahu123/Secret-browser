package com.example.extensionengine

import android.webkit.WebView

class ScriptInjector {

    /**
     * Physically runs a block of JavaScript code inside the target WebView content container.
     */
    fun injectScript(webView: WebView, code: String, onResult: ((String?) -> Unit)? = null) {
        webView.post {
            try {
                webView.evaluateJavascript(code) { value ->
                    onResult?.invoke(value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult?.invoke(null)
            }
        }
    }
}
