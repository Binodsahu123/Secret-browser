package com.example.mediadetectorengine

import android.webkit.JavascriptInterface
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class WebViewMediaBridge(
    private val onCandidates: (List<MediaCandidateModel>) -> Unit,
    private val onRoute: (String) -> Unit,
    private val onTriggerScan: () -> Unit
) {
    private val TAG = "WebViewMediaBridge"

    @JavascriptInterface
    fun onCandidatesDetected(json: String) {
        try {
            MediaDebugLogger.log(TAG, "onCandidatesDetected callback triggered with json string.", "INFO", "JS_PROBE")
            val array = JSONArray(json)
            val candidates = mutableListOf<MediaCandidateModel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.getString("url")
                val title = obj.getString("title")
                val type = obj.getString("type")
                val mimeType = obj.getString("mimeType")
                val sourcePage = obj.getString("sourcePage")
                val quality = obj.optString("quality", "Auto")
                val sourceElement = obj.optString("sourceElement", "dom")
                val confidence = obj.optInt("confidence", 80)
                
                // Match with native C++ rules engine!
                val ruleResultStr = MediaDetector.matchNativeRules(url, sourcePage)
                val ruleJson = JSONObject(ruleResultStr)
                val supportedState = ruleJson.optString("status", "supported")
                val supportReason = ruleJson.optString("reason", "Parsed candidate")
                val isProtected = ruleJson.optBoolean("isProtected", false)

                candidates.add(
                    MediaCandidateModel(
                        url = url,
                        title = title,
                        type = type,
                        mimeType = mimeType,
                        sourcePage = sourcePage,
                        quality = quality,
                        sourceElement = sourceElement,
                        confidence = confidence,
                        supportedState = supportedState,
                        supportReason = supportReason,
                        isProtected = isProtected
                    )
                )
            }
            onCandidates(candidates)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing candidates json", e)
            MediaDebugLogger.log(TAG, "Error parsing candidates JSON: ${e.message}", "ERROR", "KOTLIN_CORE")
        }
    }

    @JavascriptInterface
    fun onRouteChanged(url: String) {
        MediaDebugLogger.log(TAG, "onRouteChanged: SPA URL mutated to $url", "INFO", "JS_PROBE")
        onRoute(url)
    }

    @JavascriptInterface
    fun triggerDomScan() {
        MediaDebugLogger.log(TAG, "triggerDomScan requested by dynamic mutation watcher in JS.", "INFO", "JS_PROBE")
        onTriggerScan()
    }
}
