package com.example.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import java.io.File
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
    val permissionManager = PermissionManager(context)
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
        // Track storage changes and dispatch storage.onChanged event down through EventManager
        storageManager.changeListener = { extensionId, area, changes ->
            val data = org.json.JSONObject().apply {
                put("changes", changes)
                put("areaName", area)
            }
            eventManager.triggerEvent("storage.onChanged", data)
        }

        // Initialize bootstrap provider for intercepting HTML page requests
        ExtensionDirectoryResolver.bootstrapProvider = { id ->
            compileBootstrapScript(id)
        }

        // Initialize bridge provider inside background scripts
        backgroundScriptManager.setRuntimeBridgeProvider { webView ->
            createBridge(webView)
        }
        
        // Load installed active extensions asynchronously with a start delay to keep startup instantaneous
        ioScope.launch {
            try {
                // Defer extension boots by 2.0 seconds to guarantee smooth, Chrome-level instant starts
                kotlinx.coroutines.delay(2000L)
                val dbList = database.extensionDao().getAllExtensions()
                
                // Process database & manifest parsing off the main thread (on Dispatchers.IO)
                val preparedRuntimes = mutableListOf<Pair<ParsedExtension, ExtensionRuntime?>>()
                for (entity in dbList) {
                    try {
                        val parsed = loader.loadFromDatabase(entity)
                        var runtime: ExtensionRuntime? = null
                        if (entity.enabledState) {
                            runtime = ExtensionRuntime(
                                parsed, context, backgroundScriptManager,
                                contentScriptManager, popupManager, compileBootstrapScript(parsed.id)
                            )
                        }
                        preparedRuntimes.add(Pair(parsed, runtime))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Register and start active extensions on Main thread cleanly
                withContext(Dispatchers.Main) {
                    for (pair in preparedRuntimes) {
                        val parsed = pair.first
                        val runtime = pair.second
                        try {
                            registry.register(parsed)
                            ExtensionDirectoryResolver.cacheIdAndName(parsed.id, parsed.name)
                            if (runtime != null) {
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
    fun injectContentScripts(webView: WebView, url: String, runAt: String? = null) {
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
                val activeList = registry.getAllActiveExtensions()
                val enabledList = activeList.filter { ext ->
                    val isEnabled = runtimeMap[ext.id]?.isActive ?: false
                    isEnabled
                }
                
                // ContentScriptManager handles evaluating and choosing content scripts matching the specified phase
                contentScriptManager.matchAndInject(
                    evaluator = evaluator,
                    url = url,
                    parsedExtensions = enabledList,
                    runAtFilter = runAt ?: "document_idle",
                    bootstrapScriptProvider = { extId -> compileBootstrapScript(extId) }
                )
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
            context, webView, storageManager, messageBus, delegate, eventManager, tabId, portManager, tabBridge, registry, permissionManager
        )
    }

    private fun loadExtensionMessagesJson(extensionId: String): String {
        try {
            val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, extensionId)
            val localesDir = File(extensionDir, "_locales")
            if (!localesDir.exists() || !localesDir.isDirectory) {
                return "{}"
            }

            val currentLocale = java.util.Locale.getDefault()
            val langCode = currentLocale.language
            val country = currentLocale.country
            val fullCode = if (country.isNotBlank()) "${langCode}_$country" else langCode

            val candidateDirs = listOf(
                fullCode,
                fullCode.replace("_", "-"),
                langCode,
                "en",
                "en-US",
                "en_US"
            )

            var messagesFile: File? = null
            for (cand in candidateDirs) {
                val f = File(localesDir, cand + "/messages.json")
                if (f.exists() && f.isFile) {
                    messagesFile = f
                    break
                }
            }

            if (messagesFile == null) {
                val subfolders = localesDir.listFiles { f -> f.isDirectory }
                if (subfolders != null) {
                    for (sub in subfolders) {
                        val f = File(sub, "messages.json")
                        if (f.exists() && f.isFile) {
                            messagesFile = f
                            break
                        }
                    }
                }
            }

            if (messagesFile != null && messagesFile.exists()) {
                val fileContent = messagesFile.readText()
                // Validate it's correct JSON
                val testObj = org.json.JSONObject(fileContent)
                return testObj.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "{}"
    }

    fun compileBootstrapScript(extensionId: String): String {
        val ext = registry.getExtension(extensionId)
        val manifestJsonSafe = ext?.manifestJson ?: "{}"
        val quotedManifest = org.json.JSONObject.quote(manifestJsonSafe)
        val messagesJsonSafe = loadExtensionMessagesJson(extensionId)
        val quotedMessages = org.json.JSONObject.quote(messagesJsonSafe)

        val domUtils = domBridge.compileDomUtilities()
        val postMessageUtils = PostMessageBridge().compilePostMessageScript()

        return """
            (function() {
                const extId = "$extensionId";
                
                // Inject DOM and PostMessage Utility Helpers
                $domUtils
                $postMessageUtils
                
                window._orionManifests = window._orionManifests || {};
                window._orionManifests[extId] = $quotedManifest;

                window._orionExtensionMessages = window._orionExtensionMessages || {};
                try {
                    window._orionExtensionMessages[extId] = JSON.parse($quotedMessages);
                } catch(e) {
                    window._orionExtensionMessages[extId] = {};
                }

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
                    } else if (eventName === "storage.onChanged") {
                        const changes = data.changes || {};
                        const areaName = data.areaName || "local";
                        eventArgs = [changes, areaName];
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
                        return new Promise((resolve) => {
                            const cbId = "cb_" + Math.random().toString(36).substring(2, 9) + "_" + Date.now();
                            
                            // Safety timeout (2.0 seconds) to prevent any popup script from freezing the UI
                            const timeoutId = setTimeout(() => {
                                if (window._extCallbacks[cbId]) {
                                    delete window._extCallbacks[cbId];
                                    console.warn("[BRIDGE_TIMEOUT] Api '" + apiName + "' timed out after 2000ms. Returning fallback empty response.");
                                    resolve(null);
                                }
                            }, 2000);

                            window._extCallbacks[cbId] = function(res) {
                                clearTimeout(timeoutId);
                                delete window._extCallbacks[cbId];
                                if (res && res.error) {
                                    console.warn("[BRIDGE_WARN] Api '" + apiName + "' returned error: " + res.error + ". Gracefully resolving with null.");
                                    resolve(null);
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
                                clearTimeout(timeoutId);
                                console.error("[BRIDGE_ERROR] Error posting message for '" + apiName + "':", e);
                                resolve(null);
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

                    const dummyMethod = function(name) {
                        return function() {
                            console.log("[MOCK_API] Called: " + name, arguments);
                            const lastArg = arguments[arguments.length - 1];
                            if (typeof lastArg === "function") {
                                setTimeout(() => { try { lastArg(); } catch(e){} }, 5);
                            }
                            return Promise.resolve();
                        };
                    };

                    const actionMock = {
                        setTitle: dummyMethod("action.setTitle"),
                        getTitle: dummyMethod("action.getTitle"),
                        setIcon: dummyMethod("action.setIcon"),
                        setPopup: dummyMethod("action.setPopup"),
                        getPopup: dummyMethod("action.getPopup"),
                        setBadgeText: dummyMethod("action.setBadgeText"),
                        getBadgeText: dummyMethod("action.getBadgeText"),
                        setBadgeBackgroundColor: dummyMethod("action.setBadgeBackgroundColor"),
                        getBadgeBackgroundColor: dummyMethod("action.getBadgeBackgroundColor"),
                        enable: dummyMethod("action.enable"),
                        disable: dummyMethod("action.disable"),
                        onClicked: { addListener: function(cb){}, removeListener: function(cb){} }
                    };

                    return {
                        action: actionMock,
                        browserAction: actionMock,
                        pageAction: actionMock,
                        contextMenus: {
                            create: dummyMethod("contextMenus.create"),
                            update: dummyMethod("contextMenus.update"),
                            remove: dummyMethod("contextMenus.remove"),
                            removeAll: dummyMethod("contextMenus.removeAll"),
                            onClicked: { addListener: function(cb){}, removeListener: function(cb){} }
                        },
                        runtime: {
                            id: contextExtId,
                            lastError: null,
                            getPlatformInfo: function(callback) {
                                const info = { os: "android", arch: "arm", nacl_arch: "arm" };
                                if (callback) callback(info);
                                return Promise.resolve(info);
                            },
                            openOptionsPage: function(callback) {
                                const manifest = window.browser.runtime.getManifest();
                                const optionsPage = manifest.options_ui ? (manifest.options_ui.page || "") : (manifest.options_page || "options.html");
                                const url = window.browser.runtime.getURL(optionsPage);
                                const p = window.browser.tabs.create({ url: url });
                                if (callback) p.then(callback);
                                return p;
                            },
                            reload: function() {
                                try { OrionExtensionBridge.postMessage(JSON.stringify({ api: "runtime.reload", extensionId: contextExtId, args: [] }), "reload_" + Date.now()); } catch(e){}
                            },
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
                            onChanged: createEvent(contextExtId, "storage.onChanged"),
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
                            },
                            session: {
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
                            managed: {
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
                            get: function(tabId, callback) {
                                const p = bridgeCall("tabs.get", [tabId]);
                                if (callback) p.then(callback);
                                return p;
                            },
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
                        scripting: {
                            executeScript: function(spec, callback) {
                                if (spec && spec.func && typeof spec.func === "function") {
                                    spec.func = spec.func.toString();
                                }
                                const p = bridgeCall("scripting.executeScript", [spec]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            insertCSS: function(spec, callback) {
                                const p = bridgeCall("scripting.insertCSS", [spec]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            removeCSS: function(spec, callback) {
                                const p = bridgeCall("scripting.removeCSS", [spec]);
                                if (callback) p.then(callback);
                                return p;
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
                            onHistoryStateUpdated: createEvent(contextExtId, "webNavigation.onHistoryStateUpdated"),
                            onBeforeNavigate: createEvent(contextExtId, "webNavigation.onBeforeNavigate"),
                            onCommitted: createEvent(contextExtId, "webNavigation.onCommitted"),
                            onDOMContentLoaded: createEvent(contextExtId, "webNavigation.onDOMContentLoaded"),
                            onErrorOccurred: createEvent(contextExtId, "webNavigation.onErrorOccurred"),
                            onCreatedNavigationTarget: createEvent(contextExtId, "webNavigation.onCreatedNavigationTarget"),
                            onReferenceFragmentUpdated: createEvent(contextExtId, "webNavigation.onReferenceFragmentUpdated"),
                            onTabReplaced: createEvent(contextExtId, "webNavigation.onTabReplaced")
                        },
                        i18n: {
                            getMessage: function(messageName, substitutions) {
                                const msgs = window._orionExtensionMessages[contextExtId] || {};
                                const item = msgs[messageName] || msgs[messageName.toLowerCase()];
                                if (!item) return messageName;
                                let msg = item.message || "";
                                if (!msg) return messageName;
                                
                                if (substitutions) {
                                    if (!Array.isArray(substitutions)) {
                                        substitutions = [substitutions];
                                    }
                                    substitutions.forEach((sub, i) => {
                                        const phIndex = i + 1;
                                        msg = msg.replace(new RegExp("\\$" + phIndex, "g"), String(sub));
                                    });
                                }
                                
                                if (item.placeholders) {
                                    for (const phName in item.placeholders) {
                                        const phObj = item.placeholders[phName];
                                        const content = phObj.content || "";
                                        let resolvedContent = content;
                                        if (substitutions) {
                                            substitutions.forEach((sub, i) => {
                                                const phIndex = i + 1;
                                                resolvedContent = resolvedContent.replace(new RegExp("\\$" + phIndex, "g"), String(sub));
                                            });
                                        }
                                        msg = msg.split("$" + phName + "$").join(resolvedContent);
                                        msg = msg.split("$" + phName.toLowerCase() + "$").join(resolvedContent);
                                    }
                                }
                                return msg;
                            },
                            getAcceptLanguages: function(callback) {
                                const langs = [navigator.language || "en-US"];
                                if (callback) callback(langs);
                                return Promise.resolve(langs);
                            },
                            getUILanguage: function() {
                                return navigator.language || "en-US";
                            },
                            detectLanguage: function(text, callback) {
                                const res = { isReliable: true, languages: [{ language: "en", percentage: 100 }] };
                                if (callback) callback(res);
                                return Promise.resolve(res);
                            }
                        },
                        extension: {
                            getURL: function(path) {
                                return window.browser.runtime.getURL(path);
                            },
                            getBackgroundPage: function() {
                                return window;
                            },
                            getViews: function(fetchProperties) {
                                return [window];
                            },
                            isAllowedIncognitoAccess: function(callback) {
                                if (callback) callback(false);
                                return Promise.resolve(false);
                            },
                            isAllowedFileSchemeAccess: function(callback) {
                                if (callback) callback(true);
                                return Promise.resolve(true);
                            },
                            sendMessage: function(msg, cb) {
                                return window.browser.runtime.sendMessage(msg, cb);
                            },
                            connect: function(info) {
                                return window.browser.runtime.connect(info);
                            },
                            inIncognitoContext: false
                        },
                        action: {
                            setIcon: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setTitle: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setPopup: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setBadgeText: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setBadgeBackgroundColor: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            getBadgeText: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            getBadgeBackgroundColor: function(details, callback) { if (callback) callback([0,0,0,0]); return Promise.resolve([0,0,0,0]); },
                            getTitle: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            getPopup: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            enable: function(tabId, callback) { if (callback) callback(); return Promise.resolve(); },
                            disable: function(tabId, callback) { if (callback) callback(); return Promise.resolve(); },
                            onClicked: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        browserAction: {
                            setIcon: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setTitle: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setPopup: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setBadgeText: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setBadgeBackgroundColor: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            getBadgeText: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            getBadgeBackgroundColor: function(details, callback) { if (callback) callback([0,0,0,0]); return Promise.resolve([0,0,0,0]); },
                            getTitle: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            getPopup: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            enable: function(tabId, callback) { if (callback) callback(); return Promise.resolve(); },
                            disable: function(tabId, callback) { if (callback) callback(); return Promise.resolve(); },
                            onClicked: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        pageAction: {
                            setIcon: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setTitle: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            setPopup: function(details, callback) { if (callback) callback(); return Promise.resolve(); },
                            getPopup: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            getTitle: function(details, callback) { if (callback) callback(""); return Promise.resolve(""); },
                            enable: function(tabId, callback) { if (callback) callback(); return Promise.resolve(); },
                            disable: function(tabId, callback) { if (callback) callback(); return Promise.resolve(); },
                            onClicked: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        cookies: {
                            get: function(details, callback) {
                                const p = bridgeCall("cookies.get", [details]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            getAll: function(details, callback) {
                                const p = bridgeCall("cookies.getAll", [details]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            set: function(details, callback) {
                                const p = bridgeCall("cookies.set", [details]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            remove: function(details, callback) {
                                const p = bridgeCall("cookies.remove", [details]);
                                if (callback) p.then(callback);
                                return p;
                            },
                            onChanged: {
                                addListener: function(cb) {},
                                removeListener: function(cb) {}
                            }
                        },
                        windows: {
                            getCurrent: function(callback) {
                                const win = { id: 1, focused: true, type: "normal", state: "normal" };
                                if (callback) callback(win);
                                return Promise.resolve(win);
                            },
                            getLastFocused: function(callback) {
                                const win = { id: 1, focused: true, type: "normal", state: "normal" };
                                if (callback) callback(win);
                                return Promise.resolve(win);
                            },
                            getAll: function(callback) {
                                const list = [{ id: 1, focused: true, type: "normal", state: "normal" }];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            }
                        },
                        webRequest: {
                            onBeforeRequest: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onBeforeSendHeaders: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onSendHeaders: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onHeadersReceived: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onAuthRequired: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onResponseStarted: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onBeforeRedirect: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onCompleted: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onErrorOccurred: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        contextMenus: {
                            create: function(details, callback) {
                                if (callback) callback();
                                return "menu_" + Math.random().toString(36).substring(2, 9);
                            },
                            update: function(id, details, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            remove: function(id, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            removeAll: function(callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            onClicked: {
                                addListener: function(cb) {},
                                removeListener: function(cb) {}
                            }
                        },
                        history: {
                            search: function(query, callback) {
                                if (callback) callback([]);
                                return Promise.resolve([]);
                            },
                            addUrl: function(details, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            deleteUrl: function(details, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            deleteRange: function(range, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            deleteAll: function(callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            onVisited: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onVisitRemoved: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        bookmarks: {
                            get: function(idOrIdList, callback) {
                                const list = [];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            getChildren: function(id, callback) {
                                const list = [];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            getRecent: function(numberOfItems, callback) {
                                const list = [];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            getTree: function(callback) {
                                const list = [];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            getSubTree: function(id, callback) {
                                const list = [];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            search: function(query, callback) {
                                const list = [];
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            create: function(bookmark, callback) {
                                const item = { id: "1", title: bookmark.title || "", url: bookmark.url || "" };
                                if (callback) callback(item);
                                return Promise.resolve(item);
                            },
                            move: function(id, destination, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            update: function(id, changes, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            remove: function(id, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            removeTree: function(id, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            onCreated: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onRemoved: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onChanged: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onMoved: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onChildrenReordered: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onImportStarted: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onImportEnded: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        idle: {
                            queryState: function(detectionIntervalInSeconds, callback) {
                                if (callback) callback("active");
                                return Promise.resolve("active");
                            },
                            setDetectionInterval: function(intervalInSeconds) {},
                            onStateChanged: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        management: {
                            get: function(id, callback) {
                                const details = { id: id, name: "Extension", enabled: true };
                                if (callback) callback(details);
                                return Promise.resolve(details);
                            },
                            getSelf: function(callback) {
                                const manifest = window.browser.runtime.getManifest();
                                const details = {
                                    id: contextExtId,
                                    name: manifest.name || "Extension",
                                    version: manifest.version || "1.0",
                                    enabled: true,
                                    type: "extension"
                                };
                                if (callback) callback(details);
                                return Promise.resolve(details);
                            },
                            getAll: function(callback) {
                                const list = [];
                                for (let id in window._orionManifests) {
                                    try {
                                        const m = JSON.parse(window._orionManifests[id]);
                                        list.push({ id: id, name: m.name || "Extension", enabled: true });
                                    } catch(e) {}
                                }
                                if (callback) callback(list);
                                return Promise.resolve(list);
                            },
                            setEnabled: function(id, enabled, callback) {
                                if (callback) callback();
                                return Promise.resolve();
                            },
                            onInstalled: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onUninstalled: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onEnabled: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onDisabled: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        declarativeContent: {
                            onPageChanged: {
                                addRules: function(rules, callback) { if (callback) callback(rules); return Promise.resolve(rules); },
                                removeRules: function(rules, callback) { if (callback) callback(); return Promise.resolve(); },
                                getRules: function(ruleIds, callback) { if (callback) callback([]); return Promise.resolve([]); },
                                addListener: function(cb) {},
                                removeListener: function(cb) {}
                            },
                            ShowPageAction: function() { return {}; },
                            PageStateMatcher: function(props) { return props || {}; }
                        },
                        permissions: {
                            contains: function(permissions, callback) {
                                const res = { result: true };
                                if (callback) callback(res);
                                return Promise.resolve(res);
                            },
                            request: function(permissions, callback) {
                                const res = { result: true };
                                if (callback) callback(res);
                                return Promise.resolve(res);
                            },
                            remove: function(permissions, callback) {
                                const res = { result: true };
                                if (callback) callback(res);
                                return Promise.resolve(res);
                            },
                            getAll: function(callback) {
                                const manifest = window._orionGetExtensionContext(contextExtId).runtime.getManifest();
                                const res = {
                                    permissions: manifest.permissions || [],
                                    origins: manifest.host_permissions || []
                                };
                                if (callback) callback(res);
                                return Promise.resolve(res);
                            }
                        },
                        commands: {
                            getAll: function(callback) {
                                if (callback) callback([]);
                                return Promise.resolve([]);
                            },
                            onCommand: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        omnibox: {
                            setDefaultSuggestion: function(suggestion) {},
                            onInputStarted: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onInputChanged: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onInputEntered: { addListener: function(cb) {}, removeListener: function(cb) {} },
                            onInputCancelled: { addListener: function(cb) {}, removeListener: function(cb) {} }
                        },
                        system: {
                            cpu: {
                                getInfo: function(callback) {
                                    const info = { numOfProcessors: 4, processors: [], modelName: "ARM Processor" };
                                    if (callback) callback(info);
                                    return Promise.resolve(info);
                                }
                            },
                            memory: {
                                getInfo: function(callback) {
                                    const info = { capacity: 4194304000, availableCapacity: 2097152000 };
                                    if (callback) callback(info);
                                    return Promise.resolve(info);
                                }
                            },
                            storage: {
                                getInfo: function(callback) {
                                    const info = [{ id: "main", name: "Internal Storage", type: "fixed", capacity: 64424509440, availableCapacity: 32212254720 }];
                                    if (callback) callback(info);
                                    return Promise.resolve(info);
                                }
                            }
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
