package com.example.extensionengine

import org.json.JSONObject

class StorageManager(private val db: ExtensionDatabase) {

    private val storageDao = db.storageDao()

    suspend fun get(extensionId: String, area: String, keys: Any?): JSONObject {
        val result = JSONObject()
        val allEntities = when (keys) {
            null -> {
                storageDao.getStorageByArea(extensionId, area)
            }
            is String -> {
                storageDao.getStorageByKeys(extensionId, area, listOf(keys))
            }
            is List<*> -> {
                val keysList = keys.filterIsInstance<String>()
                storageDao.getStorageByKeys(extensionId, area, keysList)
            }
            is JSONObject -> {
                val defaultKeys = keys.keys().asSequence().toList()
                val loaded = storageDao.getStorageByKeys(extensionId, area, defaultKeys)
                for (key in defaultKeys) {
                    result.put(key, keys.get(key))
                }
                loaded
            }
            else -> emptyList()
        }

        for (entity in allEntities) {
            val jsonVal = try {
                val raw = entity.valueJson
                if (raw.startsWith("{")) {
                    JSONObject(raw)
                } else if (raw.startsWith("[")) {
                    org.json.JSONArray(raw)
                } else if (raw == "true" || raw == "false") {
                    raw.toBoolean()
                } else if (raw.toDoubleOrNull() != null) {
                    if (raw.contains(".")) raw.toDouble() else raw.toLong()
                } else {
                    raw
                }
            } catch (e: Exception) {
                entity.valueJson
            }
            result.put(entity.key, jsonVal)
        }

        return result
    }

    suspend fun set(extensionId: String, area: String, items: JSONObject) {
        val entities = mutableListOf<StorageEntity>()
        val keys = items.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = items.get(key)
            val valStr = value.toString()
            entities.add(StorageEntity(extensionId, area, key, valStr))
        }
        if (entities.isNotEmpty()) {
            storageDao.insertStorage(entities)
        }
    }

    suspend fun remove(extensionId: String, area: String, keys: List<String>) {
        storageDao.deleteStorageByKeys(extensionId, area, keys)
    }

    suspend fun clear(extensionId: String, area: String) {
        storageDao.clearStorage(extensionId, area)
    }
}
