package com.example.extensionengine

import org.json.JSONObject

data class PortConnection(
    val channelId: String,
    val name: String,
    val senderId: String,
    val targetId: String,
    var isDisconnected: Boolean = false
) {
    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("channelId", channelId)
            put("name", name)
            put("senderId", senderId)
            put("targetId", targetId)
            put("isDisconnected", isDisconnected)
        }
    }
}
