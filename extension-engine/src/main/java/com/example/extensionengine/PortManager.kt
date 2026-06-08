package com.example.extensionengine

import org.json.JSONObject

class PortManager(private val messageBus: MessageBus) {
    val registry = PortRegistry()
    val messenger = PortMessenger(messageBus)

    fun connect(extensionId: String, channelId: String, portName: String, senderId: String) {
        val connection = PortConnection(
            channelId = channelId,
            name = portName,
            senderId = senderId,
            targetId = extensionId
        )
        registry.register(connection)
        messenger.sendConnect(extensionId, channelId, portName, senderId)
    }

    fun postMessage(channelId: String, message: JSONObject) {
        val conn = registry.get(channelId)
        if (conn != null && !conn.isDisconnected) {
            messenger.postMessage(channelId, message)
        }
    }

    fun disconnect(channelId: String) {
        val conn = registry.remove(channelId)
        if (conn != null) {
            conn.isDisconnected = true
            messenger.sendDisconnect(channelId)
        }
    }
}
