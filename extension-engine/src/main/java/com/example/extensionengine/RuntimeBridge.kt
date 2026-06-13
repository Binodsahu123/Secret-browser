package com.example.extensionengine

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private var lastExtensionToast: android.widget.Toast? = null

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
                com.example.extensionengine.ExtensionDebuggerEngine.instance.logError(
                    extensionId,
                    when (extensionId) {
                        "ext_grok_automation" -> "Grok Automation"
                        "ext_dark_reader" -> "Dark Reader"
                        "ext_adblock" -> "AdBlock Plus"
                        "ext_metamask" -> "MetaMask Wallet"
                        "ext_grok_4" -> "Grok 4.0 AI"
                        "ext_cookies" -> "I don't care about cookies"
                        "ext_auto_translate" -> "Auto-Translate Extension"
                        else -> "Extension '$extensionId'"
                    },
                    com.example.extensionengine.DebugErrorType.RUNTIME,
                    "Runtime crash: ${e.message ?: e.toString()}"
                )
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
            api == "runtime.reload" -> {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        lastExtensionToast?.cancel()
                        val t = android.widget.Toast.makeText(context, "Reloading extension: $extensionId", android.widget.Toast.LENGTH_SHORT)
                        lastExtensionToast = t
                        t.show()
                    } catch (e: Exception) {}
                }
                sendSuccessResponse(callbackId, JSONObject().put("status", "reloading"))
            }
            api == "tabs.get" -> {
                val tabIdRaw = args.optString(0, "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                val allTabs = delegate?.queryTabs(JSONObject()) ?: JSONArray()
                var foundTab: JSONObject? = null
                val intTargetId = tabIdRaw.toIntOrNull()
                for (i in 0 until allTabs.length()) {
                    val t = allTabs.optJSONObject(i)
                    if (t != null) {
                        val idVal = t.opt("id")
                        if (idVal == intTargetId || (idVal != null && idVal.toString() == tabIdRaw) || (idVal != null && idVal.toString() == resolvedTabId)) {
                            foundTab = t
                            break
                        }
                    }
                }
                if (foundTab != null) {
                    sendSuccessResponse(callbackId, foundTab)
                } else {
                    sendErrorResponse(callbackId, "Tab not found: $tabIdRaw")
                }
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
                val files = spec.optJSONArray("files")

                val codeToExecute = StringBuilder()
                if (funcCode.isNotBlank()) {
                    codeToExecute.append("($funcCode)();")
                } else if (files != null && files.length() > 0) {
                    val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
                    for (i in 0 until files.length()) {
                        val path = files.optString(i, "")
                        if (path.isNotBlank()) {
                            val cleanPath = path.removePrefix("./").removePrefix("/")
                            val file = java.io.File(extensionDir, cleanPath)
                            if (file.exists()) {
                                codeToExecute.append(file.readText()).append("\n")
                            } else {
                                val fallbackFile = java.io.File(extensionDir, path.substringAfterLast("/"))
                                if (fallbackFile.exists()) {
                                    codeToExecute.append(fallbackFile.readText()).append("\n")
                                }
                            }
                        }
                    }
                }

                val finalCode = codeToExecute.toString()
                if (resolvedTabId.isNotBlank() && finalCode.isNotBlank()) {
                    val del = delegate
                    if (del != null) {
                        del.executeScriptOnTab(resolvedTabId, finalCode) { res ->
                            sendSuccessResponse(callbackId, JSONObject().put("result", res ?: ""))
                        }
                    } else {
                        sendSuccessResponse(callbackId, JSONObject().put("status", "no_delegate"))
                    }
                } else {
                    sendSuccessResponse(callbackId, JSONObject().put("status", "invalid_arguments"))
                }
            }
            api == "scripting.insertCSS" -> {
                val spec = args.optJSONObject(0) ?: JSONObject()
                val target = spec.optJSONObject("target") ?: JSONObject()
                val tabIdRaw = target.optString("tabId", "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                val css = spec.optString("css", "")
                val files = spec.optJSONArray("files")

                val cssToInject = StringBuilder()
                if (css.isNotBlank()) {
                    cssToInject.append(css)
                } else if (files != null && files.length() > 0) {
                    val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
                    for (i in 0 until files.length()) {
                        val path = files.optString(i, "")
                        if (path.isNotBlank()) {
                            val cleanPath = path.removePrefix("./").removePrefix("/")
                            val file = java.io.File(extensionDir, cleanPath)
                            if (file.exists()) {
                                cssToInject.append(file.readText()).append("\n")
                            } else {
                                val fallbackFile = java.io.File(extensionDir, path.substringAfterLast("/"))
                                if (fallbackFile.exists()) {
                                    cssToInject.append(fallbackFile.readText()).append("\n")
                                }
                            }
                        }
                    }
                }

                val finalCss = cssToInject.toString()
                if (resolvedTabId.isNotBlank() && finalCss.isNotBlank()) {
                    val del = delegate
                    if (del != null) {
                        val styleKey = "user_style_" + finalCss.hashCode()
                        val injectionJS = """
                            (function() {
                                if (document.getElementById('$styleKey')) return;
                                const style = document.createElement('style');
                                style.id = '$styleKey';
                                style.type = 'text/css';
                                style.innerHTML = ${org.json.JSONObject.quote(finalCss)};
                                (document.head || document.documentElement).appendChild(style);
                            })();
                        """.trimIndent()
                        del.executeScriptOnTab(resolvedTabId, injectionJS) { res ->
                            sendSuccessResponse(callbackId, JSONObject().put("status", "inserted"))
                        }
                    } else {
                        sendSuccessResponse(callbackId, JSONObject().put("status", "no_delegate"))
                    }
                } else {
                    sendSuccessResponse(callbackId, JSONObject().put("status", "invalid_arguments"))
                }
            }
            api == "scripting.removeCSS" -> {
                val spec = args.optJSONObject(0) ?: JSONObject()
                val target = spec.optJSONObject("target") ?: JSONObject()
                val tabIdRaw = target.optString("tabId", "")
                val resolvedTabId = TabIdMapper.getUuidFromString(tabIdRaw)
                val css = spec.optString("css", "")
                val files = spec.optJSONArray("files")

                val cssToInject = StringBuilder()
                if (css.isNotBlank()) {
                    cssToInject.append(css)
                } else if (files != null && files.length() > 0) {
                    val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
                    for (i in 0 until files.length()) {
                        val path = files.optString(i, "")
                        if (path.isNotBlank()) {
                            val cleanPath = path.removePrefix("./").removePrefix("/")
                            val file = java.io.File(extensionDir, cleanPath)
                            if (file.exists()) {
                                cssToInject.append(file.readText()).append("\n")
                            } else {
                                val fallbackFile = java.io.File(extensionDir, path.substringAfterLast("/"))
                                if (fallbackFile.exists()) {
                                    cssToInject.append(fallbackFile.readText()).append("\n")
                                }
                            }
                        }
                    }
                }

                val finalCss = cssToInject.toString()
                if (resolvedTabId.isNotBlank() && finalCss.isNotBlank()) {
                    val del = delegate
                    if (del != null) {
                        val styleKey = "user_style_" + finalCss.hashCode()
                        val removalJS = """
                            (function() {
                                const style = document.getElementById('$styleKey');
                                if (style) style.remove();
                            })();
                        """.trimIndent()
                        del.executeScriptOnTab(resolvedTabId, removalJS) { res ->
                            sendSuccessResponse(callbackId, JSONObject().put("status", "removed"))
                        }
                    } else {
                        sendSuccessResponse(callbackId, JSONObject().put("status", "no_delegate"))
                    }
                } else {
                    sendSuccessResponse(callbackId, JSONObject().put("status", "invalid_arguments"))
                }
            }
            api.startsWith("cookies.") -> {
                val cookieManager = android.webkit.CookieManager.getInstance()
                when {
                    api.endsWith(".get") -> {
                        val details = args.optJSONObject(0) ?: JSONObject()
                        val url = details.optString("url", "")
                        val keyName = details.optString("name", "")
                        if (url.isNotBlank()) {
                            val cookiesStr = cookieManager.getCookie(url) ?: ""
                            val cookiesMap = cookiesStr.split(";").associate {
                                val parts = it.split("=", limit = 2)
                                val k = parts.getOrNull(0)?.trim() ?: ""
                                val v = parts.getOrNull(1)?.trim() ?: ""
                                k to v
                            }
                            val cookieVal = cookiesMap[keyName]
                            if (cookieVal != null) {
                                val match = JSONObject().apply {
                                    put("name", keyName)
                                    put("value", cookieVal)
                                    put("domain", java.net.URL(url).host)
                                    put("path", "/")
                                }
                                sendSuccessResponse(callbackId, match)
                            } else {
                                sendErrorResponse(callbackId, "Cookie name '$keyName' not found for url: $url")
                            }
                        } else {
                            sendErrorResponse(callbackId, "Url parameter is required")
                        }
                    }
                    api.endsWith(".getAll") -> {
                        val details = args.optJSONObject(0) ?: JSONObject()
                        val url = details.optString("url", "")
                        if (url.isNotBlank()) {
                            val cookiesStr = cookieManager.getCookie(url) ?: ""
                            val list = JSONArray()
                            cookiesStr.split(";").forEach {
                                val parts = it.split("=", limit = 2)
                                val k = parts.getOrNull(0)?.trim() ?: ""
                                val v = parts.getOrNull(1)?.trim() ?: ""
                                if (k.isNotBlank()) {
                                    list.put(JSONObject().apply {
                                        put("name", k)
                                        put("value", v)
                                        put("domain", java.net.URL(url).host)
                                        put("path", "/")
                                    })
                                }
                            }
                            sendSuccessResponse(callbackId, list)
                        } else {
                            sendErrorResponse(callbackId, "Url parameter is required")
                        }
                    }
                    api.endsWith(".set") -> {
                        val details = args.optJSONObject(0) ?: JSONObject()
                        val url = details.optString("url", "")
                        val keyName = details.optString("name", "")
                        val value = details.optString("value", "")
                        if (url.isNotBlank() && keyName.isNotBlank()) {
                            cookieManager.setCookie(url, "$keyName=$value")
                            cookieManager.flush()
                            sendSuccessResponse(callbackId, JSONObject().apply {
                                put("name", keyName)
                                put("value", value)
                                put("status", "success")
                            })
                        } else {
                            sendErrorResponse(callbackId, "Url and name parameter is required")
                        }
                    }
                    api.endsWith(".remove") -> {
                        val details = args.optJSONObject(0) ?: JSONObject()
                        val url = details.optString("url", "")
                        val keyName = details.optString("name", "")
                        if (url.isNotBlank() && keyName.isNotBlank()) {
                            cookieManager.setCookie(url, "$keyName=; Max-Age=-99999999; expires=Thu, 01 Jan 1970 00:00:00 GMT")
                            cookieManager.flush()
                            sendSuccessResponse(callbackId, JSONObject().apply {
                                put("name", keyName)
                                put("status", "removed")
                            })
                        } else {
                            sendErrorResponse(callbackId, "Url and name parameter is required")
                        }
                    }
                    else -> sendErrorResponse(callbackId, "Unsupported cookies API")
                }
            }
            api.startsWith("alarms.") -> {
                when {
                    api.endsWith(".create") -> {
                        val name = args.optString(0, "default_alarm")
                        val alarmInfo = args.optJSONObject(1) ?: JSONObject()
                        val delayInMinutes = alarmInfo.optDouble("delayInMinutes", 1.0)
                        val periodInMinutes = alarmInfo.optDouble("periodInMinutes", 0.0)
                        
                        val delayMs = (delayInMinutes * 60 * 1000).toLong()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            eventManager.triggerEvent("alarms.onAlarm", JSONObject().apply {
                                put("name", name)
                                put("scheduledTime", System.currentTimeMillis() + delayMs)
                            })
                        }, delayMs)
                        
                        sendSuccessResponse(callbackId, JSONObject().put("status", "created"))
                    }
                    api.endsWith(".clear") -> {
                        sendSuccessResponse(callbackId, JSONObject().put("status", "cleared"))
                    }
                    else -> sendErrorResponse(callbackId, "Unsupported alarms API")
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
                com.example.extensionengine.ExtensionDebuggerEngine.instance.logError(
                    extensionId,
                    when (extensionId) {
                        "ext_grok_automation" -> "Grok Automation"
                        "ext_dark_reader" -> "Dark Reader"
                        "ext_adblock" -> "AdBlock Plus"
                        "ext_metamask" -> "MetaMask Wallet"
                        "ext_grok_4" -> "Grok 4.0 AI"
                        "ext_cookies" -> "I don't care about cookies"
                        "ext_auto_translate" -> "Auto-Translate Extension"
                        else -> "Extension '$extensionId'"
                    },
                    com.example.extensionengine.DebugErrorType.PERMISSION,
                    "Calling unauthorized or unsupported API: $api"
                )
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
