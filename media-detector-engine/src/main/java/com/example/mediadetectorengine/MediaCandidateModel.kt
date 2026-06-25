package com.example.mediadetectorengine

import java.io.Serializable

data class MediaCandidateModel(
    val url: String,
    val title: String,
    val type: String, // "video", "audio", "image", "playlist", "document", "archive"
    val mimeType: String,
    val sourcePage: String,
    val quality: String = "Auto",
    val sourceElement: String = "dom", // "video", "source", "audio", "a_anchor", "meta_tag", "performance_entry", "network_sniffer"
    val confidence: Int = 100,
    val supportedState: String = "supported", // "supported", "unsupported", "blocked"
    val supportReason: String = "Direct file candidate",
    val isProtected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
