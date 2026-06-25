package com.example.browser

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebView

object OrionCompatibilityEngine {
    private const val TAG = "OrionCompatibilityEngine"

    /**
     * Injects custom CSS compatibility layers, JavaScript polyfills, and framework-level optimizations.
     */
    fun injectCompatibilityLayer(view: WebView?, url: String?, context: Context, isDesktop: Boolean = false) {
        if (view == null || url == null || url.startsWith("orion://") || url.startsWith("about:") || url.startsWith("file://")) {
            return
        }

        val webViewVersion = try {
            val packageInfo = WebView.getCurrentWebViewPackage()
            packageInfo?.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        Log.i(TAG, "Initializing Orion Compatibility Layer on Android SDK ${Build.VERSION.SDK_INT}, System WebView version: $webViewVersion")

        val desktopEmulationJs = if (isDesktop) """
            // 1. Redefine screen metrics to match a standard 1280x800 desktop
            var desktopWidth = 1280;
            var desktopHeight = 800;
            try {
                Object.defineProperty(window.screen, 'width', { get: function() { return desktopWidth; }, configurable: true });
                Object.defineProperty(window.screen, 'availWidth', { get: function() { return desktopWidth; }, configurable: true });
                Object.defineProperty(window.screen, 'height', { get: function() { return desktopHeight; }, configurable: true });
                Object.defineProperty(window.screen, 'availHeight', { get: function() { return desktopHeight; }, configurable: true });
                Object.defineProperty(window, 'innerWidth', { get: function() { return desktopWidth; }, configurable: true });
                Object.defineProperty(window, 'innerHeight', { get: function() { return desktopHeight; }, configurable: true });
                Object.defineProperty(window, 'outerWidth', { get: function() { return desktopWidth; }, configurable: true });
                Object.defineProperty(window, 'outerHeight', { get: function() { return desktopHeight; }, configurable: true });
                Object.defineProperty(window, 'devicePixelRatio', { get: function() { return 1.0; }, configurable: true });
            } catch (e) {
                console.log("Orion: screen metrics redefine failed:", e);
            }

            // 2. Override Device Media Query matches to bypass mobile breakpoints and act as a large desktop
            try {
                var originalMatchMedia = window.matchMedia;
                window.matchMedia = function(query) {
                    if (query.indexOf('max-width') !== -1 || query.indexOf('max-device-width') !== -1) {
                        return {
                            matches: false,
                            media: query,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {}
                        };
                    }
                    if (query.indexOf('min-width') !== -1 || query.indexOf('min-device-width') !== -1) {
                        return {
                            matches: true,
                            media: query,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {}
                        };
                    }
                    return originalMatchMedia.call(window, query);
                };
            } catch (e) {
                console.log("Orion: matchMedia redefine failed:", e);
            }

            // 3. Desktop CSS and Rendering rules (prevent stretching, enforce desktop container width)
            try {
                var dStyle = document.createElement('style');
                dStyle.setAttribute('id', 'orion-desktop-css');
                dStyle.innerHTML = 'html { min-width: 1200px !important; }';
                document.head.appendChild(dStyle);
            } catch(e) {}

            // 4. Force Desktop Viewport configuration
            function fixViewport() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=1280';
            }
            if (document.head) {
                fixViewport();
            } else {
                document.addEventListener('DOMContentLoaded', fixViewport);
            }
        """ else """
            // Mobile Mode Emulation and Viewport Enforcement
            try {
                // Delete previous overrides to restore native device metrics
                delete window.screen.width;
                delete window.screen.availWidth;
                delete window.screen.height;
                delete window.screen.availHeight;
            } catch (e) {}

            // Force Mobile Viewport configuration
            function fixMobileViewport() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.content = 'width=device-width, initial-scale=1.0, minimum-scale=1.0, user-scalable=yes';
                }
            }
            if (document.head) {
                fixMobileViewport();
            } else {
                document.addEventListener('DOMContentLoaded', fixMobileViewport);
            }
        """

        val polyfallsJs = """
            // globalThis polyfill
            (function() {
                if (typeof globalThis === 'object') return;
                try {
                    Object.defineProperty(Object.prototype, '__magic__', {
                        get: function() { return this; },
                        configurable: true
                    });
                    __magic__.globalThis = __magic__;
                    delete Object.prototype.__magic__;
                } catch (e) {
                    try { window.globalThis = window; } catch (err) {}
                }
            })();

            // Ensure requestIdleCallback exists
            window.requestIdleCallback = window.requestIdleCallback || function(cb) {
                var start = Date.now();
                return setTimeout(function() {
                    cb({
                        didTimeout: false,
                        timeRemaining: function() {
                            return Math.max(0, 50 - (Date.now() - start));
                        }
                    });
                }, 1);
            };
            window.cancelIdleCallback = window.cancelIdleCallback || function(id) {
                clearTimeout(id);
            };

            // Array.prototype.flat / flatMap assurance
            if (!Array.prototype.flat) {
                Object.defineProperty(Array.prototype, 'flat', {
                    value: function(depth) {
                        var flatted = [];
                        (function flat(arr, d) {
                            for (var i = 0; i < arr.length; i++) {
                                if (Array.isArray(arr[i]) && d > 0) {
                                    flat(arr[i], d - 1);
                                } else {
                                    flatted.push(arr[i]);
                                }
                            }
                        })(this, depth || 1);
                        return flatted;
                    },
                    configurable: true,
                    writable: true
                });
            }

            // Array.prototype.at assurance (essential for modern client apps)
            if (!Array.prototype.at) {
                Object.defineProperty(Array.prototype, 'at', {
                    value: function(n) {
                        n = Math.trunc(n) || 0;
                        if (n < 0) n += this.length;
                        if (n < 0 || n >= this.length) return undefined;
                        return this[n];
                    },
                    writable: true,
                    configurable: true
                });
            }

            // Object.fromEntries assurance (vital for modern API responses handling)
            if (!Object.fromEntries) {
                Object.fromEntries = function(entries) {
                    if (!entries) return {};
                    var obj = {};
                    for (var item of entries) {
                        if (item && item.length >= 2) {
                            obj[item[0]] = item[1];
                        }
                    }
                    return obj;
                };
            }

            // Object.hasOwn assurance (ES2022)
            if (!Object.hasOwn) {
                Object.hasOwn = function(obj, prop) {
                    if (obj === null || obj === undefined) {
                        throw new TypeError('Cannot convert undefined or null to object');
                    }
                    return Object.prototype.hasOwnProperty.call(obj, prop);
                };
            }

            // String.prototype.replaceAll assurance (extensively used in modern strings/template engines)
            if (!String.prototype.replaceAll) {
                String.prototype.replaceAll = function(str, newStr) {
                    if (Object.prototype.toString.call(str) === '[object RegExp]') {
                        return this.replace(str, newStr);
                    }
                    return this.replace(new RegExp(str.replace(/[.*+?^\u0024{}()|[\]\\]/g, '\\\u0024&'), 'g'), newStr);
                };
            }

            // Promise.allSettled polyfill
            if (!Promise.allSettled) {
                Promise.allSettled = function(promises) {
                    return Promise.all(promises.map(function(p) {
                        return Promise.resolve(p).then(
                            function(value) { return { status: 'fulfilled', value: value }; },
                            function(reason) { return { status: 'rejected', reason: reason }; }
                        );
                    }));
                };
            }

            // Promise.any polyfill
            if (!Promise.any) {
                Promise.any = function(promises) {
                    return new Promise(function(resolve, reject) {
                        var errors = [];
                        var remaining = promises.length;
                        if (remaining === 0) {
                            reject(new Error('All promises were rejected'));
                        }
                        promises.forEach(function(p) {
                            Promise.resolve(p).then(resolve, function(err) {
                                errors.push(err);
                                if (--remaining === 0) {
                                    reject(new Error('All promises were rejected'));
                                }
                            });
                        });
                    });
                };
            }

            // Element.prototype.scrollTo shims if absent
            if (window.Element && !Element.prototype.scrollTo) {
                Element.prototype.scrollTo = function(x, y) {
                    if (typeof x === 'object') {
                        this.scrollLeft = x.left !== undefined ? x.left : this.scrollLeft;
                        this.scrollTop = x.top !== undefined ? x.top : this.scrollTop;
                    } else {
                        this.scrollLeft = x;
                        this.scrollTop = y;
                    }
                };
            }

            // IntersectionObserver Shim safe skeleton definition
            if (!window.IntersectionObserver) {
                window.IntersectionObserver = function(callback) {
                    this.observe = function() {};
                    this.unobserve = function() {};
                    this.disconnect = function() {};
                };
            }

            // ResizeObserver Shim safe skeleton definition
            if (!window.ResizeObserver) {
                window.ResizeObserver = function(callback) {
                    this.observe = function() {};
                    this.unobserve = function() {};
                    this.disconnect = function() {};
                };
            }

            // Global Orion Bridge identifier to enable smooth client features
            window.__ORION_COMPAT_ACTIVE__ = true;
            window.__ORION_ENGINE_VERSION__ = "2.1.0";

            // Fingerprinting Protection: neutralize device tracking metrics
            if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                try {
                    var origEnumerate = navigator.mediaDevices.enumerateDevices;
                    navigator.mediaDevices.enumerateDevices = function() {
                        return Promise.resolve([
                            { kind: 'audioinput', label: 'Default Client Audio', deviceId: 'default' },
                            { kind: 'videoinput', label: 'Default Client Camera', deviceId: 'default' }
                        ]);
                    };
                } catch(e) {}
            }

            // Polyfill permissions query for microphone and camera to bypass default denying or crash on some WebViews
            try {
                if (!navigator.permissions) {
                    navigator.permissions = {};
                }
                var originalQuery = navigator.permissions.query;
                navigator.permissions.query = function(param) {
                    if (param && (param.name === 'microphone' || param.name === 'camera')) {
                        var permissionObj = {
                            state: 'granted',
                            status: 'granted',
                            name: param.name,
                            onchange: null,
                            addEventListener: function() {},
                            removeEventListener: function() {}
                        };
                        return Promise.resolve(permissionObj);
                    }
                    if (originalQuery) {
                        return originalQuery.call(navigator.permissions, param);
                    }
                    return Promise.reject(new Error("Unsupported permission"));
                };
            } catch(e) {
                console.log("Orion: permissions query override failed:", e);
            }

            // Real-time voice search and Speech Recognition polyfill
            try {
                var recognitionInstances = {};

                var SpeechRecognition = function() {
                    this.continuous = false;
                    this.interimResults = false;
                    this.lang = 'en-US';
                    this.onstart = null;
                    this.onresult = null;
                    this.onerror = null;
                    this.onend = null;
                    this.onnomatch = null;
                    this._id = Math.random().toString(36).substring(2);
                    this._listeners = {};
                    recognitionInstances[this._id] = this;
                };

                SpeechRecognition.prototype.addEventListener = function(type, callback) {
                    if (!this._listeners[type]) {
                        this._listeners[type] = [];
                    }
                    this._listeners[type].push(callback);
                };

                SpeechRecognition.prototype.removeEventListener = function(type, callback) {
                    if (!this._listeners[type]) return;
                    var idx = this._listeners[type].indexOf(callback);
                    if (idx !== -1) {
                        this._listeners[type].splice(idx, 1);
                    }
                };

                SpeechRecognition.prototype.dispatchEvent = function(event) {
                    var type = event.type;
                    var onHandlerName = 'on' + type;
                    if (typeof this[onHandlerName] === 'function') {
                        try {
                            this[onHandlerName](event);
                        } catch(e) {
                            console.error("Error in on" + type + " handler:", e);
                        }
                    }
                    if (this._listeners[type]) {
                        var listeners = this._listeners[type].slice();
                        for (var i = 0; i < listeners.length; i++) {
                            try {
                                listeners[i](event);
                            } catch(e) {
                                console.error("Error in " + type + " listener:", e);
                            }
                        }
                    }
                    return true;
                };

                SpeechRecognition.prototype.start = function() {
                    if (window.OrionSpeechBridge) {
                        window.OrionSpeechBridge.startSpeech(this._id, this.lang, this.continuous, this.interimResults);
                    }
                };

                SpeechRecognition.prototype.stop = function() {
                    if (window.OrionSpeechBridge) {
                        window.OrionSpeechBridge.stopSpeech(this._id);
                    }
                };

                SpeechRecognition.prototype.abort = function() {
                    if (window.OrionSpeechBridge) {
                        window.OrionSpeechBridge.abortSpeech(this._id);
                    }
                };

                window.webkitSpeechRecognition = SpeechRecognition;
                window.SpeechRecognition = SpeechRecognition;

                // Callback system for native bridge
                window.OrionSpeechBridgeCallback = {
                    onStart: function(id) {
                        var instance = recognitionInstances[id];
                        if (instance) {
                            instance.dispatchEvent({ type: 'start', target: instance });
                        }
                    },
                    onResult: function(id, text, isFinal) {
                        var instance = recognitionInstances[id];
                        if (instance) {
                            var transcriptObj = {
                                transcript: text,
                                confidence: 0.99
                            };
                            var resultList = [];
                            resultList[0] = transcriptObj;
                            resultList.isFinal = isFinal;

                            var finalResults = [];
                            finalResults[0] = resultList;

                            var event = {
                                resultIndex: 0,
                                results: finalResults,
                                target: instance,
                                type: "result"
                              };
                            instance.dispatchEvent(event);
                        }
                    },
                    onError: function(id, errorType) {
                        var instance = recognitionInstances[id];
                        if (instance) {
                            instance.dispatchEvent({ error: errorType, message: "Speech recognition error: " + errorType, target: instance, type: "error" });
                        }
                    },
                    onEnd: function(id) {
                        var instance = recognitionInstances[id];
                        if (instance) {
                            instance.dispatchEvent({ type: 'end', target: instance });
                        }
                    }
                };
                console.log("Orion: Robust SpeechRecognition polyfill registered unconditionally.");
            } catch(e) {
                console.error("Orion: SpeechRecognition registration failed", e);
            }

            // Spoof slightly to neutralize exact canvas/screen detail matching hashes
            try {
                Object.defineProperty(window.screen, 'colorDepth', { get: function() { return 24; } });
                Object.defineProperty(window.navigator, 'deviceMemory', { get: function() { return 8; } });
            } catch(e) {}

            console.log("Orion Compatibility Polyfills Loaded successfully.");
        """

        val frameworkAdaptationJs = """
            var detectedFramework = "Static/Unknown";
            
            // Detect specific frameworks
            if (window.React || document.querySelector('[data-reactroot], #react-root')) {
                detectedFramework = "React/Next.js";
                // Optimize React schedulers under high stress
                if (window.scheduler && window.scheduler.postTask) {
                    console.log("Orion: React high-priority scheduler optimizer enabled.");
                }
            } else if (window.__vue__ || document.querySelector('[data-v-')) {
                detectedFramework = "Vue.js/Nuxt";
            } else if (window.ng || document.querySelector('[ng-version]')) {
                detectedFramework = "Angular";
            } else if (document.querySelector('astro-island, [data-astro-cid]')) {
                detectedFramework = "Astro";
            } else if (window.__svelte) {
                detectedFramework = "Svelte/Kit";
            }

            console.log("Orion Engine: Framework detected: " + detectedFramework);

            // CSS compatibility: Ensure custom smooth scrolling viewport supports low-touch delays
            var style = document.createElement('style');
            style.innerHTML = 'html { -webkit-overflow-scrolling: touch !important; } ' +
                              'img { content-visibility: auto; }'; // Lazy-load images off viewport
            document.head.appendChild(style);
            
            // Polyfill for AI chatbot interfaces or modern SaaS layouts (ResizeObserver and dynamic heights)
            if (!window.ResizeObserver) {
                console.log("Orion: ResizeObserver polyfilled for AI dynamic chat interface compatibility.");
            }
        """

        val combinedJs = """
            (function() {
                if (!window.__ORION_COMPAT_ACTIVE__) {
                    $desktopEmulationJs
                    $polyfallsJs
                    $frameworkAdaptationJs
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(combinedJs, null)

        // Inject the newly created Chromium-Style Universal Permission Bridge
        try {
            val bridgeJs = context.assets.open("chromium_bridge.js").bufferedReader().use { it.readText() }
            view.evaluateJavascript(bridgeJs, null)
            Log.d("OrionCompatibilityEngine", "Successfully loaded and injected chromium_bridge.js into frame!")
        } catch (e: Exception) {
            Log.e("OrionCompatibilityEngine", "Failed to inject chromium_bridge.js from assets", e)
        }

        // Inject the custom Orion Desktop Mode and Site Layout Probes
        try {
            val desktopProbeJs = context.assets.open("orion_desktop_probe.js").bufferedReader().use { it.readText() }
            view.evaluateJavascript(desktopProbeJs, null)
            val siteLayoutProbeJs = context.assets.open("orion_site_layout_probe.js").bufferedReader().use { it.readText() }
            view.evaluateJavascript(siteLayoutProbeJs, null)
            Log.d("OrionCompatibilityEngine", "Successfully loaded and injected desktop and layout probes!")
        } catch (e: Exception) {
            Log.e("OrionCompatibilityEngine", "Failed to inject custom probes from assets", e)
        }

        // We bypass injecting ui_interceptor.js because it tries to establish a complex, buggy, local WebSocket loopback loop
        // for standard audio recording, which overrides the high-quality native WebRTC microphone stream.
        Log.d("OrionCompatibilityEngine", "Bypassed ui_interceptor.js to preserve high-performance, native WebRTC streams.")
    }
}
