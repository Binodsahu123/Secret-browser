package com.example.extensionengine

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

interface BrowserDelegate {
    fun queryTabs(queryInfo: JSONObject): JSONArray
    fun createTab(url: String, active: Boolean)
    fun removeTab(tabId: String)
    fun reloadTab(tabId: String)
    fun updateTab(tabId: String, url: String)
    fun showNotification(title: String, message: String)
    fun downloadFile(url: String, filename: String?)
    fun getActiveTabId(): String?
    fun executeScriptOnTab(tabId: String, code: String, callback: (String?) -> Unit)
}

class RuntimeBridge(
    private val context: Context,
    private val webView: WebView?,
    private val storageManager: StorageManager,
    private val messageBus: MessageBus,
    private val delegate: BrowserDelegate?,
    private val eventManager: EventManager
) {

    private val mainScope = CoroutineScope(Dispatchers.Main)

    /**
     * Entry point invoked from sandboxed Chrome script runtime calls.
     */
    @JavascriptInterface
    fun postMessage(payloadJsonStr: String, callbackId: String) {
        val root = try {
            JSONObject(payloadJsonStr)
        } catch (e: Exception) {
            return
        }

        val api = root.optString("api", "")
        val extensionId = root.optString("extensionId", "")
        val args = root.optJSONArray("args") ?: JSONArray()

        mainScope.launch {
            try {
                handleApiCall(api, extensionId, args, callbackId)
            } catch (e: Exception) {
                e.printStackTrace()
                sendErrorResponse(callbackId, e.localizedMessage ?: "Unknown bridge error")
            }
        }
    }

    private suspend fun handleApiCall(api: String, extensionId: String, args: JSONArray, callbackId: String) {
        when {
            api.startsWith("storage.") -> {
                val area = args.optString(0, "local")
                when {
                    api.endsWith(".get") -> {
                        val keysObj = args.opt(1)
                        val result = storageManager.get(extensionId, area, keysObj)
                        sendSuccessResponse(callbackId, result)
                    }
                    api.endsWith(".set") -> {
                        val items = args.optJSONObject(1) ?: JSONObject()
                        storageManager.set(extensionId, area, items)
                        sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
                    }
                    api.endsWith(".remove") -> {
                        val keysArray = args.optJSONArray(1)
                        val keysList = mutableListOf<String>()
                        if (keysArray != null) {
                            for (i in 0 until keysArray.length()) {
                                keysList.add(keysArray.getString(i))
                            }
                        } else {
                            val singleKey = args.optString(1, "")
                            if (singleKey.isNotBlank()) keysList.add(singleKey)
                        }
                        storageManager.remove(extensionId, area, keysList)
                        sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
                    }
                    api.endsWith(".clear") -> {
                        storageManager.clear(extensionId, area)
                        sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
                    }
                }
            }
            api == "runtime.sendMessage" -> {
                val message = args.optJSONObject(0) ?: JSONObject()
                val senderTabId = delegate?.getActiveTabId()
                messageBus.broadcastMessage(extensionId, senderTabId, message)
                sendSuccessResponse(callbackId, JSONObject().put("status", "sent"))
            }
            api == "tabs.query" -> {
                val queryInfo = args.optJSONObject(0) ?: JSONObject()
                val tabsArray = delegate?.queryTabs(queryInfo) ?: JSONArray()
                sendSuccessResponse(callbackId, tabsArray)
            }
            api == "tabs.create" -> {
                val createProperties = args.optJSONObject(0) ?: JSONObject()
                val url = createProperties.optString("url", "about:blank")
                val active = createProperties.optBoolean("active", true)
                delegate?.createTab(url, active)
                sendSuccessResponse(callbackId, JSONObject().put("status", "created"))
            }
            api == "tabs.remove" -> {
                val tabId = args.optString(0, "")
                if (tabId.isNotBlank()) {
                    delegate?.removeTab(tabId)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "removed"))
            }
            api == "tabs.reload" -> {
                val tabId = args.optString(0, "")
                delegate?.reloadTab(tabId)
                sendSuccessResponse(callbackId, JSONObject().put("status", "reloaded"))
            }
            api == "tabs.update" -> {
                val tabId = args.optString(0, "")
                val updateProperties = args.optJSONObject(1) ?: JSONObject()
                val url = updateProperties.optString("url", "")
                if (url.isNotBlank()) {
                    delegate?.updateTab(tabId, url)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "updated"))
            }
            api == "notifications.create" -> {
                val title = args.optString(0, "Extension Alert")
                val options = args.optJSONObject(1) ?: JSONObject()
                val message = options.optString("message", options.optString("title", "Alert"))
                delegate?.showNotification(title, message)
                sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
            }
            api == "downloads.download" -> {
                val options = args.optJSONObject(0) ?: JSONObject()
                val url = options.optString("url", "")
                val filename = options.optString("filename", null)
                if (url.isNotBlank()) {
                    delegate?.downloadFile(url, filename)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
            }
            api == "scripting.executeScript" -> {
                val spec = args.optJSONObject(0) ?: JSONObject()
                val target = spec.optJSONObject("target") ?: JSONObject()
                val tabId = target.optString("tabId", "")
                val funcCode = spec.optString("func", "")
                
                if (tabId.isNotBlank() && funcCode.isNotBlank()) {
                    delegate?.executeScriptOnTab(tabId, "($funcCode)();") { res ->
                        sendSuccessResponse(callbackId, JSONObject().put("result", res ?: ""))
                    }
                } else {
                    sendSuccessResponse(callbackId, JSONObject().put("status", "invalid_arguments"))
                }
            }
            else -> {
                sendErrorResponse(callbackId, "API $api is unsupported or missing permissions.")
            }
        }
    }

    private fun sendSuccessResponse(callbackId: String, result: Any) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val resStr = result.toString()
                webView?.evaluateJavascript("window._extResponse('$callbackId', null, $resStr)", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendErrorResponse(callbackId: String, errorMsg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val errObj = JSONObject().put("error", errorMsg).toString()
                webView?.evaluateJavascript("window._extResponse('$callbackId', $errObj, null)", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
