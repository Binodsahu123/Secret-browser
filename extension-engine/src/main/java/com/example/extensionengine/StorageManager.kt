package com.example.extensionengine

import org.json.JSONObject
import org.json.JSONArray

class StorageManager(private val db: ExtensionDatabase) {

    private val storageDao = db.storageDao()

    // Callback used to dispatch chrome.storage.onChanged events downstream.
    // Receives arguments: (extensionId, area, changes)
    var changeListener: ((String, String, JSONObject) -> Unit)? = null

    private fun parseRawValue(raw: String): Any {
        return try {
            if (raw.startsWith("{")) {
                JSONObject(raw)
            } else if (raw.startsWith("[")) {
                JSONArray(raw)
            } else if (raw == "true" || raw == "false") {
                raw.toBoolean()
            } else {
                val d = raw.toDoubleOrNull()
                if (d != null) {
                    if (raw.contains(".")) d else raw.toLong()
                } else {
                    raw
                }
            }
        } catch (e: Exception) {
            raw
        }
    }

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
            val jsonVal = parseRawValue(entity.valueJson)
            result.put(entity.key, jsonVal)
        }

        return result
    }

    suspend fun set(extensionId: String, area: String, items: JSONObject) {
        val keysList = items.keys().asSequence().toList()
        if (keysList.isEmpty()) return

        // 1. Gather all existing items to track oldValue
        val oldEntities = storageDao.getStorageByKeys(extensionId, area, keysList)
        val oldValuesMap = oldEntities.associate { it.key to parseRawValue(it.valueJson) }

        // 2. Perform write
        val entities = mutableListOf<StorageEntity>()
        val changes = JSONObject()

        for (key in keysList) {
            val newValue = items.get(key)
            entities.add(StorageEntity(extensionId, area, key, newValue.toString()))

            val oldValue = oldValuesMap[key]
            val changeObj = JSONObject()
            if (oldValue != null) {
                changeObj.put("oldValue", oldValue)
            }
            changeObj.put("newValue", newValue)
            changes.put(key, changeObj)
        }

        storageDao.insertStorage(entities)

        // 3. Trigger change listeners
        if (changes.length() > 0) {
            changeListener?.invoke(extensionId, area, changes)
        }
    }

    suspend fun remove(extensionId: String, area: String, keys: List<String>) {
        if (keys.isEmpty()) return

        // 1. Gather old values
        val oldEntities = storageDao.getStorageByKeys(extensionId, area, keys)
        val changes = JSONObject()

        for (entity in oldEntities) {
            val oldValue = parseRawValue(entity.valueJson)
            val changeObj = JSONObject()
            changeObj.put("oldValue", oldValue)
            changeObj.put("newValue", JSONObject.NULL)
            changes.put(entity.key, changeObj)
        }

        // 2. Perform deletetion
        storageDao.deleteStorageByKeys(extensionId, area, keys)

        // 3. Trigger change listeners
        if (changes.length() > 0) {
            changeListener?.invoke(extensionId, area, changes)
        }
    }

    suspend fun clear(extensionId: String, area: String) {
        // 1. Gather all existing keys
        val oldEntities = storageDao.getStorageByArea(extensionId, area)
        val changes = JSONObject()

        for (entity in oldEntities) {
            val oldValue = parseRawValue(entity.valueJson)
            val changeObj = JSONObject()
            changeObj.put("oldValue", oldValue)
            changeObj.put("newValue", JSONObject.NULL)
            changes.put(entity.key, changeObj)
        }

        // 2. Perform wipe
        storageDao.clearStorage(extensionId, area)

        // 3. Trigger change listeners
        if (changes.length() > 0) {
            changeListener?.invoke(extensionId, area, changes)
        }
    }
}

