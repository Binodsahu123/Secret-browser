package com.example.browserengine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.util.concurrent.ConcurrentHashMap

class TabSnapshotEngine {
    private val snapshotCache = ConcurrentHashMap<String, Bitmap>()

    fun captureSnapshot(tabId: String, view: View) {
        try {
            if (view.width <= 0 || view.height <= 0) return
            
            // Create a matching Bitmap representation
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            
            // Replace old Cache bitmap
            val old = snapshotCache.put(tabId, bitmap)
            old?.recycle()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getSnapshot(tabId: String): Bitmap? {
        val bmp = snapshotCache[tabId]
        if (bmp != null && !bmp.isRecycled) {
            return bmp
        }
        return null
    }

    fun hasSnapshot(tabId: String): Boolean {
        val bmp = snapshotCache[tabId]
        return bmp != null && !bmp.isRecycled
    }

    fun removeSnapshot(tabId: String) {
        val bmp = snapshotCache.remove(tabId)
        if (bmp != null && !bmp.isRecycled) {
            bmp.recycle()
        }
    }

    fun clearAll() {
        snapshotCache.forEach { (_, bmp) ->
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        snapshotCache.clear()
    }
}
