package com.example.extensionengine

import android.net.Uri

/**
 * Interface representing the extension integration subsystem.
 * Handles loading, unloading, registering triggers, service worker lifecycles,
 * popup managers, and messaging protocols.
 */
interface ExtensionEngine {
    /**
     * Installs, registers, and initiates runtime routines for an extension zip.
     */
    suspend fun installExtension(uri: Uri): ParsedExtension

    /**
     * Unregisters and deletes extension metadata and background structures from storage.
     */
    suspend fun uninstallExtension(id: String)

    /**
     * Toggles the active running state of an extension.
     */
    suspend fun toggleExtension(id: String, enabled: Boolean)

    /**
     * Shuts down all active service workers, background frames, and ports.
     */
    fun shutdown()
}
