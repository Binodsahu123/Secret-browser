package com.example.browser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AISummaryEngine {
    private const val TAG = "AISummaryEngine"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzePage(
        pageText: String,
        settingsManager: AISettingsManager,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        var currentProvider = settingsManager.defaultProvider
        var attempts = 0
        val maxAttempts = 3
        var lastErrorMessage = ""

        while (attempts < maxAttempts) {
            attempts++
            val selectedModel = if (currentProvider.equals(settingsManager.defaultProvider, ignoreCase = true)) {
                settingsManager.defaultModel
            } else {
                "Default"
            }
            val preferredLanguage = settingsManager.preferredLanguage
            val responseLength = settingsManager.responseLength
            
            val guestModeManager = AIGuestModeManager(context)
            val accountManager = AIAccountManager(context)
            val apiKey = settingsManager.getApiKey(currentProvider)
            
            // Check access clearance (guest permissions or login validation)
            if (apiKey.isEmpty() && 
                !currentProvider.equals("Gemini", ignoreCase = true) && 
                !guestModeManager.isGuestAllowed(currentProvider) && 
                !accountManager.isLoggedIn(currentProvider)
            ) {
                lastErrorMessage = "Credentials or authentication required for provider '$currentProvider'"
                Log.w(TAG, "$lastErrorMessage. Retrying fallback router...")
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }

            val prompt = AIPageAnalyzer.prepareAnalysisPrompt(pageText, preferredLanguage, responseLength)
            val route = AIModelRouter.resolveRoute(currentProvider, selectedModel, settingsManager)

            try {
                val jsonRequest = buildRequestBody(currentProvider, route.resolvedModelName, prompt)
                val requestBuilder = Request.Builder()
                    .url(route.endpointUrl)
                    .post(jsonRequest.toString().toRequestBody(MEDIA_TYPE_JSON))

                if (currentProvider.equals("Gemini", ignoreCase = true)) {
                    requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                } else {
                    if (route.useBearerToken) {
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    } else {
                        requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                    }
                }

                if (currentProvider.equals("Claude", ignoreCase = true)) {
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                }

                val request = requestBuilder.build()
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        lastErrorMessage = "HTTP ${response.code} from $currentProvider: $bodyString"
                        Log.e(TAG, "Dynamic execution failed: $lastErrorMessage")
                        
                        val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                        if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                            currentProvider = next
                            // trigger next iteration in loop
                        } else {
                            return@withContext parseResponse(currentProvider, bodyString)
                        }
                    } else {
                        return@withContext parseResponse(currentProvider, bodyString)
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: "Timeout connecting to $currentProvider"
                Log.e(TAG, "Network exception calling $currentProvider: $lastErrorMessage")
                
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }
        }

        return@withContext """
            [MAIN TOPIC]
            Failover Completed
            
            [SUMMARY]
            We were unable to complete the analysis report because the selected AI provider failed and no fallback alternative was active or configured.
            
            Details: $lastErrorMessage
            
            Please verify your network connectivity or API settings inside the Browser's AI Settings.
        """.trimIndent()
    }

    suspend fun chatSession(
        chatHistory: List<Pair<String, String>>,
        newMessage: String,
        settingsManager: AISettingsManager,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        var currentProvider = settingsManager.defaultProvider
        var attempts = 0
        val maxAttempts = 3
        var lastErrorMessage = ""

        while (attempts < maxAttempts) {
            attempts++
            val selectedModel = if (currentProvider.equals(settingsManager.defaultProvider, ignoreCase = true)) {
                settingsManager.defaultModel
            } else {
                "Default"
            }
            
            val guestModeManager = AIGuestModeManager(context)
            val accountManager = AIAccountManager(context)
            val apiKey = settingsManager.getApiKey(currentProvider)
            
            if (apiKey.isEmpty() && 
                !currentProvider.equals("Gemini", ignoreCase = true) && 
                !guestModeManager.isGuestAllowed(currentProvider) && 
                !accountManager.isLoggedIn(currentProvider)
            ) {
                lastErrorMessage = "Authentication credentials required for provider '$currentProvider'"
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }

            val route = AIModelRouter.resolveRoute(currentProvider, selectedModel, settingsManager)

            try {
                val jsonRequest = buildChatRequestBody(currentProvider, route.resolvedModelName, chatHistory, newMessage)
                val requestBuilder = Request.Builder()
                    .url(route.endpointUrl)
                    .post(jsonRequest.toString().toRequestBody(MEDIA_TYPE_JSON))

                if (currentProvider.equals("Gemini", ignoreCase = true)) {
                    requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                } else {
                    if (route.useBearerToken) {
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    } else {
                        requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                    }
                }

                if (currentProvider.equals("Claude", ignoreCase = true)) {
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        lastErrorMessage = "HTTP ${response.code}: $bodyString"
                        val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                        if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                            currentProvider = next
                            // trigger next loop iteration
                        } else {
                            return@withContext "Error: HTTP ${response.code} from $currentProvider"
                        }
                    } else {
                        return@withContext parseResponse(currentProvider, bodyString)
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: "Timeout or response connection lost"
                val next = AIProviderRouter.findFallbackProvider(currentProvider, context)
                if (next != null && !next.equals(currentProvider, ignoreCase = true)) {
                    currentProvider = next
                    continue
                } else {
                    break
                }
            }
        }

        return@withContext "Failed to fetch response: $lastErrorMessage"
    }

    private fun buildRequestBody(provider: String, model: String, prompt: String): JSONObject {
        return when (provider) {
            "Gemini" -> {
                val part = JSONObject().put("text", prompt)
                val content = JSONObject().put("parts", JSONArray().put(part))
                JSONObject().put("contents", JSONArray().put(content))
            }
            "Claude" -> {
                val msg = JSONObject().put("role", "user").put("content", prompt)
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4000)
                    .put("messages", JSONArray().put(msg))
            }
            else -> {
                val msg = JSONObject().put("role", "user").put("content", prompt)
                JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(msg))
            }
        }
    }

    private fun buildChatRequestBody(
        provider: String,
        model: String,
        history: List<Pair<String, String>>,
        message: String
    ): JSONObject {
        return when (provider) {
            "Gemini" -> {
                val contents = JSONArray()
                history.forEach { (sender, text) ->
                    val role = if (sender == "user") "user" else "model"
                    val contentObj = JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    contents.put(contentObj)
                }
                val currentObj = JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message)))
                contents.put(currentObj)

                JSONObject().put("contents", contents)
            }
            "Claude" -> {
                val messages = JSONArray()
                history.forEach { (sender, text) ->
                    messages.put(JSONObject().put("role", sender).put("content", text))
                }
                messages.put(JSONObject().put("role", "user").put("content", message))
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4000)
                    .put("messages", messages)
            }
            else -> {
                val messages = JSONArray()
                history.forEach { (sender, text) ->
                    messages.put(JSONObject().put("role", sender).put("content", text))
                }
                messages.put(JSONObject().put("role", "user").put("content", message))
                JSONObject()
                    .put("model", model)
                    .put("messages", messages)
            }
        }
    }

    private fun parseResponse(provider: String, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            when (provider) {
                "Gemini" -> {
                    json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
                "Claude" -> {
                    json.getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                }
                else -> {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Response Parsing Error: Unable to read JSON block from provider response.\nRaw Output:\n$responseBody"
        }
    }
}
