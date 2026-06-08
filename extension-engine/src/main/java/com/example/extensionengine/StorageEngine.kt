package com.example.extensionengine

/**
 * Interface representing standard cookie systems, key-value storage profiles, and document caching strategies.
 */
interface StorageEngine {
    /**
     * Retrieves are set of cookie records for the chosen domain.
     */
    fun getCookies(url: String): String

    /**
     * Stores a specific cookie header against a domain URL.
     */
    fun setCookie(url: String, cookieValue: String)

    /**
     * Clears all session and persistent cookies.
     */
    fun clearCookies()

    /**
     * Flushes current disk queues or commits configurations.
     */
    fun flush()
}
