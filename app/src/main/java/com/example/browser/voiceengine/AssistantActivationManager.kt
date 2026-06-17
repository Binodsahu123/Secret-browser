package com.example.browser.voiceengine

import android.content.Context
import android.util.Log

class AssistantActivationManager(
    private val context: Context,
    private val onActivate: (String?) -> Unit
) : WakeEventManager.WakeEventListener {

    fun startListening() {
        WakeEventManager.registerListener(this)
    }

    fun stopListening() {
        WakeEventManager.unregisterListener(this)
    }

    override fun onWakeEvent(commandPayload: String?) {
        Log.i("AssistantActivationManager", "Wake event triggered activation with payload: $commandPayload")
        onActivate(commandPayload)
    }
}
