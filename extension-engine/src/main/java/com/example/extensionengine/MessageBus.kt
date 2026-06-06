package com.example.extensionengine

import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

interface MessageListener {
    fun onMessageReceived(extensionId: String, senderTabId: String?, message: JSONObject, callbackId: String?)
}

class MessageBus {
    private val listeners = CopyOnWriteArrayList<MessageListener>()

    fun registerListener(listener: MessageListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: MessageListener) {
        listeners.remove(listener)
    }

    /**
     * Propagates a standard chrome.runtime message payload to all active handlers,
     * including background scripts and target tab interfaces.
     */
    fun broadcastMessage(extensionId: String, senderTabId: String?, message: JSONObject, callbackId: String? = null) {
        listeners.forEach { listener ->
            try {
                listener.onMessageReceived(extensionId, senderTabId, message, callbackId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
