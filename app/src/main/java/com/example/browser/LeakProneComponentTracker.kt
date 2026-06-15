package com.example.browser

import android.content.Context
import java.lang.ref.WeakReference

object LeakProneComponentTracker {
    private val trackedContexts = mutableListOf<WeakReference<Context>>()

    fun trackContext(context: Context) {
        trackedContexts.add(WeakReference(context))
        trackedContexts.removeAll { it.get() == null }
    }
}
