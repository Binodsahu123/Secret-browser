/**
 * DECOUPLED 3-LAYER WEB AUDIO HARDWARE BYPASS PIPELINE
 * UI Layer - Deep Javascipt Interceptor with Material 3 Design Prompt & Audio Loopback
 */
(function() {
    if (window.__ui_interceptor_active__) {
        console.log("UI Interceptor: Already injected on this frame landscape.");
        return;
    }
    window.__ui_interceptor_active__ = true;

    console.log("UI Interceptor Init: Monkey patching getUserMedia to route raw NDK stream...");

    // Safe backup representation
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        window.nativeMediaDevicesBackup = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    } else {
        window.nativeMediaDevicesBackup = function(c) {
            return Promise.reject(new DOMException("Unsecure context or missing mediaDevices in container", "NotSupportedError"));
        };
    }

    // High performance promise references to keep track of active pipelines
    window.activePermissionPromise = null;

    // Elegant, accessible Dialog prompt complying with modern Material Design guidelines
    function injectCustomPermissionUI(origin, callback) {
        const modalId = "custom-browser-permission-dialog";
        if (document.getElementById(modalId)) return;

        const dialogContainer = document.createElement("div");
        dialogContainer.id = modalId;
        
        // Material Design 3 style with rounded tokens, dark neutral colors, shadows, and crisp typography
        dialogContainer.style = `
            position: fixed;
            top: 16px;
            left: 16px;
            right: 16px;
            max-width: 480px;
            margin: 0 auto;
            background: #ffffff;
            z-index: 2147483647;
            border-radius: 16px;
            box-shadow: 0px 8px 24px rgba(0, 0, 0, 0.15);
            font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            padding: 20px;
            box-sizing: border-box;
            border: 1px solid rgba(0, 0, 0, 0.08);
            display: flex;
            flex-direction: column;
            gap: 16px;
            animation: slideDown 0.3s ease-out;
        `;

        // Inject the slide down animation helper
        const styleSheet = document.createElement("style");
        styleSheet.innerText = `
            @keyframes slideDown {
                from { transform: translateY(-100%); opacity: 0; }
                to { transform: translateY(0); opacity: 1; }
            }
            .perm-btn {
                padding: 10px 24px;
                border-radius: 100px;
                font-weight: 600;
                font-size: 14px;
                cursor: pointer;
                transition: all 0.2s cubic-bezier(0.2, 0, 0, 1);
                border: none;
                outline: none;
                min-height: 40px;
                touch-action: manipulation;
            }
            .perm-btn-deny {
                background: transparent;
                border: 1px solid #74777f;
                color: #6750a4;
            }
            .perm-btn-allow {
                background: #6750a4;
                color: #ffffff;
                box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            }
            .perm-btn-allow:hover {
                background: #4f378b;
                box-shadow: 0 2px 6px rgba(0,0,0,0.15);
            }
        `;
        document.head.appendChild(styleSheet);
        
        dialogContainer.innerHTML = `
            <div style="display: flex; align-items: flex-start; gap: 14px;">
                <div style="background: #e8def8; padding: 10px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
                    <svg viewBox="0 0 24 24" width="24" height="24" fill="#1d192b">
                        <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z"/>
                    </svg>
                </div>
                <div style="display: flex; flex-direction: column; gap: 4px;">
                    <span style="font-size: 16px; font-weight: 700; color: #1c1b1f; letter-spacing: 0.1px;">Verify Mic Connection</span>
                    <span style="font-size: 14px; color: #49454f; line-height: 1.4;">The website <b style="color:#000;">${origin}</b> requests access to your recording hardware.</span>
                </div>
            </div>
            <div style="display: flex; justify-content: flex-end; gap: 12px; width: 100%;">
                <button class="perm-btn perm-btn-deny" id="btn-perm-deny">Deny</button>
                <button class="perm-btn perm-btn-allow" id="btn-perm-allow">Allow</button>
            </div>
        `;

        document.body.appendChild(dialogContainer);

        document.getElementById("btn-perm-deny").onclick = function() {
            dialogContainer.remove();
            callback(false);
        };

        document.getElementById("btn-perm-allow").onclick = function() {
            dialogContainer.remove();
            callback(true);
        };
    }

    // 2. Redirect system calls
    navigator.mediaDevices.getUserMedia = function(constraints) {
        return new Promise(function(resolve, reject) {
            if (!constraints || !constraints.audio) {
                // Audio not requested - let system stream pass conventionally
                window.nativeMediaDevicesBackup(constraints).then(resolve).catch(reject);
                return;
            }

            const pageOrigin = window.location.origin;

            injectCustomPermissionUI(pageOrigin, function(userAllowed) {
                if (!userAllowed) {
                    reject(new DOMException("Permission denied by user UI", "NotAllowedError"));
                    return;
                }

                // If bridge matches, prepare C++ stream loops
                if (window.NativeAudioBridge) {
                    const trackingId = "tx_" + Date.now() + "_" + Math.floor(Math.random()*1000);
                    window.activePermissionPromise = {
                        resolve: resolve,
                        reject: reject,
                        constraints: constraints
                    };
                    window.NativeAudioBridge.initializeHardwareStream(trackingId, pageOrigin);
                } else {
                    console.warn("UI Interceptor: NativeAudioBridge missing. Defaulting to stock fallback...");
                    window.nativeMediaDevicesBackup(constraints).then(resolve).catch(reject);
                }
            });
        });
    };

    // 3. Low-latency native PCM feedback channel socket client
    window.onNativeHardwareStreamReady = function(streamUrl) {
        console.log("UI Interceptor: JNI loopback feed is ready. Hooking stream: " + streamUrl);
        if (!window.activePermissionPromise) {
            console.error("UI Interceptor: No active promise found to handle local loopback!");
            return;
        }

        const resolve = window.activePermissionPromise.resolve;
        const reject = window.activePermissionPromise.reject;
        window.activePermissionPromise = null;

        try {
            // Instantiate standard dynamic synthesis context to buffer PCM direct from localhost Web Socket
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
            const destination = audioCtx.createMediaStreamDestination();
            
            const ws = new WebSocket(streamUrl);
            ws.binaryType = 'arraybuffer';
            
            // Allocate a rolling playout sample tracker
            let nextStartTime = audioCtx.currentTime;
            const BUFFER_TIME_SPAN = 0.05; // 50ms playout frame time span to buffer chunks smoothly

            ws.onmessage = function(event) {
                const rawBuffer = event.data;
                const int16Array = new Int16Array(rawBuffer);
                const float32Array = new Float32Array(int16Array.length);
                
                // Pure high-capacity normalization
                for (let i = 0; i < int16Array.length; i++) {
                    float32Array[i] = int16Array[i] / 32768.0;
                }
                
                const audioBuffer = audioCtx.createBuffer(1, float32Array.length, 16000);
                audioBuffer.getChannelData(0).set(float32Array);
                
                const bufferSource = audioCtx.createBufferSource();
                bufferSource.buffer = audioBuffer;
                bufferSource.connect(destination);

                // Lock scheduler timeline to prevent digital clicking or frame overlaps
                const currentTime = audioCtx.currentTime;
                if (nextStartTime < currentTime) {
                    nextStartTime = currentTime + 0.01;
                }
                bufferSource.start(nextStartTime);
                nextStartTime += audioBuffer.duration;
            };

            ws.onerror = function(e) {
                console.error("UI Interceptor: High performance WebSocket stream disrupted.");
            };
            
            ws.onclose = function() {
                console.log("UI Interceptor: Stream WebSocket closed.");
            };

            // Resolve modern audio capture wrapper with synthetic media stream feed
            resolve(destination.stream);
        } catch (err) {
            console.error("UI Interceptor: Streaming setup failed: ", err);
            reject(new DOMException(err.message, "AbortError"));
        }
    };
})();
