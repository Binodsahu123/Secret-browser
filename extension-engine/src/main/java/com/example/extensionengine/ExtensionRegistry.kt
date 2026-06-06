package com.example.extensionengine

import java.util.concurrent.ConcurrentHashMap

class ExtensionRegistry {
    private val activeExtensions = ConcurrentHashMap<String, ParsedExtension>()

    fun register(extension: ParsedExtension) {
        activeExtensions[extension.id] = extension
    }

    fun unregister(id: String) {
        activeExtensions.remove(id)
    }

    fun getExtension(id: String): ParsedExtension? = activeExtensions[id]

    fun getAllActiveExtensions(): List<ParsedExtension> = activeExtensions.values.toList()

    fun clear() {
        activeExtensions.clear()
    }
}
