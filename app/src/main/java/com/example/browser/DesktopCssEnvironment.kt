package com.example.browser

object DesktopCssEnvironment {
    fun getDesktopCssOverrideScript(): String {
        return """
            (function() {
                var styleId = 'orion-desktop-css-overrides';
                var existing = document.getElementById(styleId);
                if (existing) return;

                var styleNode = document.createElement('style');
                styleNode.id = styleId;
                styleNode.type = 'text/css';
                styleNode.innerHTML = `
                    /* Force responsive layout frameworks to trigger desktop breakpoints */
                    @media (max-width: 767px) {
                        body, html, main, .main, #main, container, .container, #container {
                            min-width: 1200px !important;
                            max-width: 100% !important;
                            width: auto !important;
                        }
                    }
                    /* Override custom mobile narrow-width locks */
                    .mobile-only, .mobile-view, .mobile-header, .mobile-footer {
                        display: none !important;
                    }
                    .desktop-only, .desktop-view {
                        display: block !important;
                    }
                `;
                document.head.appendChild(styleNode);
                
                // Emulate mouse hover events on touch-start
                document.addEventListener('touchstart', function(e) {
                    var touch = e.touches[0];
                    var element = document.elementFromPoint(touch.clientX, touch.clientY);
                    if (element) {
                        var hoverEvent = new MouseEvent('mouseover', {
                            clientX: touch.clientX,
                            clientY: touch.clientY,
                            bubbles: true
                        });
                        element.dispatchEvent(hoverEvent);
                    }
                }, { passive: true });
            })();
        """.trimIndent()
    }

    fun getMobileCssRestoreScript(): String {
        return """
            (function() {
                var styleNode = document.getElementById('orion-desktop-css-overrides');
                if (styleNode) {
                    styleNode.parentNode.removeChild(styleNode);
                }
            })();
        """.trimIndent()
    }
}
