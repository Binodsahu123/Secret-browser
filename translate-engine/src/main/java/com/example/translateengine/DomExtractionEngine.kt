package com.example.translateengine

import android.webkit.WebView

object DomExtractionEngine {
    
    /**
     * Builds and returns Javascript to extract translatable content.
     * Extracts text nodes (excluding script, style, code, iframe tags, and strings that look like URLs)
     * and input/textarea placeholders. Registers element references in `window.orionTextNodes`.
     */
    fun getExtractionJs(isDesktopMode: Boolean): String {
        return """
            (function() {
                var isDesktopMode = $isDesktopMode;
                function shouldIncludeEl(el) {
                    var temp = el;
                    while (temp && temp !== document.body) {
                        var s = window.getComputedStyle(temp);
                        if (s.display === 'none' || s.visibility === 'hidden') {
                            var cls = temp.className;
                            if (typeof cls === 'string') {
                                cls = cls.toLowerCase();
                                if (isDesktopMode) {
                                    if (cls.indexOf('mobile') >= 0 || cls.indexOf('hidden-lg') >= 0 || cls.indexOf('hidden-xl') >= 0 || cls.indexOf('visible-xs') >= 0 || cls.indexOf('visible-sm') >= 0) {
                                        return false;
                                    }
                                } else {
                                    if (cls.indexOf('desktop') >= 0 || cls.indexOf('hidden-xs') >= 0 || cls.indexOf('hidden-sm') >= 0 || cls.indexOf('visible-lg') >= 0 || cls.indexOf('visible-xl') >= 0) {
                                        return false;
                                    }
                                }
                            }
                        }
                        temp = temp.parentElement;
                    }
                    return true;
                }

                var walker = document.createTreeWalker(
                    document.body,
                    NodeFilter.SHOW_TEXT,
                    {
                        acceptNode: function(node) {
                            var parent = node.parentElement;
                            if (!parent) return NodeFilter.FILTER_REJECT;
                            var tag = parent.tagName.toLowerCase();
                            if (tag === 'script' || tag === 'style' || tag === 'iframe' || tag === 'code' || tag === 'noscript' || tag === 'pre' || tag === 'svg' || tag === 'canvas') {
                                return NodeFilter.FILTER_REJECT;
                            }
                            if (!shouldIncludeEl(parent)) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            var text = node.nodeValue ? node.nodeValue.trim() : "";
                            if (text.length === 0) return NodeFilter.FILTER_REJECT;
                            if (text.match(/^(https?:\/\/|www\.)/i)) return NodeFilter.FILTER_REJECT;
                            return NodeFilter.FILTER_ACCEPT;
                        }
                    }
                );
                
                var items = [];
                var node;
                var idx = 0;
                window.orionTextNodes = window.orionTextNodes || {};
                window.orionOriginalSnapshot = window.orionOriginalSnapshot || {};
                
                while(node = walker.nextNode()) {
                    if (!node.orionTrId) {
                        node.orionTrId = "text_" + idx++;
                        window.orionTextNodes[node.orionTrId] = node;
                        node.originalText = node.nodeValue;
                        window.orionOriginalSnapshot[node.orionTrId] = node.nodeValue;
                    }
                    var textVal = node.nodeValue ? node.nodeValue.trim() : "";
                    if (textVal.length > 0) {
                        items.push({
                            id: node.orionTrId,
                            type: "text",
                            text: textVal
                        });
                    }
                }
                
                var placeholders = document.querySelectorAll('input[placeholder], textarea[placeholder]');
                placeholders.forEach(function(el) {
                    if (!shouldIncludeEl(el)) return;
                    if (!el.orionTrId) {
                        el.orionTrId = "place_" + idx++;
                        window.orionTextNodes[el.orionTrId] = el;
                        el.originalPlaceholder = el.getAttribute('placeholder');
                        window.orionOriginalSnapshot[el.orionTrId + "_place"] = el.getAttribute('placeholder');
                    }
                    var placeVal = el.getAttribute('placeholder') ? el.getAttribute('placeholder').trim() : "";
                    if (placeVal.length > 0) {
                        items.push({
                            id: el.orionTrId,
                            type: "placeholder",
                            text: placeVal
                        });
                    }
                });

                var buttons = document.querySelectorAll('input[type="button"], input[type="submit"], input[type="reset"]');
                buttons.forEach(function(el) {
                    if (!shouldIncludeEl(el)) return;
                    var val = el.value ? el.value.trim() : "";
                    if (val.length === 0) return;
                    if (!el.orionTrId) {
                        el.orionTrId = "buttonval_" + idx++;
                        window.orionTextNodes[el.orionTrId] = el;
                        el.originalValue = el.value;
                        window.orionOriginalSnapshot[el.orionTrId + "_val"] = el.value;
                    }
                    items.push({
                        id: el.orionTrId,
                        type: "value",
                        text: val
                    });
                });
                
                return JSON.stringify(items);
            })()
        """.trimIndent()
    }

    /**
     * Executes extraction script on a given webview.
     */
    fun extractContent(webView: WebView, isDesktopMode: Boolean, callback: (String?) -> Unit) {
        webView.post {
            webView.evaluateJavascript(getExtractionJs(isDesktopMode)) { value ->
                if (value == null || value == "null") {
                    callback(null)
                } else {
                    // Stripping outer escape quotes if returned as a JSON string literal
                    var cleaned = value
                    if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                        cleaned = cleaned.substring(1, cleaned.length - 1)
                        // Decode simple unicode escape sequences and JSON characters
                        cleaned = cleaned.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
                    }
                    callback(cleaned)
                }
            }
        }
    }
}
