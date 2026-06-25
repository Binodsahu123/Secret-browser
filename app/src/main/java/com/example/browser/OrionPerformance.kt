package com.example.browser

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.webkit.WebView
import android.util.Log
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object OrionStartupManager {
    private const val TAG = "OrionStartupManager"
    private var isInitialized = false

    fun initialize(application: Application, scope: CoroutineScope, onFinished: () -> Unit) {
        if (isInitialized) return
        isInitialized = true

        Log.i(TAG, "Initializing OrionStartupManager - Deferring heavy engines for fast Cold-Start")
        
        // Defer heavy initialization to background threads (Cold Start < 1.5 sec)
        scope.launch(Dispatchers.IO) {
            try {
                // Initialize AdBlocker
                com.example.adblockengine.AdBlocker.init(application)
                Log.d(TAG, "Optimized Step: AdBlocker initialized asynchronously")
            } catch (e: Exception) {
                Log.e(TAG, "AdBlocker init failed: ${e.message}")
            }

            try {
                // Initialize Network Sniffer
                com.example.browser.NetworkSnifferEngine.initialize(application)
                Log.d(TAG, "Optimized Step: NetworkSniffer initialized asynchronously")
            } catch (e: Exception) {
                Log.e(TAG, "NetworkSnifferEngine init failed", e)
            }

            // Let UI know initialization finished
            withContext(Dispatchers.Main) {
                onFinished()
                // Warm up WebView Pool on the UI thread during main idle loop
                OrionWebViewWarmup.warmUp(application)
            }
        }
    }
}

object OrionWebViewPool {
    private const val TAG = "OrionWebViewPool"
    private val pool = ArrayDeque<WebView>()
    private var maxPoolSize = 3
    private var webViewFactory: ((Context) -> WebView)? = null

    fun setFactory(factory: (Context) -> WebView) {
        synchronized(pool) {
            this.webViewFactory = factory
        }
    }

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramGB = memInfo.totalMem / (1024L * 1024L * 1024L)
        maxPoolSize = when {
            ramGB >= 6 -> 5
            ramGB >= 4 -> 4
            else -> 2
        }
        Log.i(TAG, "Configured max WebView pool size: $maxPoolSize based on RAM ($ramGB GB)")
    }

    fun obtain(context: Context): WebView {
        synchronized(pool) {
            val recycled = pool.removeFirstOrNull()
            if (recycled != null) {
                Log.i(TAG, "Obtained pre-warmed WebView from pool")
                recycled.clearHistory()
                return recycled
            }
        }
        Log.i(TAG, "Pool empty - instantiating fresh WebView using registered factory")
        val factory = webViewFactory
        return if (factory != null) {
            factory(context)
        } else {
            WebView(context)
        }
    }

    fun recycle(webView: WebView) {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        synchronized(pool) {
            if (pool.size < maxPoolSize) {
                pool.addLast(webView)
                Log.i(TAG, "Recycled WebView into pool. Cache size: ${pool.size}")
            } else {
                Log.i(TAG, "Pool limit reached - destroying WebView")
                webView.destroy()
            }
        }
    }

    fun size(): Int = synchronized(pool) { pool.size }

    fun clear() {
        synchronized(pool) {
            pool.forEach { it.destroy() }
            pool.clear()
            Log.i(TAG, "Cleared WebView pool")
        }
    }
}

object OrionWebViewWarmup {
    private const val TAG = "OrionWebViewWarmup"

    fun warmUp(context: Context) {
        // Run on Main/UI Thread where WebViews must be created but don't block layout pass
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                OrionWebViewPool.init(context)
                val currentSize = OrionWebViewPool.size()
                if (currentSize < 2) {
                    val countToPrewarm = 2 - currentSize
                    Log.i(TAG, "Pre-warming $countToPrewarm WebViews")
                    repeat(countToPrewarm) {
                        val factory = OrionWebViewPool.obtain(context) // will use factory if set, or basic WebView
                        OrionWebViewPool.recycle(factory)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebView pre-warming failed: ${e.message}")
            }
        }, 1500) // Execute 1.5 seconds post-startup to bypass launch bottlenecks
    }
}

object OrionNavigationCache {
    private const val TAG = "OrionNavigationCache"
    
    // Memory cache for web previews/captures or back/forward states (Tab Switch < 100ms)
    private val previewCache = LruCache<String, Bitmap>(20) // URL/TabId -> Bitmap snapshot
    private val headersCache = ConcurrentHashMap<String, Map<String, String>>()

    fun cacheSnapshot(key: String, bitmap: Bitmap) {
        previewCache.put(key, bitmap)
    }

    fun getSnapshot(key: String): Bitmap? {
        return previewCache.get(key)
    }

    fun cacheHeaders(url: String, headers: Map<String, String>) {
        headersCache[url] = headers
    }

    fun getHeaders(url: String): Map<String, String>? {
        return headersCache[url]
    }

    fun clear() {
        previewCache.evictAll()
        headersCache.clear()
    }
}

object OrionMemoryManager : ComponentCallbacks2 {
    private const val TAG = "OrionMemoryManager"
    private var application: Application? = null

    fun register(app: Application) {
        this.application = app
        app.registerComponentCallbacks(this)
        Log.i(TAG, "OrionMemoryManager registered with background memory trim monitors")
    }

    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "onTrimMemory called with code level: $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || 
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            Log.e(TAG, "Critical memory alert! Compacting WebView buffers and clearing pools")
            OrionWebViewPool.clear()
            OrionNavigationCache.clear()
            System.gc()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        Log.e(TAG, "Low Memory threshold met! Aggressively clearing all in-memory pools.")
        OrionWebViewPool.clear()
        OrionNavigationCache.clear()
        System.gc()
    }
}
