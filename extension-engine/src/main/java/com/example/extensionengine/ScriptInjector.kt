package com.example.extensionengine

/**
 * Interface that abstracts dynamic script/code compilation targets.
 * Decouples extension content injections from platform-specific WebViews.
 */
interface ScriptEvaluator {
    fun evaluateJavascript(code: String, callback: ((String?) -> Unit)? = null)
    fun post(action: () -> Unit)
}

class ScriptInjector {

    /**
     * Runs a block of JavaScript code inside the target evaluator scope.
     */
    fun injectScript(evaluator: ScriptEvaluator, code: String, onResult: ((String?) -> Unit)? = null) {
        evaluator.post {
            try {
                evaluator.evaluateJavascript(code) { value ->
                    onResult?.invoke(value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult?.invoke(null)
            }
        }
    }
}
