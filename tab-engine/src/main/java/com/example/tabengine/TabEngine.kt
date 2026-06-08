package com.example.tabengine

import android.content.Context
import java.util.UUID

data class TabItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "about:blank",
    val title: String = "New Tab",
    val timestamp: Long = System.currentTimeMillis()
)

interface TabEngine {
    fun createTab(url: String, title: String): TabItem
    fun closeTab(tabId: String)
    fun getTabs(): List<TabItem>
}

class TabManager(private val context: Context) : TabEngine {
    private val tabsList = mutableListOf<TabItem>()

    init {
        // Initialize with default home tab
        tabsList.add(TabItem(url = "https://www.google.com", title = "Google"))
    }

    override fun createTab(url: String, title: String): TabItem {
        val tab = TabItem(url = url, title = title)
        tabsList.add(tab)
        return tab
    }

    override fun closeTab(tabId: String) {
        tabsList.removeAll { it.id == tabId }
    }

    override fun getTabs(): List<TabItem> = tabsList.toList()
}

class TabStorage(private val context: Context) {
    fun saveTabs(tabs: List<TabItem>) {
        // Persistent storage logic
    }
    fun loadTabs(): List<TabItem> {
        return emptyList()
    }
}
