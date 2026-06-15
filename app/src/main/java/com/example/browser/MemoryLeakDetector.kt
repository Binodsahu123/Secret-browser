package com.example.browser

object MemoryLeakDetector {
    fun runSystemGC() {
        System.gc()
        System.runFinalization()
    }
}
