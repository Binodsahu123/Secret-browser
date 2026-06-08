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
    private val eventManager: EventManager,
    val tabId: String? = null,
    private val portManager: PortManager,
    private val tabBridge: TabBridge
) : MessageListener, PortConnectionListener {

    private val mainScope = CoroutineScope(Dispatchers.Main)

    init {
        messageBus.registerListener(this)
        messageBus.registerPortListener(this)
    }

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
                val rawMsg = args.opt(0)
                val message = args.optJSONObject(0) ?: JSONObject().put("__is_wrapped__", true).put("value", rawMsg)
                val senderTabId = tabId ?: delegate?.getActiveTabId()
                messageBus.broadcastMessage(extensionId, senderTabId, message, callbackId)
            }
            api == "runtime.response" -> {
                val targetCallbackId = args.optString(0, "")
                val responseData = args.opt(1) ?: JSONObject()
                messageBus.broadcastResponse(extensionId, targetCallbackId, responseData)
            }
            api == "tabs.sendMessage" -> {
                val targetTabIdRaw = args.optString(0, "")
                val targetTabId = TabIdMapper.getUuidFromString(targetTabIdRaw)
                val rawMsg = args.opt(1)
                val message = args.optJSONObject(1) ?: JSONObject().put("__is_wrapped__", true).put("value", rawMsg)
                tabBridge.sendMessage(extensionId, targetTabId, message, callbackId)
            }
            api == "tabs.connect" -> {
                val targetTabIdRaw = args.optString(0, "")
                val targetTabId = TabIdMapper.getUuidFromString(targetTabIdRaw)
                val channelId = args.optString(1, "")
                val portName = args.optString(2, "")
                tabBridge.connect(extensionId, targetTabId, channelId, portName)
                sendSuccessResponse(callbackId, JSONObject().put("status", "connected"))
            }
            api == "runtime.portConnect" -> {
                val targetId = args.optString(0, extensionId)
                val channelId = args.optString(1, "")
                val portName = args.optString(2, "")
                portManager.connect(targetId, channelId, portName, extensionId)
                sendSuccessResponse(callbackId, JSONObject().put("status", "connected"))
            }
            api == "runtime.portPostMessage" -> {
                val channelId = args.optString(0, "")
                val rawMsg = args.opt(1)
                val message = args.optJSONObject(1) ?: JSONObject().put("__is_wrapped__", true).put("value", rawMsg)
                portManager.postMessage(channelId, message)
                sendSuccessResponse(callbackId, JSONObject().put("status", "sent"))
            }
            api == "runtime.portDisconnect" -> {
                val channelId = args.optString(0, "")
                portManager.disconnect(channelId)
                sendSuccessResponse(callbackId, JSONObject().put("status", "disconnected"))
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
                val tabIdRaw = args.optString(0, "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                if (resolvedTabId.isNotBlank()) {
                    delegate?.removeTab(resolvedTabId)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "removed"))
            }
            api == "tabs.reload" -> {
                val tabIdRaw = args.optString(0, "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                delegate?.reloadTab(resolvedTabId)
                sendSuccessResponse(callbackId, JSONObject().put("status", "reloaded"))
            }
            api == "tabs.update" -> {
                val tabIdRaw = args.optString(0, "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                val updateProperties = args.optJSONObject(1) ?: JSONObject()
                val url = updateProperties.optString("url", "")
                if (url.isNotBlank()) {
                    delegate?.updateTab(resolvedTabId, url)
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
                val filename = if (options.has("filename")) options.optString("filename") else null
                if (url.isNotBlank()) {
                    delegate?.downloadFile(url, filename)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "success"))
            }
            api == "scripting.executeScript" -> {
                val spec = args.optJSONObject(0) ?: JSONObject()
                val target = spec.optJSONObject("target") ?: JSONObject()
                val tabIdRaw = target.optString("tabId", "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                val funcCode = spec.optString("func", "")
                
                if (resolvedTabId.isNotBlank() && funcCode.isNotBlank()) {
                    val del = delegate
                    if (del != null) {
                        del.executeScriptOnTab(resolvedTabId, "($funcCode)();") { res ->
                            sendSuccessResponse(callbackId, JSONObject().put("result", res ?: ""))
                        }
                    } else {
                        sendSuccessResponse(callbackId, JSONObject().put("status", "no_delegate"))
                    }
                } else {
                    sendSuccessResponse(callbackId, JSONObject().put("status", "invalid_arguments"))
                }
            }
            api == "event.addListener" -> {
                val eventName = args.optString(0, "")
                if (eventName.isNotBlank()) {
                    eventManager.addListener(eventName, extensionId)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "added"))
            }
            api == "event.removeListener" -> {
                val eventName = args.optString(0, "")
                if (eventName.isNotBlank()) {
                    eventManager.removeListener(eventName, extensionId)
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "removed"))
            }
            else -> {
                sendErrorResponse(callbackId, "API $api is unsupported or missing permissions.")
            }
        }
    }

    override fun onMessageReceived(extensionId: String, senderTabId: String?, message: JSONObject, callbackId: String?, targetTabId: String?) {
        val view = webView ?: return
        val tab = tabId
        if (targetTabId != null) {
            if (tab != targetTabId) return
        } else {
            if (tab != null) return
        }

        view.post {
            try {
                val isWrapped = message.optBoolean("__is_wrapped__", false)
                val msgStr = if (isWrapped) {
                    val wrappedVal = message.opt("value")
                    if (wrappedVal is String) {
                        JSONObject.quote(wrappedVal)
                    } else {
                        wrappedVal?.toString() ?: "null"
                    }
                } else {
                    message.toString()
                }
                val senderJson = JSONObject().apply {
                    put("id", extensionId)
                    if (senderTabId != null) {
                        val intTabId = TabIdMapper.getIntId(senderTabId)
                        val tabObj = JSONObject().put("id", intTabId)
                        put("tab", tabObj)
                    }
                }.toString()
                val cbParam = if (callbackId != null) "'$callbackId'" else "null"
                view.evaluateJavascript(
                    "if(window._extOnMessage) { window._extOnMessage('$extensionId', $msgStr, $senderJson, $cbParam); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResponseReceived(extensionId: String, callbackId: String, response: Any) {
        val view = webView ?: return
        view.post {
            try {
                val resStr = if (response is String) {
                    JSONObject.quote(response)
                } else {
                    response.toString()
                }
                view.evaluateJavascript(
                    "if(window._extResponse) { window._extResponse('$callbackId', null, $resStr); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPortConnect(extensionId: String, channelId: String, portName: String, senderId: String) {
        val view = webView ?: return
        view.post {
            try {
                view.evaluateJavascript(
                    "if(window._extPortConnect) { window._extPortConnect('$channelId', '$portName', '$senderId'); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPortMessage(channelId: String, message: JSONObject) {
        val view = webView ?: return
        view.post {
            try {
                val isWrapped = message.optBoolean("__is_wrapped__", false)
                val msgStr = if (isWrapped) {
                    val wrappedVal = message.opt("value")
                    if (wrappedVal is String) {
                        JSONObject.quote(wrappedVal)
                    } else {
                        wrappedVal?.toString() ?: "null"
                    }
                } else {
                    message.toString()
                }
                view.evaluateJavascript(
                    "if(window._extPortMessage) { window._extPortMessage('$channelId', $msgStr); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPortDisconnect(channelId: String) {
        val view = webView ?: return
        view.post {
            try {
                view.evaluateJavascript(
                    "if(window._extPortDisconnect) { window._extPortDisconnect('$channelId'); }",
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
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
