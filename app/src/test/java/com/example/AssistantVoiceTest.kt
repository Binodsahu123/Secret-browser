package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.browser.IntentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantVoiceTest {

    @Test
    fun testIntentEngineParsing() {
        val testCases = mapOf(
            "hello orion open youtube" to IntentEngine.IntentType.OPEN_YOUTUBE,
            "open google" to IntentEngine.IntentType.OPEN_GOOGLE,
            "hey orion open downloads" to IntentEngine.IntentType.OPEN_DOWNLOADS,
            "open history" to IntentEngine.IntentType.OPEN_HISTORY,
            "open settings" to IntentEngine.IntentType.OPEN_SETTINGS,
            "open new tab" to IntentEngine.IntentType.OPEN_NEW_TAB,
            "create incognito tab" to IntentEngine.IntentType.OPEN_INCOGNITO,
            "close current tab" to IntentEngine.IntentType.CLOSE_CURRENT_TAB,
            "search android news" to IntentEngine.IntentType.SEARCH_ANDROID_NEWS,
            "search ai news" to IntentEngine.IntentType.SEARCH_AI_NEWS,
            "open gmail" to IntentEngine.IntentType.OPEN_GMAIL,
            "open facebook" to IntentEngine.IntentType.OPEN_FACEBOOK,
            "open chatgpt" to IntentEngine.IntentType.OPEN_CHATGPT,
            "search kotlin documentation" to IntentEngine.IntentType.SEARCH_GOOGLE,
            "something unknown" to IntentEngine.IntentType.UNKNOWN
        )

        for ((input, expectedType) in testCases) {
            val parsed = IntentEngine.determineIntent(input)
            assertEquals("Input: '$input' did not map to expected IntentType", expectedType, parsed.type)
        }
    }

    @Test
    fun testWakeWordStripping() {
        // Test that wake word commands get their prefix removed correctly
        val command1 = IntentEngine.determineIntent("hello orion open youtube")
        assertEquals(IntentEngine.IntentType.OPEN_YOUTUBE, command1.type)

        val command2 = IntentEngine.determineIntent("hey orion search android news")
        assertEquals(IntentEngine.IntentType.SEARCH_ANDROID_NEWS, command2.type)
        
        val command3 = IntentEngine.determineIntent("hello orion search for Jetpack Compose")
        assertEquals(IntentEngine.IntentType.SEARCH_GOOGLE, command3.type)
        assertEquals("for jetpack compose", command3.payload)
    }

    @Test
    fun testWakeWordEngineActivationRules() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.browser.WakeWordEngine(context) { _ -> }

        // Test 1: User says: Hello -> Expected: Nothing happens (null returned)
        val test1 = engine.matchWakeWordCommand("Hello")
        assertEquals(null, test1)

        // Test 2: User says: Open YouTube -> Expected: Nothing happens (null returned)
        val test2 = engine.matchWakeWordCommand("Open YouTube")
        assertEquals(null, test2)

        // Test 3: User says: Hello Orion -> Expected: Matches and triggers (returns empty string command)
        val test3 = engine.matchWakeWordCommand("Hello Orion")
        assertNotNull(test3)
        assertEquals("", test3)

        // Test 4: User says: Hello Orion Open YouTube -> Expected: Matches pre-word, returns "Open YouTube" payload
        val test4 = engine.matchWakeWordCommand("Hello Orion Open YouTube")
        assertEquals("Open YouTube", test4)

        // Test 5: User says: Hello Orion Open Facebook -> Expected: Matches, returns "Open Facebook" payload
        val test5 = engine.matchWakeWordCommand("Hello Orion Open Facebook")
        assertEquals("Open Facebook", test5)
    }

    @Test
    fun testVoiceCommandDatabaseResolution() {
        // News Channel Mappings
        assertEquals("https://aajtak.in", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("aaj tak"))
        assertEquals("https://zeenews.india.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("zee news"))
        assertEquals("https://ndtv.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("ndtv"))
        assertEquals("https://republicworld.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("republic tv"))
        assertEquals("https://bhaskar.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("dainik bhaskar"))
        assertEquals("https://bbc.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("bbc news"))

        // Weather Service Mappings
        assertEquals("https://weather.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("weather"))
        assertEquals("https://accuweather.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("accuweather"))
        assertEquals("https://mausam.imd.gov.in", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("mausam"))

        // Popular Portals / Shopping / Search
        assertEquals("https://amazon.in", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("amazon"))
        assertEquals("https://flipkart.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("flipkart"))
        assertEquals("https://chatgpt.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("chatgpt"))
        assertEquals("https://irctc.co.in", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("irctc"))
        assertEquals("https://paytm.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("paytm"))

        // Dynamic Fallback Domain parser (e.g., any word requested as a website should resolve directly)
        assertEquals("https://www.randomcoolsite.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("open randomcoolsite website"))
        assertEquals("https://www.myblog.com", com.example.browser.VoiceCommandDatabase.resolveQueryToUrl("myblog"))
    }
}
