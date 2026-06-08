package com.example.extensionengine

import android.content.Context

/**
 * Top-level abstract interface representing the decoupled browser engine.
 * It manages the lifecycle and exposes specialized components.
 */
interface BrowserEngine {
    val context: Context
    val renderingEngine: RenderingEngine
    val navigationEngine: NavigationEngine
    val tabEngine: TabEngine
    val downloadEngine: DownloadEngine
    val permissionEngine: PermissionEngine
    val storageEngine: StorageEngine
    val extensionEngine: ExtensionEngine

    /**
     * Initializes the rendering backend, native libs, and configurations.
     */
    fun init()

    /**
     * Safely releases any resource-intensive JNI instances or background runners.
     */
    fun shutdown()
}
