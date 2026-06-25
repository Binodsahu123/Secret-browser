package com.example.browser

object UserAgentManager {
    // True Chrome Desktop User-Agent (matches Windows Chrome 126 parity)
    const val DESKTOP_UA_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Orion/2.0"

    // Alternative macOS Safari User-Agent
    const val DESKTOP_UA_SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15 Orion/2.0"

    // iPad User-Agent for tablet layout testing
    const val TABLET_UA_IPAD = "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/605.1.15 Orion/2.0"

    // Default Mobile User-Agent string
    const val MOBILE_UA_CHROME = "Mozilla/5.0 (Linux; Android 13; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 Orion/2.0"

    fun getDesktopUserAgent(host: String): String {
        return when {
            host.contains("apple.com") || host.contains("icloud.com") -> DESKTOP_UA_SAFARI
            else -> DESKTOP_UA_CHROME
        }
    }

    fun getMobileUserAgent(): String {
        return MOBILE_UA_CHROME
    }
}
