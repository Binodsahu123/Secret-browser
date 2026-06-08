package com.example.extensionengine

/**
 * Interface representing the tab manager subsystem.
 * Handles instantiating, referencing, switching, and destroying individual multi-process frames.
 */
interface TabEngine {
    /**
     * Instantiates a new tab container inside the engine's tracking registry.
     */
    fun createTab(url: String, parentTabId: String? = null): String

    /**
     * Unregisters and disposes of the tab context.
     */
    fun closeTab(tabId: String)

    /**
     * Focuses the chosen tab ID to be the active screen viewport.
     */
    fun selectTab(tabId: String)

    /**
     * Returns the currently active tab identifier.
     */
    fun getActiveTabId(): String?

    /**
     * Retrieves all valid tab identifiers registered under this session.
     */
    fun getOpenTabs(): List<String>
}
