package com.example.translateengine

import android.webkit.WebView

object OriginalDomSnapshotManager {
    /**
     * Snapshots original text values of the nodes currently present in the document.
     * This prepares the page so we can restore original text at any time without reload.
     */
    fun createSnapshot(webView: WebView, callback: (Boolean) -> Unit = {}) {
        val js = """
            (function() {
                if (!window.orionTextNodes) return false;
                window.orionOriginalSnapshot = window.orionOriginalSnapshot || {};
                var count = 0;
                for (var key in window.orionTextNodes) {
                    var node = window.orionTextNodes[key];
                    if (node) {
                        if (node.nodeType === Node.TEXT_NODE) {
                            if (node.originalText !== undefined) {
                                window.orionOriginalSnapshot[key] = node.originalText;
                                count++;
                            }
                        } else if (node.tagName) {
                            if (node.originalPlaceholder !== undefined) {
                                window.orionOriginalSnapshot[key + "_place"] = node.originalPlaceholder;
                                count++;
                            }
                            if (node.originalValue !== undefined) {
                                window.orionOriginalSnapshot[key + "_val"] = node.originalValue;
                                count++;
                            }
                        }
                    }
                }
                return count > 0;
            })()
        """.trimIndent()
        webView.post {
            webView.evaluateJavascript(js) { res ->
                callback(res == "true")
            }
        }
    }

    /**
     * Restores original text values from the snapshot.
     */
    fun restoreFromSnapshot(webView: WebView, callback: (String?) -> Unit = {}) {
        val js = """
            (function() {
                if (!window.orionTextNodes || !window.orionOriginalSnapshot) return "no_snapshot";
                var restoredCount = 0;
                for (var key in window.orionTextNodes) {
                    var node = window.orionTextNodes[key];
                    if (node) {
                        if (node.nodeType === Node.TEXT_NODE) {
                            var original = window.orionOriginalSnapshot[key];
                            if (original !== undefined) {
                                node.nodeValue = original;
                                restoredCount++;
                            }
                        } else if (node.tagName) {
                            var originalPlace = window.orionOriginalSnapshot[key + "_place"];
                            if (originalPlace !== undefined) {
                                node.setAttribute('placeholder', originalPlace);
                                restoredCount++;
                            }
                            var originalVal = window.orionOriginalSnapshot[key + "_val"];
                            if (originalVal !== undefined) {
                                node.value = originalVal;
                                restoredCount++;
                            }
                        }
                    }
                }
                return "restored_" + restoredCount;
            })()
        """.trimIndent()
        webView.post {
            webView.evaluateJavascript(js) { res ->
                callback(res)
            }
        }
    }
}
