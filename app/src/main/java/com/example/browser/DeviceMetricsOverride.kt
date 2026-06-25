package com.example.browser

object DeviceMetricsOverride {
    fun getMetricsOverrideScript(isDesktop: Boolean, screenWidth: Int = 1920, screenHeight: Int = 1080): String {
        if (!isDesktop) {
            return """
                (function() {
                    delete window.screen.width;
                    delete window.screen.height;
                    delete window.devicePixelRatio;
                })();
            """.trimIndent()
        }

        return """
            (function() {
                try {
                    // Override screen size APIs
                    Object.defineProperty(window.screen, 'width', { get: function() { return $screenWidth; }, configurable: true });
                    Object.defineProperty(window.screen, 'height', { get: function() { return $screenHeight; }, configurable: true });
                    Object.defineProperty(window.screen, 'availWidth', { get: function() { return $screenWidth; }, configurable: true });
                    Object.defineProperty(window.screen, 'availHeight', { get: function() { return $screenHeight; }, configurable: true });
                    
                    // Override device pixel ratio to standard desktop value
                    Object.defineProperty(window, 'devicePixelRatio', { get: function() { return 1.0; }, configurable: true });
                    
                    // Mock window inner boundaries if required
                    Object.defineProperty(window, 'innerWidth', { get: function() { return 1280; }, configurable: true });
                    Object.defineProperty(window, 'innerHeight', { get: function() { return 800; }, configurable: true });
                } catch(e) {
                    console.error("DeviceMetricsOverride failure: " + e.message);
                }
            })();
        """.trimIndent()
    }
}
