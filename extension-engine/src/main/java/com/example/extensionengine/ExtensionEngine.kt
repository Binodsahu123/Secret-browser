package com.example.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExtensionEngine(
    private val context: Context,
    private val delegate: BrowserDelegate?
) {

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

    private val runtimeMap = mutableMapOf<String, ExtensionRuntime>()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    init {
        // Initialize bridge provider inside background scripts
        backgroundScriptManager.setRuntimeBridgeProvider {
            createBridge(null)
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
                            
                            if (entity.isEnabled) {
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
    fun setupWebView(webView: WebView) {
        webView.post {
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                
                val bridge = createBridge(webView)
                webView.addJavascriptInterface(bridge, "OrionExtensionBridge")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Injects matching scripts on page reload / transition completion.
     */
    fun injectContentScripts(webView: WebView, url: String) {
        if (url.startsWith("orion://") || url == "about:blank" || url.startsWith("file://")) return

        webView.post {
            try {
                // Compile API bootstraps for all active extensions
                val activeList = registry.getAllActiveExtensions()
                for (ext in activeList) {
                    val isEnabled = runtimeMap[ext.id]?.isActive ?: false
                    if (isEnabled && permissionManager.hasHostPermission(ext.hostPermissions, ext.permissions, url)) {
                        val boot = compileBootstrapScript(ext.id)
                        webView.evaluateJavascript(boot, null)
                    }
                }
                
                // Inject matched content scripts
                contentScriptManager.matchAndInject(webView, url, activeList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun installExtension(uri: Uri): ParsedExtension {
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

    suspend fun uninstallExtension(id: String) {
        withContext(Dispatchers.Main) {
            runtimeMap.remove(id)?.stop()
            backgroundScriptManager.stopBackgroundWorker(id)
        }
        registry.unregister(id)
        database.extensionDao().deleteExtensionById(id)
    }

    suspend fun toggleExtension(id: String, enabled: Boolean) {
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

    fun shutdown() {
        backgroundScriptManager.stopAll()
    }

    fun createBridge(webView: WebView?): RuntimeBridge {
        return RuntimeBridge(
            context, webView, storageManager, messageBus, delegate, eventManager
        )
    }

    fun compileBootstrapScript(extensionId: String): String {
        return """
            (function() {
                if (window.browser && window.browser.runtime && window.browser.runtime.id === "$extensionId") return;
                
                const extId = "$extensionId";
                window._extCallbacks = window._extCallbacks || {};
                
                window._extResponse = function(callbackId, error, result) {
                    const cb = window._extCallbacks[callbackId];
                    if (cb) {
                        delete window._extCallbacks[callbackId];
                        cb(result);
                    }
                };
                
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
                                extensionId: extId,
                                args: args || []
                            }), cbId);
                        } catch (e) {
                            reject(e);
                        }
                    });
                };

                window.browser = window.browser || {};
                window.browser.runtime = window.browser.runtime || {};
                window.browser.runtime.id = extId;
                window.browser.runtime.sendMessage = function(msg, callback) {
                    const p = bridgeCall("runtime.sendMessage", [msg]);
                    if (callback) p.then(callback);
                    return p;
                };

                window.browser.storage = window.browser.storage || {};
                window.browser.storage.local = {
                    get: function(keys, callback) {
                        const p = bridgeCall("storage.get", ["local", keys]);
                        if (callback) p.then(callback);
                        return p;
                    },
                    set: function(items, callback) {
                        const p = bridgeCall("storage.set", ["local", items]);
                        if (callback) p.then(callback);
                        return p;
                    },
                    remove: function(keys, callback) {
                        const p = bridgeCall("storage.remove", ["local", keys]);
                        if (callback) p.then(callback);
                        return p;
                    },
                    clear: function(callback) {
                        const p = bridgeCall("storage.clear", ["local"]);
                        if (callback) p.then(callback);
                        return p;
                    }
                };

                window.browser.storage.sync = window.browser.storage.local;

                window.browser.tabs = {
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
                    }
                };

                window.browser.notifications = {
                    create: function(id, options, callback) {
                        const p = bridgeCall("notifications.create", [id, options]);
                        if (callback) p.then(callback);
                        return p;
                    }
                };

                window.browser.downloads = {
                    download: function(options, callback) {
                        const p = bridgeCall("downloads.download", [options]);
                        if (callback) p.then(callback);
                        return p;
                    }
                };

                window.chrome = window.browser;
            })();
        """.trimIndent()
    }
}
