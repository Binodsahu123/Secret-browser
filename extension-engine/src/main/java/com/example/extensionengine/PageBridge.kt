package com.example.extensionengine

class PageBridge {
    /**
     * Compiles a javascript bridge executed in the page context to handle
     * event orchestration and cross-context message routing.
     */
    fun compileBridgeScript(): String {
        return """
            (function() {
                if (window.__orionPageBridgeLoaded) return;
                window.__orionPageBridgeLoaded = true;

                // Handle postMessage bridging
                window.addEventListener("message", function(event) {
                    if (event.data && event.data.source === "orion-page") {
                        // Forward message to content script side as CustomEvent
                        const customEvent = new CustomEvent("orion-to-contentscript", {
                            detail: event.data.payload
                        });
                        window.dispatchEvent(customEvent);
                    }
                });

                // Listen to Content Script events and forward to Page if needed
                window.addEventListener("orion-to-page", function(event) {
                    window.postMessage({
                        source: "orion-content-script",
                        payload: event.detail
                    }, "*");
                });
            })();
        """.trimIndent()
    }
}
