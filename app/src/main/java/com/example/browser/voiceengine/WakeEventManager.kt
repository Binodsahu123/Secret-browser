package com.example.browser.voiceengine

import android.content.Context
import android.content.Intent
import android.util.Log

object WakeEventManager {
    interface WakeEventListener {
        fun onWakeEvent(commandPayload: String? = null)
    }

    private val listeners = mutableListOf<WakeEventListener>()

    fun registerListener(listener: WakeEventListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: WakeEventListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun triggerWake(context: Context, commandPayload: String? = null) {
        Log.i("WakeEventManager", "Waking up Orion! Command: $commandPayload")
        
        // Notify local listeners
        synchronized(listeners) {
            listeners.forEach {
                try {
                    it.onWakeEvent(commandPayload)
                } catch (e: Exception) {
                    Log.e("WakeEventManager", "Listener error", e)
                }
            }
        }

        // Also broadcast for system synchronization
        val intent = Intent("com.example.browser.WAKE_WORD_DETECTED").apply {
            putExtra("COMMAND_PAYLOAD", commandPayload)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
