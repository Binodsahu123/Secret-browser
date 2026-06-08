package com.example.extensionengine

class PostMessageBridge {
    /**
     * Compiles script for managing MessageChannels and safe cross-origin PostMessage operations.
     */
    fun compilePostMessageScript(): String {
        return """
            window.OrionPostMessage = window.OrionPostMessage || {
                send: function(payload, targetOrigin) {
                    window.postMessage({
                        source: "orion-internal-bridge",
                        payload: payload
                    }, targetOrigin || "*");
                },
                listen: function(onMessageCallback) {
                    const listener = function(event) {
                        if (event.data && event.data.source === "orion-internal-bridge") {
                            onMessageCallback(event.data.payload, event.origin, event.source);
                        }
                    };
                    window.addEventListener("message", listener);
                    return function() {
                        window.removeEventListener("message", listener);
                    };
                }
            };
        """.trimIndent()
    }
}
