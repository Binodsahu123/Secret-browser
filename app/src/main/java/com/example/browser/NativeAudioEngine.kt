package com.example.browser

import android.util.Log

object NativeAudioEngine {
    private const val TAG = "NativeAudioEngine"
    
    var isLibLoaded = false
        private set

    init {
        try {
            System.loadLibrary("native_media_bridge")
            isLibLoaded = true
            Log.i(TAG, "Successfully loaded native_media_bridge shared library JNI bindings!")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native C++ library native_media_bridge not found. Falling back to pure native Android OS Media APIs.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading native bridge", e)
        }
    }

    /**
     * Calls native C++ bridge to bypass Android WebView streaming pipeline blockage using OpenSL ES.
     */
    external fun startNativeCapture(): Boolean
    
    /**
     * Releases native hardware recording resources.
     */
    external fun stopNativeCapture()
}
