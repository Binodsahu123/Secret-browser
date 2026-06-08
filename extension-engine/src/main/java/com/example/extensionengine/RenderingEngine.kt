package com.example.extensionengine

import android.content.Context
import android.graphics.Bitmap
import android.view.View

/**
 * Interface representing the UI viewport and rendering canvas.
 * Decouples the presentation layer from the underlying Chromium/native render tree.
 */
interface RenderingEngine {
    /**
     * Instantiates or retrieves the visual rendering View for the specified tab.
     */
    fun getOrCreateRenderView(tabId: String, context: Context): View

    /**
     * Captures a visual snapshot of the currently focused document model.
     */
    fun captureScreenshot(tabId: String, callback: (Bitmap?) -> Unit)

    /**
     * Pauses intensive rendering and background timers for inactive tabs.
     */
    fun pauseRendering(tabId: String)

    /**
     * Resumes the layout rendering schedule.
     */
    fun resumeRendering(tabId: String)

    /**
     * Destroys layout structures and cleans up memory associated with the tab.
     */
    fun destroyRenderView(tabId: String)

    /**
     * Clears local system render cache, disk databases, and indexed database trees.
     */
    fun clearCache(context: Context, includeDiskFiles: Boolean)

    /**
     * Controls whether hardware accelerating compositing is used.
     */
    fun setHardwareAccelerationEnabled(enabled: Boolean)

    /**
     * Adjusts the rendering view's text scaling or viewport zoom.
     */
    fun setZoomPercent(tabId: String, percent: Int)
}
