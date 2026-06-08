package com.example.extensionengine

class CssInjector {

    /**
     * Appends a style block with raw CSS definitions into the document head container.
     */
    fun injectCss(evaluator: ScriptEvaluator, cssContent: String) {
        if (cssContent.isBlank()) return
        evaluator.post {
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
                
                evaluator.evaluateJavascript(stylePayloadScript, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
