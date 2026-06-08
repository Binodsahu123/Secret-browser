package com.example.browserengine

import java.util.concurrent.ConcurrentHashMap

class MemoryCacheEngine<K : Any, V : Any>(private val maxCapacity: Int = 100) {
    private val cache = ConcurrentHashMap<K, V>()
    private val accessOrder = mutableListOf<K>()

    @Synchronized
    fun put(key: K, value: V) {
        if (cache.containsKey(key)) {
            accessOrder.remove(key)
        } else if (cache.size >= maxCapacity) {
            val oldestKey = accessOrder.removeAt(0)
            cache.remove(oldestKey)
        }
        cache[key] = value
        accessOrder.add(key)
    }

    @Synchronized
    fun get(key: K): V? {
        if (!cache.containsKey(key)) return null
        // Promote access
        accessOrder.remove(key)
        accessOrder.add(key)
        return cache[key]
    }

    @Synchronized
    fun remove(key: K): V? {
        accessOrder.remove(key)
        return cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    fun size(): Int = cache.size
}
