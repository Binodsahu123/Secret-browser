package com.example.translateengine

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

object DomReplacementEngine {

    /**
     * Builds and returns JavaScript to replace extracted node target contents with translated ones.
     */
    fun getReplacementJs(translationsJson: String): String {
        // Escaping backslashes and single quotes in the payload so that it stringifies safely in Javascript
        val escapedJson = translationsJson
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        return """
            (function() {
                var translations = JSON.parse('$escapedJson');
                if (!window.orionTextNodes) return "no_cache";
                
                var replacedCount = 0;
                translations.forEach(function(item) {
                    var node = window.orionTextNodes[item.id];
                    if (node) {
                        if (item.type === "text") {
                            node.nodeValue = item.translatedText;
                            node.orionLastTranslated = item.translatedText;
                            replacedCount++;
                        } else if (item.type === "placeholder") {
                            node.setAttribute('placeholder', item.translatedText);
                            replacedCount++;
                        } else if (item.type === "value") {
                            node.value = item.translatedText;
                            replacedCount++;
                        }
                    }
                });
                return "replaced_" + replacedCount;
            })()
        """.trimIndent()
    }

    /**
     * Executes the replacement in the WebView.
     */
    fun replaceContent(webView: WebView, translationsJson: String, callback: (String?) -> Unit = {}) {
        webView.post {
            webView.evaluateJavascript(getReplacementJs(translationsJson)) { res ->
                callback(res)
            }
        }
    }
}
