package com.example.browserengine

import android.util.Log

class TabMemoryManager(private val maxActiveTabs: Int = 6) {
    private val tabLruList = mutableListOf<String>()

    fun recordTabAccess(tabId: String) {
        synchronized(tabLruList) {
            tabLruList.remove(tabId)
            tabLruList.add(0, tabId) // Prepend as most recently accessed
        }
    }

    fun getTabsToTrim(activeTabs: List<String>): List<String> {
        synchronized(tabLruList) {
            val eligibleToTrim = tabLruList.filter { it in activeTabs }
            if (eligibleToTrim.size > maxActiveTabs) {
                return eligibleToTrim.subList(maxActiveTabs, eligibleToTrim.size)
            }
            return emptyList()
        }
    }

    fun removeTab(tabId: String) {
        synchronized(tabLruList) {
            tabLruList.remove(tabId)
        }
    }

    fun clearAll() {
        synchronized(tabLruList) {
            tabLruList.clear()
        }
    }
}
