package com.example.tabengine

import org.junit.Assert.assertEquals
import org.junit.Test

class TabEngineTest {
    @Test
    fun testTabCreation() {
        val tab = TabItem(url = "https://example.com", title = "Example")
        assertEquals("https://example.com", tab.url)
        assertEquals("Example", tab.title)
    }
}
