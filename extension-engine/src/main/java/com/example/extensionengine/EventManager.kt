package com.example.extensionengine

import org.json.JSONObject

class EventManager(private val messageBus: MessageBus) {

    private val eventListeners = mutableMapOf<String, MutableList<String>>()

    fun addListener(eventName: String, extensionId: String) {
        val list = eventListeners.getOrPut(eventName) { mutableListOf() }
        if (!list.contains(extensionId)) {
            list.add(extensionId)
        }
    }

    fun removeListener(eventName: String, extensionId: String) {
        eventListeners[eventName]?.remove(extensionId)
    }

    /**
     * Publishes a browser lifecycle event (like tabs.onUpdated or webNavigation)
     * down to subscribed background or content scripts.
     */
    fun triggerEvent(eventName: String, params: JSONObject) {
        val registrants = eventListeners[eventName] ?: return
        for (extId in registrants) {
            val message = JSONObject()
            message.put("type", "EVENT_DISPATCH")
            message.put("eventName", eventName)
            message.put("data", params)
            messageBus.broadcastMessage(extId, null, message)
        }
    }
}
