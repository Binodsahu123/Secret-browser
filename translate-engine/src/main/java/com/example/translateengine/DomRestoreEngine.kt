package com.example.translateengine

import android.webkit.WebView

object DomRestoreEngine {

    /**
     * Builds and returns JavaScript to restore original texts/placeholders/values.
     */
    fun getRestoreJs(): String {
        return """
            (function() {
                if (!window.orionTextNodes) return "no_nodes";
                var restoredCount = 0;
                for (var key in window.orionTextNodes) {
                    var node = window.orionTextNodes[key];
                    if (node) {
                        if (node.nodeType === Node.TEXT_NODE) {
                            if (node.originalText !== undefined) {
                                node.nodeValue = node.originalText;
                                restoredCount++;
                            }
                        } else if (node.tagName) {
                            if (node.originalPlaceholder !== undefined) {
                                node.setAttribute('placeholder', node.originalPlaceholder);
                                restoredCount++;
                            }
                            if (node.originalValue !== undefined) {
                                node.value = node.originalValue;
                                restoredCount++;
                            }
                        }
                    }
                }
                return "restored_" + restoredCount;
            })()
        """.trimIndent()
    }

    /**
     * Executes the restore in the WebView in-place, keeping layout and scroll position intact.
     */
    fun restoreOriginal(webView: WebView, callback: (String?) -> Unit = {}) {
        webView.post {
            webView.evaluateJavascript(getRestoreJs()) { res ->
                callback(res)
            }
        }
    }
}
