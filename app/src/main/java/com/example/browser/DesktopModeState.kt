package com.example.browser

data class DesktopModeState(
    val isDesktopModeEnabled: Boolean = false,
    val appliedUserAgent: String = "",
    val viewportWidth: Int = 1280,
    val initialScale: Float = 0.25f,
    val devicePixelRatioOverride: Float = 1.0f,
    val isUrlRewritten: Boolean = false,
    val cssOverridesApplied: Boolean = false,
    val isVideoStatePreserved: Boolean = false,
    val lastLoadedUrl: String = ""
)
