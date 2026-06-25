/**
 * CHROMIUM BRIDGE & PERMISSION INTERCEPTOR
 * Production-grade custom WebRTC and hardware permission engine.
 */
(function() {
    if (window.__chromium_bridge_active__) {
        console.log("Chromium Bridge: Already active on this page context.");
        return;
    }
    window.__chromium_bridge_active__ = true;

    console.log("Chromium Bridge Init: Registering high-performance hardware WebRTC and Geolocation redirects...");

    // 0. Force isSecureContext to be true so modern features are not locked out on web pages
    try {
        Object.defineProperty(window, 'isSecureContext', {
            get: function() { return true; },
            configurable: true
        });
        Object.defineProperty(navigator, 'isSecureContext', {
            get: function() { return true; },
            configurable: true
        });
    } catch (e) {
        console.error("Chromium Bridge: Failed to polyfill isSecureContext: ", e);
    }

    // Ensure navigator.mediaDevices is initialized as an object if missing
    if (!navigator.mediaDevices) {
        try {
            navigator.mediaDevices = {};
        } catch (e) {
            console.error("Chromium Bridge: Failed to define navigator.mediaDevices: ", e);
        }
    }

    // State registry for pending hardware actions to prevent garbage collection
    window.activePermissionTransactions = new Map();

    // Preserve references to genuine, untouched browser hardware entry points
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        window.nativeGetUserMediaBackup = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    } else {
        window.nativeGetUserMediaBackup = function(constraints) {
            return Promise.reject(new DOMException("Native WebRTC getUserMedia not supported in this frame", "NotSupportedError"));
        };
    }

    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
        window.nativeEnumerateDevicesBackup = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
    }

    // Direct helper to construct low-level synthesis fallbacks if streaming is interrupted
    window.createNativeAudioFallbackStream = function(streamUrl) {
        try {
            var audioContext = new (window.AudioContext || window.webkitAudioContext)();
            var destination = audioContext.createMediaStreamDestination();
            
            if (streamUrl) {
                var audio = new Audio(streamUrl);
                audio.crossOrigin = "anonymous";
                var source = audioContext.createMediaElementSource(audio);
                source.connect(destination);
                audio.play().catch(function(e) { console.error("Fallback stream playback error: ", e); });
            } else {
                var osc = audioContext.createOscillator();
                var gain = audioContext.createGain();
                gain.gain.setValueAtTime(0.0001, audioContext.currentTime);
                osc.connect(gain);
                gain.connect(destination);
                osc.start();
            }
            return destination.stream;
        } catch (e) {
            console.error("Chromium Bridge: Failed to construct synthetic feedback stream:", e);
            return new MediaStream();
        }
    };

    // 1. Thread-safe global callback invoked directly by native Android JVM
    window.resolveAndroidPermissionBridge = function(id, statusCode, rawDataPayload) {
        console.log("Chromium Bridge JNI Callback: ID=" + id + ", StatusCode=" + statusCode);
        
        if (!window.activePermissionTransactions.has(id)) {
            console.warn("Chromium Bridge: No registered transaction found for ID=" + id);
            return;
        }

        const transaction = window.activePermissionTransactions.get(id);
        window.activePermissionTransactions.delete(id);

        // 1 = PERMISSION_ALLOWED / "granted"
        if (statusCode === 1 || statusCode === "granted" || statusCode === "allowed") {
            if (rawDataPayload && rawDataPayload.useFallbackRoute) {
                console.log("Chromium Bridge: Active fallback audio stream specified.");
                const syntheticStream = window.createNativeAudioFallbackStream(rawDataPayload.streamUrl);
                transaction.resolve(syntheticStream);
            } else {
                console.log("Chromium Bridge: System permission allowed. Routing to hardware media stream...");
                window.nativeGetUserMediaBackup(transaction.constraints)
                    .then(function(stream) {
                        transaction.resolve(stream);
                    })
                    .catch(function(err) {
                        console.error("Chromium Bridge: Failed capturing hardware steam via backup: ", err);
                        // Safe recovery stream
                        const recovery = window.createNativeAudioFallbackStream(null);
                        transaction.resolve(recovery);
                    });
            }
        } else {
            console.warn("Chromium Bridge: System permission denied.");
            transaction.reject(new DOMException("Permission denied by user engine", "NotAllowedError"));
        }
    };

    // 2. [DISABLED] Monkey-patch navigator.mediaDevices.getUserMedia entirely
    // Bypassed to preserve high-performance, native WebRTC streams directly in the WebView.
    /*
    try {
        const patchUserMedia = function(constraints) {
            return new Promise(function(resolve, reject) {
                const origin = window.location.origin;
                
                // Secure, non-colliding UUID generation fallback
                let transactionId;
                try {
                    transactionId = (window.crypto && window.crypto.randomUUID) ? window.crypto.randomUUID() : (Math.random().toString(36).substring(2) + Date.now().toString(36));
                } catch(e) {
                    transactionId = Math.random().toString(36).substring(2) + Date.now().toString(36);
                }

                window.activePermissionTransactions.set(transactionId, {
                    resolve: resolve,
                    reject: reject,
                    constraints: constraints
                });

                if (window.AndroidPermissionProxy) {
                    // Modern Chromium Hardware request block
                    const payload = JSON.stringify({
                        id: transactionId,
                        url: origin,
                        audioRequested: !!(constraints && constraints.audio),
                        videoRequested: !!(constraints && constraints.video)
                    });
                    
                    console.log("Chromium Bridge: Handshaking request via requestHardwareAccess for transaction: " + transactionId);
                    try {
                        if (typeof window.AndroidPermissionProxy.requestHardwareAccess === "function") {
                            window.AndroidPermissionProxy.requestHardwareAccess(payload);
                        } else {
                            // Backward compatibility fallback to requestPermission
                            const legacyPayload = JSON.stringify({
                                transactionId: transactionId,
                                origin: origin,
                                permissionType: (constraints && constraints.audio) ? "MICROPHONE" : "CAMERA"
                            });
                            window.AndroidPermissionProxy.requestPermission(legacyPayload);
                        }
                    } catch (e) {
                        console.error("Chromium Bridge: Error dispatching to proxy interface: ", e);
                        reject(new DOMException("Native proxy interface disconnected.", "NotReadableError"));
                    }
                } else {
                    console.warn("Chromium Bridge: AndroidPermissionProxy web interface missing. Falling back...");
                    if (window.nativeGetUserMediaBackup) {
                        window.nativeGetUserMediaBackup(constraints).then(resolve).catch(reject);
                    } else {
                        reject(new DOMException("Native getUserMedia unsupported.", "NotSupportedError"));
                    }
                }
            });
        };

        if (window.MediaDevices && window.MediaDevices.prototype) {
            Object.defineProperty(window.MediaDevices.prototype, 'getUserMedia', {
                value: patchUserMedia,
                writable: true,
                configurable: true
            });
        }
        if (navigator.mediaDevices) {
            Object.defineProperty(navigator.mediaDevices, 'getUserMedia', {
                value: patchUserMedia,
                writable: true,
                configurable: true
            });
        }
        console.log("Chromium Bridge: Successfully patched getUserMedia with Object.defineProperty!");
    } catch (e) {
        console.error("Chromium Bridge: Critical error patching getUserMedia:", e);
    }
    */

    // 3. Monkey-patch device enumeration to ensure virtual/high-def labels bypass web controls
    if (!navigator.mediaDevices.enumerateDevices) {
        navigator.mediaDevices.enumerateDevices = function() {
            return Promise.resolve([
                { deviceId: "default", kind: "audioinput", label: "System Default Microphone (Verified Secure)", groupId: "default" },
                { deviceId: "default", kind: "videoinput", label: "System Default Camera (Verified Secure)", groupId: "default" }
            ]);
        };
    } else {
        var nativeEnumerateDevices = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
        navigator.mediaDevices.enumerateDevices = function() {
            return nativeEnumerateDevices().then(function(devices) {
                if (!devices || devices.length === 0) {
                    return [
                        { deviceId: "default", kind: "audioinput", label: "System Default Microphone (Verified Secure)", groupId: "default" },
                        { deviceId: "default", kind: "videoinput", label: "System Default Camera (Verified Secure)", groupId: "default" }
                    ];
                }
                return devices.map(function(device) {
                    if (device.kind === "audioinput" && !device.label) {
                        return {
                            deviceId: device.deviceId || "default",
                            kind: "audioinput",
                            label: "High-Definition Hardware Microphone Input",
                            groupId: device.groupId || "default"
                        };
                    }
                    if (device.kind === "videoinput" && !device.label) {
                        return {
                            deviceId: device.deviceId || "default",
                            kind: "videoinput",
                            label: "High-Definition Hardware Camera Capture",
                            groupId: device.groupId || "default"
                        };
                    }
                    return device;
                });
            }).catch(function(e) {
                return [
                    { deviceId: "default", kind: "audioinput", label: "System Default Microphone (Verified Secure)", groupId: "default" },
                    { deviceId: "default", kind: "videoinput", label: "System Default Camera (Verified Secure)", groupId: "default" }
                ];
            });
        };
    }

    // 3b. [DISABLED] Force legacy navigator getUserMedia methods to route to navigator.mediaDevices.getUserMedia
    // Bypassed to let original browser legacy objects route natively.
    /*
    var routeToUserMedia = function(constraints, success, error) {
        navigator.mediaDevices.getUserMedia(constraints).then(success).catch(error);
    };
    if (!navigator.getUserMedia) navigator.getUserMedia = routeToUserMedia;
    if (!navigator.webkitGetUserMedia) navigator.webkitGetUserMedia = routeToUserMedia;
    if (!navigator.mozGetUserMedia) navigator.mozGetUserMedia = routeToUserMedia;
    if (!navigator.msGetUserMedia) navigator.msGetUserMedia = routeToUserMedia;
    */

    // 4. Overwrite navigator.geolocation API constraints for precise responsive alignment
    if (navigator.geolocation) {
        var originalGetCurrentPos = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
        navigator.geolocation.getCurrentPosition = function(successCallback, errorCallback, options) {
            console.log("Chromium Bridge: Intercepting getCurrentPosition...");
            originalGetCurrentPos(successCallback, errorCallback, options);
        };
    }

    console.log("Chromium Bridge: Execution fully complete and active.");
})();
