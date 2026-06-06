package com.example.extensionengine

import android.webkit.WebView

class CssInjector {

    /**
     * Creates and appends a stylesheet block containing raw CSS definitions into the host head container.
     */
    fun injectCss(webView: WebView, cssContent: String) {
        if (cssContent.isBlank()) return
        webView.post {
            try {
                val escapedCss = cssContent
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", " ")
                    .replace("\r", " ")
                
                val stylePayloadScript = """
                    (function() {
                        const style = document.createElement('style');
                        style.type = 'text/css';
                        style.innerHTML = '$escapedCss';
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(stylePayloadScript, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
