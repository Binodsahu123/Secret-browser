package com.example.browser

object ViewportManager {
    private const val DESKTOP_WIDTH = 1280
    private const val DESKTOP_SCALE = 0.25f
    private const val MIN_SCALE = 0.1f
    private const val MAX_SCALE = 2.0f

    fun getDesktopViewportScript(width: Int = DESKTOP_WIDTH, scale: Float = DESKTOP_SCALE): String {
        return """
            (function() {
                var meta = document.querySelector('meta[name=viewport]');
                var contentStr = 'width=$width, initial-scale=$scale, minimum-scale=$MIN_SCALE, maximum-scale=$MAX_SCALE, user-scalable=yes';
                if (meta) {
                    meta.setAttribute('content', contentStr);
                } else {
                    var newMeta = document.createElement('meta');
                    newMeta.name = 'viewport';
                    newMeta.content = contentStr;
                    document.head.appendChild(newMeta);
                }
                // Dispatch resize event to trigger layout redraws
                window.dispatchEvent(new Event('resize'));
            })();
        """.trimIndent()
    }

    fun getMobileViewportRestoreScript(): String {
        return """
            (function() {
                var meta = document.querySelector('meta[name=viewport]');
                if (meta) {
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, user-scalable=yes');
                }
                window.dispatchEvent(new Event('resize'));
            })();
        """.trimIndent()
    }
}
