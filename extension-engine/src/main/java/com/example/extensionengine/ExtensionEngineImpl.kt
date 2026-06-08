package com.example.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExtensionEngineImpl(
    private val context: Context,
    private val delegate: BrowserDelegate?
) : ExtensionEngine {

    val database = ExtensionDatabase.getInstance(context)
    val registry = ExtensionRegistry()
    val parser = ManifestParser()
    val loader = ExtensionLoader(context, parser, database)
    val permissionManager = PermissionManager()
    val storageManager = StorageManager(database)
    val messageBus = MessageBus()
    val eventManager = EventManager(messageBus)
    val popupManager = PopupManager()
    val updateManager = UpdateManager()
    val scriptInjector = ScriptInjector()
    val cssInjector = CssInjector()
    val contentScriptManager = ContentScriptManager(context, permissionManager, scriptInjector, cssInjector)
    val backgroundScriptManager = BackgroundScriptManager(context, scriptInjector, messageBus)

    val portManager = PortManager(messageBus)
    val activeTabManager = ActiveTabManager(delegate)
    val tabMessenger = TabMessenger(messageBus)
    val tabBridge = TabBridge(tabMessenger, activeTabManager, portManager)
    val pageBridge = PageBridge()
    val domBridge = DomBridge()
    val postMessageBridge = PostMessageBridge()

    private val runtimeMap = mutableMapOf<String, ExtensionRuntime>()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    init {
        // Initialize bootstrap provider for intercepting HTML page requests
        ExtensionDirectoryResolver.bootstrapProvider = { id ->
            compileBootstrapScript(id)
        }

        // Initialize bridge provider inside background scripts
        backgroundScriptManager.setRuntimeBridgeProvider { webView ->
            createBridge(webView)
        }
        
        // Load installed active extensions
        ioScope.launch {
            try {
                val dbList = database.extensionDao().getAllExtensions()
                withContext(Dispatchers.Main) {
                    for (entity in dbList) {
                        try {
                            val parsed = loader.loadFromDatabase(entity)
                            registry.register(parsed)
                            ExtensionDirectoryResolver.cacheIdAndName(parsed.id, parsed.name)
                            
                            if (entity.enabledState) {
                                val runtime = ExtensionRuntime(
                                    parsed, context, backgroundScriptManager,
                                    contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
                                )
                                runtimeMap[parsed.id] = runtime
                                runtime.start()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Binds a WebView with the extension bridge, allowing injection of API support.
     */
    fun setupWebView(webView: WebView, tabId: String? = null) {
        val action = {
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                
                val bridge = createBridge(webView, tabId)
                webView.tag = bridge
                webView.addJavascriptInterface(bridge, "OrionExtensionBridge")
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            webView.post(action)
        }
    }

    /**
     * Injects matching scripts on page reload / transition completion.
     */
    fun injectContentScripts(webView: WebView, url: String) {
        if (url.startsWith("orion://") || url == "about:blank" || url.startsWith("file://")) return

        val evaluator = object : ScriptEvaluator {
            override fun evaluateJavascript(code: String, callback: ((String?) -> Unit)?) {
                webView.evaluateJavascript(code) { res -> callback?.invoke(res) }
            }
            override fun post(action: () -> Unit) {
                webView.post(action)
            }
        }

        webView.post {
            try {
                // Compile API bootstraps for all active extensions
                val activeList = registry.getAllActiveExtensions()
                for (ext in activeList) {
                    val isEnabled = runtimeMap[ext.id]?.isActive ?: false
                    val matchesUrl = permissionManager.hasHostPermission(ext.hostPermissions, ext.permissions, url) ||
                            ext.contentScripts.any { spec -> permissionManager.hasHostPermission(spec.matches, emptyList(), url) }
                    if (isEnabled && matchesUrl) {
                        val boot = compileBootstrapScript(ext.id)
                        webView.evaluateJavascript(boot, null)
                    }
                }
                
                // Inject matched content scripts
                contentScriptManager.matchAndInject(evaluator, url, activeList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun installExtension(uri: Uri): ParsedExtension {
        val parsed = loader.loadAndInstallFromZip(uri)
        registry.register(parsed)
        ExtensionDirectoryResolver.cacheIdAndName(parsed.id, parsed.name)
        
        withContext(Dispatchers.Main) {
            val runtime = ExtensionRuntime(
                parsed, context, backgroundScriptManager,
                contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
            )
            runtimeMap[parsed.id] = runtime
            runtime.start()
        }
        return parsed
    }

    override suspend fun uninstallExtension(id: String) {
        withContext(Dispatchers.Main) {
            runtimeMap.remove(id)?.stop()
            backgroundScriptManager.stopBackgroundWorker(id)
        }
        registry.unregister(id)
        database.extensionDao().deleteExtensionById(id)
    }

    override suspend fun toggleExtension(id: String, enabled: Boolean) {
        database.extensionDao().updateEnabledState(id, enabled)
        
        withContext(Dispatchers.Main) {
            val runtime = runtimeMap[id]
            if (enabled) {
                if (runtime != null) {
                    runtime.start()
                } else {
                    val parsed = registry.getExtension(id)
                    if (parsed != null) {
                        val newRuntime = ExtensionRuntime(
                            parsed, context, backgroundScriptManager,
                            contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
                        )
                        runtimeMap[parsed.id] = newRuntime
                        newRuntime.start()
                    }
                }
            } else {
                runtime?.stop()
            }
        }
    }

    override fun shutdown() {
        backgroundScriptManager.stopAll()
    }

    fun createBridge(webView: WebView?, tabId: String? = null): RuntimeBridge {
        return RuntimeBridge(
            context, webView, storageManager, messageBus, delegate, eventManager, tabId, portManager, tabBridge
        )
    }

    fun compileBootstrapScript(extensionId: String): String {
        val ext = registry.getExtension(extensionId)
        val manifestJsonSafe = ext?.manifestJson ?: "{}"
        val quotedManifest = org.json.JSONObject.quote(manifestJsonSafe)

        return """
            (function() {
                const extId = "$extensionId";
                
                window._orionManifests = window._orionManifests || {};
                window._orionManifests[extId] = $quotedManifest;

                // Bind modern diagnostic listeners for uncaught errors and promise rejections
                window.addEventListener("error", function(e) {
                    console.error("[UNCAUGHT_ERROR] " + e.message + " (at " + e.filename + ":" + e.lineno + ":" + e.colno + ")");
                });
                window.addEventListener("unhandledrejection", function(e) {
                    console.error("[UNHANDLED_REJECTION] " + (e.reason ? (e.reason.message || e.reason) : "Unknown Promise Rejection"));
                });

                window._extCallbacks = window._extCallbacks || {};
                window._extResponse = function(callbackId, error, result) {
                    const cb = window._extCallbacks[callbackId];
                    if (cb) {
                        delete window._extCallbacks[callbackId];
                        cb(result);
                    }
                };

                window._orionExtensionEvents = window._orionExtensionEvents || {};
                window._orionDispatchEvent = function(extId, eventName, data) {
                    const extEvents = window._orionExtensionEvents[extId] || {};
                    const listeners = extEvents[eventName] || [];
                    let eventArgs = [];
                    if (eventName === "tabs.onUpdated") {
                        const intTabId = data.tabId || 0;
                        const changeInfo = data.changeInfo || {};
                        const tab = data.tab || {};
                        eventArgs = [intTabId, changeInfo, tab];
                    } else if (eventName === "tabs.onActivated") {
                        eventArgs = [data.activeInfo || {}];
                    } else if (eventName === "webNavigation.onCompleted" || eventName === "webNavigation.onHistoryStateUpdated") {
                        eventArgs = [data.details || {}];
                    } else {
                        eventArgs = [data];
                    }
                    listeners.forEach(cb => {
                        try { cb.apply(null, eventArgs); } catch(e) { console.error("Event execution error for " + eventName, e); }
                    });
                };

                window._orionGetExtensionContext = window._orionGetExtensionContext || function(contextExtId) {
                    const bridgeCall = function(apiName, args) {
                        return new Promise((resolve, reject) => {
                            const cbId = "cb_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                            window._extCallbacks[cbId] = function(res) {
                                if (res && res.error) {
                                    reject(new Error(res.error));
                                } else {
                                    resolve(res);
                                }
                            };
                            try {
                                OrionExtensionBridge.postMessage(JSON.stringify({
                                    api: apiName,
                                    extensionId: contextExtId,
                                    args: args || []
                                }), cbId);
                            } catch (e) {
                                reject(e);
                            }
                        });
                    };

                    const createEvent = function(extId, eventName) {
                        window._orionExtensionEvents = window._orionExtensionEvents || {};
                        window._orionExtensionEvents[extId] = window._orionExtensionEvents[extId] || {};
                        window._orionExtensionEvents[extId][eventName] = window._orionExtensionEvents[extId][eventName] || [];
                        bridgeCall("event.addListener", [eventName]);
                        return {
                            addListener: function(cb) {
                                if (typeof cb === "function") {
                                    window._orionExtensionEvents[extId][eventName].push(cb);
                                }
                            },
                            removeListener: function(cb) {
                                if (window._orionExtensionEvents[extId][eventName]) {
                                    window._orionExtensionEvents[extId][eventName] = 
                                        window._orionExtensionEvents[extId][eventName].filter(l => l !== cb);
                                }
                                bridgeCall("event.removeListener", [eventName]);
                            }
                        };
                    };

                    window._orionExtensionListeners = window._orionExtensionListeners || {};
                    window._orionExtensionListeners[contextExtId] = window._orionExtensionListeners[contextExtId] || {
                        onMessage: [],
                        onConnect: []
                    };

                    return {
                        runtime: {
                            id: contextExtId,
                            getURL: function(path) {
                                if (!path) return "chrome-extension://" + contextExtId + "/";
                                if (path.startsWith("/")) path = path.substring(1);
                                return "chrome-extension://" + contextExtId + "/" + path;
                            },
                            getManifest: function() {
                                const str = window._orionManifests[contextExtId] || "{}";
                                try { return JSON.parse(str); } catch(e) { return {}; }
                            },
                            onInstalled: {
                                listeners: [],
                                addListener: function(cb) {
                                    setTimeout(() => { try { cb({ reason: "install" }); } catch(e) {} }, 50);
                                },
                                removeListener: function(cb) {}
                            },
                            onMessage: {
                                addListener: function(cb) {
                                    window._orionExtensionListeners[contextExtId].onMessage.push(cb);
                                },
                                removeListener: function(cb) {
                                    window._orionExtensionListeners[contextExtId].onMessage = 
                                        window._orionExtensionListeners[contextExtId].onMessage.filter(l => l !== cb);
                                }
                            },
                            onConnect: {
                                addListener: function(cb) {
                                    window._orionExtensionListeners[contextExtId].onConnect.push(cb);
                                },
                                removeListener: function(cb) {
                                    window._orionExtensionListeners[contextExtId].onConnect = 
                                        window._orionExtensionListeners[contextExtId].onConnect.filter(l => l !== cb);
                                }
                            },
                            sendMessage: function() {
                                let targetExtensionId = contextExtId;
                                let message = arguments[0];
                                let options = arguments[1];
                                let responseCallback = arguments[2];
                                
                                if (typeof arguments[0] === "string") {
                                    targetExtensionId = arguments[0];
                                    message = arguments[1];
                                    options = arguments[2];
                                    responseCallback = arguments[3];
                                }
                                
                                if (typeof options === "function") {
                                    responseCallback = options;
                                    options = null;
                                }
                                if (typeof message === "function") {
                                    responseCallback = message;
                                    message = arguments[0];
                                }
                                
                                const p = bridgeCall("runtime.sendMessage", [message]);
                                if (responseCallback) p.then(responseCallback);
                                return p;
                            },
                            connect: function() {
                                let targetExtensionId = contextExtId;
                                let connectInfo = null;
                                
                                if (arguments.length === 1) {
                                    if (typeof arguments[0] === "string") {
                                        targetExtensionId = arguments[0];
                                    } else if (typeof arguments[0] === "object") {
                                        connectInfo = arguments[0];
                                    }
                                } else if (arguments.length >= 2) {
                                    targetExtensionId = arguments[0];
                                    connectInfo = arguments[1];
                                }
                                
                                const portName = (connectInfo && connectInfo.name) || "";
                                const channelId = "port_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                                
                                const port = {
                                    name: portName,
                                    disconnect: function() {
                                        bridgeCall("runtime.portDisconnect", [channelId]);
                                    },
                                    onDisconnect: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    onMessage: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    postMessage: function(msg) {
                                        bridgeCall("runtime.portPostMessage", [channelId, msg]);
                                    }
                                };
                                window._ports = window._ports || {};
                                window._ports[channelId] = port;
                                bridgeCall("runtime.portConnect", [targetExtensionId, channelId, portName]);
                                return port;
                            }
                        },
                        storage: {
                            local: {
                                get: function(keys, callback) {
                                    let resolvedKeys = keys;
                                    let resolvedCallback = callback;
                                    if (typeof keys === "function") {
                                        resolvedCallback = keys;
                                        resolvedKeys = null;
                                    }
                                    const p = bridgeCall("storage.get", ["local", resolvedKeys]);
                                    if (resolvedCallback) p.then(resolvedCallback);
                                    return p;
                                },
                                set: function(items, callback) {
                                    let resolvedCallback = callback;
                                    if (typeof items === "function") {
                                        resolvedCallback = items;
                                    }
                                    const p = bridgeCall("storage.set", ["local", items]);
                                    if (resolvedCallback) p.then(resolvedCallback);
                                    return p;
                                },
                                remove: function(keys, callback) {
                                    let resolvedKeys = keys;
                                    let resolvedCallback = callback;
                                    if (typeof keys === "function") {
                                        resolvedCallback = keys;
                                        resolvedKeys = null;
                                    }
                                    const p = bridgeCall("storage.remove", ["local", resolvedKeys]);
                                    if (resolvedCallback) p.then(resolvedCallback);
                                    return p;
                                },
                                clear: function(callback) {
                                    const p = bridgeCall("storage.clear", ["local"]);
                                    if (callback) p.then(callback);
                                    return p;
                                }
                            },
                            sync: {
                                get: function(keys, callback) {
                                    let resolvedKeys = keys;
                                    let resolvedCallback = callback;
                                    if (typeof keys === "function") {
                                        resolvedCallback = keys;
                                        resolvedKeys = null;
                                    }
                                    const p = bridgeCall("storage.get", ["local", resolvedKeys]);
                                    if (resolvedCallback) p.then(resolvedCallback);
                                    return p;
                                },
                                set: function(items, callback) {
                                    let resolvedCallback = callback;
                                    if (typeof items === "function") {
                                        resolvedCallback = items;
                                    }
                                    const p = bridgeCall("storage.set", ["local", items]);
                                    if (resolvedCallback) p.then(resolvedCallback);
                                    return p;
                                },
                                remove: function(keys, callback) {
                                    let resolvedKeys = keys;
                                    let resolvedCallback = callback;
                                    if (typeof keys === "function") {
                                        resolvedCallback = keys;
                                        resolvedKeys = null;
                                    }
                                    const p = bridgeCall("storage.remove", ["local", resolvedKeys]);
                                    if (resolvedCallback) p.then(resolvedCallback);
                                    return p;
                                },
                                clear: function(callback) {
                                    const p = bridgeCall("storage.clear", ["local"]);
                                    if (callback) p.then(callback);
                                    return p;
                                }
                            }
                        },
                        tabs: {
                            onUpdated: createEvent(contextExtId, "tabs.onUpdated"),
                            onActivated: createEvent(contextExtId, "tabs.onActivated"),
                            query: function(queryInfo, callback) {
                                const p = bridgeCall("tabs.query", [queryInfo]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            create: function(createProperties, callback) {
                                const p = bridgeCall("tabs.create", [createProperties]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            remove: function(tabId, callback) {
                                const p = bridgeCall("tabs.remove", [tabId]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            reload: function(tabId, callback) {
                                const p = bridgeCall("tabs.reload", [tabId]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            update: function(tabId, updateProps, callback) {
                                const p = bridgeCall("tabs.update", [tabId, updateProps]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            sendMessage: function(tabId, message, options, callback) {
                                const finalCb = (typeof options === "function") ? options : callback;
                                const p = bridgeCall("tabs.sendMessage", [tabId.toString(), message]);
                                if (finalCb) p.then(finalCb);
                                return p;
                            },
                            connect: function(tabId, connectInfo) {
                                const portName = (connectInfo && connectInfo.name) || "";
                                const channelId = "port_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                                
                                const port = {
                                    name: portName,
                                    disconnect: function() {
                                        bridgeCall("runtime.portDisconnect", [channelId]);
                                    },
                                    onDisconnect: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    onMessage: {
                                        listeners: [],
                                        addListener: function(cb) { this.listeners.push(cb); },
                                        removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                                    },
                                    postMessage: function(msg) {
                                        bridgeCall("runtime.portPostMessage", [channelId, msg]);
                                    }
                                };
                                window._ports = window._ports || {};
                                window._ports[channelId] = port;
                                bridgeCall("tabs.connect", [tabId.toString(), channelId, portName]);
                                return port;
                            }
                        },
                        notifications: {
                            create: function(id, options, callback) {
                                const p = bridgeCall("notifications.create", [id, options]);
                                if (callback) p.then(callback);
                                return p;
                            }
                        },
                        downloads: {
                            download: function(options, callback) {
                                const p = bridgeCall("downloads.download", [options]);
                                if (callback) p.then(callback);
                                return p;
                            }
                        },
                        webNavigation: {
                            onCompleted: createEvent(contextExtId, "webNavigation.onCompleted"),
                            onHistoryStateUpdated: createEvent(contextExtId, "webNavigation.onHistoryStateUpdated")
                        }
                    };
                };

                window.browser = window._orionGetExtensionContext(extId);
                window.chrome = window.browser;

                window._ports = window._ports || {};

                window._extPortMessage = function(channelId, msg) {
                    const port = window._ports[channelId];
                    if (port) {
                        port.onMessage.listeners.forEach(cb => {
                            try { cb(msg); } catch(e) {}
                        });
                    }
                };

                window._extPortDisconnect = function(channelId) {
                    const port = window._ports[channelId];
                    if (port) {
                        delete window._ports[channelId];
                        port.onDisconnect.listeners.forEach(cb => {
                            try { cb(); } catch(e) {}
                        });
                    }
                };

                window._extPortConnect = function(channelId, portName, senderId) {
                    const port = {
                        name: portName,
                        sender: { id: senderId },
                        disconnect: function() {
                            const bridgeCall = function(apiName, args) {
                                OrionExtensionBridge.postMessage(JSON.stringify({
                                    api: apiName,
                                    extensionId: extId,
                                    args: args || []
                                }), "dis_" + Date.now());
                            };
                            bridgeCall("runtime.portDisconnect", [channelId]);
                        },
                        onDisconnect: {
                            listeners: [],
                            addListener: function(cb) { this.listeners.push(cb); },
                            removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                        },
                        onMessage: {
                            listeners: [],
                            addListener: function(cb) { this.listeners.push(cb); },
                            removeListener: function(cb) { this.listeners = this.listeners.filter(l => l !== cb); }
                        },
                        postMessage: function(msg) {
                            const bridgeCall = function(apiName, args) {
                                OrionExtensionBridge.postMessage(JSON.stringify({
                                    api: apiName,
                                    extensionId: extId,
                                    args: args || []
                                }), "msg_" + Date.now());
                            };
                            bridgeCall("runtime.portPostMessage", [channelId, msg]);
                        }
                    };
                    window._ports[channelId] = port;
                    
                    window._orionExtensionListeners = window._orionExtensionListeners || {};
                    for (let targetId in window._orionExtensionListeners) {
                        const recs = window._orionExtensionListeners[targetId].onConnect;
                        if (recs) {
                            recs.forEach(cb => {
                                try { cb(port); } catch(e) {}
                            });
                        }
                    }
                };

                window._extOnMessage = function(targetExtId, message, sender, callbackId) {
                    if (message && message.type === "EVENT_DISPATCH") {
                        const eventName = message.eventName;
                        const data = message.data || {};
                        window._orionDispatchEvent(targetExtId, eventName, data);
                        return;
                    }
                    window._orionExtensionListeners = window._orionExtensionListeners || {};
                    if (window._orionExtensionListeners[targetExtId]) {
                        const sendResponse = function(resp) {
                            if (callbackId) {
                                try {
                                    OrionExtensionBridge.postMessage(JSON.stringify({
                                        api: "runtime.response",
                                        extensionId: targetExtId,
                                        args: [callbackId, resp]
                                    }), "resp_" + Date.now());
                                } catch(e) {
                                    console.error("sendResponse error: ", e);
                                }
                            }
                        };
                        window._orionExtensionListeners[targetExtId].onMessage.forEach(cb => {
                            try { cb(message, sender, sendResponse); } catch(e) {}
                        });
                    }
                };
            })();
        """.trimIndent()
    }
}
