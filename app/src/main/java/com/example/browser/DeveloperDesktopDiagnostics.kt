package com.example.browser

import android.util.Log

object DeveloperDesktopDiagnostics {
    private const val TAG = "DesktopDiagnostics"

    fun updateConnectionState(
        userAgentApplied: Boolean = false,
        viewportApplied: Boolean = false,
        cssRulesApplied: Boolean = false,
        hostRewriteApplied: Boolean = false,
        hostRewriteSkipped: Boolean = false,
        desktopPageLoaded: Boolean = false
    ) {
        try {
            OrionDeveloperEngine.desktopConnectionState.value = OrionDeveloperEngine.DesktopConnectionState(
                userAgentApplied = userAgentApplied,
                viewportApplied = viewportApplied,
                cssRulesApplied = cssRulesApplied,
                hostRewriteApplied = hostRewriteApplied,
                hostRewriteSkipped = hostRewriteSkipped,
                desktopPageLoaded = desktopPageLoaded
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update connection state", e)
        }
    }

    fun updateMonitorState(
        desktopModeEnabled: Boolean = false,
        userAgentType: String = "Mobile",
        viewportWidth: Int = 360,
        urlRewriteSuccess: Boolean = true,
        currentUrl: String = ""
    ) {
        try {
            OrionDeveloperEngine.desktopModeMonitorState.value = OrionDeveloperEngine.DesktopModeMonitorState(
                desktopModeEnabled = desktopModeEnabled,
                userAgentType = userAgentType,
                viewportWidth = viewportWidth,
                urlRewriteSuccess = urlRewriteSuccess,
                currentUrl = currentUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update monitor state", e)
        }
    }

    fun logToggleEvent(host: String, isDesktop: Boolean) {
        OrionDeveloperEngine.logError(
            component = "DesktopMode",
            message = "Desktop mode toggled for $host. Active = $isDesktop",
            level = "INFO",
            location = "com.example.browser.DesktopModeManager"
        )
    }

    fun logRewriteSkip(host: String, reason: String) {
        OrionDeveloperEngine.logError(
            component = "DesktopMode",
            message = "Domain rewrite skipped for $host: $reason",
            level = "WARNING",
            location = "com.example.browser.UrlRewriteEngine"
        )
    }
}
