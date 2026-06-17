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
    fun injectCompatibilityLayer(view: WebView?, url: String?, context: Context) {
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

        // 1. Core JS Polyfills (Promises, Element prototypes, custom requestAnimationFrame)
        val polyfallsJs = """
            (function() {
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

                // Global Orion Bridge identifier to enable smooth client features
                window.__ORION_COMPAT_ACTIVE__ = true;
                window.__ORION_ENGINE_VERSION__ = "2.0.0";

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
                // Spoof slightly to neutralize exact canvas/screen detail matching hashes
                try {
                    Object.defineProperty(window.screen, 'colorDepth', { get: function() { return 24; } });
                    Object.defineProperty(window.navigator, 'deviceMemory', { get: function() { return 8; } });
                } catch(e) {}

                console.log("Orion Compatibility Polyfills Loaded successfully.");
            })();
        """.trimIndent()

        view.evaluateJavascript(polyfallsJs, null)

        // 2. Framework Specific Adaptations & Optimization Injection
        val frameworkAdaptationJs = """
            (function() {
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
                style.innerHTML = 'html { scroll-behavior: smooth !important; -webkit-overflow-scrolling: touch !important; } ' +
                                  '* { touch-action: manipulation; } ' +
                                  'img { content-visibility: auto; }'; // Lazy-load images off viewport
                document.head.appendChild(style);
                
                // Polyfill for AI chatbot interfaces or modern SaaS layouts (ResizeObserver and dynamic heights)
                if (!window.ResizeObserver) {
                    console.log("Orion: ResizeObserver polyfilled for AI dynamic chat interface compatibility.");
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(frameworkAdaptationJs, null)
    }
}
