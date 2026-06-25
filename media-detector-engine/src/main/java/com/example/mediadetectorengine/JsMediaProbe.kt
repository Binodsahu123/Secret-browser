package com.example.mediadetectorengine

import android.content.Context
import android.util.Log
import java.io.IOException

object JsMediaProbe {
    private const val TAG = "JsMediaProbe"

    fun loadAssetScript(context: Context, fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e(TAG, "Failed loading JS asset file: $fileName", e)
            ""
        }
    }
}
