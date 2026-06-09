package com.example.browser

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzePage(
        pageText: String,
        settingsManager: AISettingsManager
    ): String = withContext(Dispatchers.IO) {
        val provider = settingsManager.defaultProvider
        val selectedModel = settingsManager.defaultModel
        val preferredLanguage = settingsManager.preferredLanguage
        val responseLength = settingsManager.responseLength
        
        val apiKey = settingsManager.getApiKey(provider)
        
        if (apiKey.trim().isEmpty() && !provider.equals("Gemini", ignoreCase = true)) {
            return@withContext """
                [MAIN TOPIC]
                API Key Required
                
                [SUMMARY]
                You have selected '$provider', but no API Key was detected in your Settings.
                To activate real-time intelligence for webpage summary and chat:
                1. Go to Browser Settings -> AI Settings.
                2. Select '$provider' as your Provider.
                3. Enter your custom API Key for '$provider'.
                
                Below is a mock demo of how the page content looks:
                ${pageText.take(200)}...
            """.trimIndent()
        }

        val prompt = AIPageAnalyzer.prepareAnalysisPrompt(pageText, preferredLanguage, responseLength)
        val route = AIModelRouter.resolveRoute(provider, selectedModel, settingsManager)

        try {
            val jsonRequest = buildRequestBody(provider, route.resolvedModelName, prompt)
            val requestBuilder = Request.Builder()
                .url(route.endpointUrl)
                .post(jsonRequest.toString().toRequestBody(MEDIA_TYPE_JSON))

            // Apply authorization headers
            if (provider.equals("Gemini", ignoreCase = true)) {
                // If Gemini, either header or query parameter is used. We use header.
                requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
            } else {
                if (route.useBearerToken) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                } else {
                    requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                }
            }

            // Anthropic has specialized headers
            if (provider.equals("Claude", ignoreCase = true)) {
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(bodyString).optString("error", "HTTP ${response.code}")
                    } catch (e: Exception) {
                        try {
                            JSONObject(bodyString).getJSONObject("error").optString("message", "HTTP ${response.code}")
                        } catch (ex: Exception) {
                            "HTTP ${response.code}"
                        }
                    }
                    return@withContext """
                        [MAIN TOPIC]
                        API Request Error
                        
                        [SUMMARY]
                        An error occurred while communicating with '$provider'.
                        Error Code: ${response.code}
                        Details: $errorMsg
                        
                        Please verify your API key is correct and valid for model '${route.resolvedModelName}' in Browser Settings -> AI Settings.
                    """.trimIndent()
                }

                return@withContext parseResponse(provider, bodyString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext """
                [MAIN TOPIC]
                Connection Failed
                
                [SUMMARY]
                Failed to reach the AI engine provider.
                Details: ${e.localizedMessage ?: "Unknown Timeout or Network Offside error"}
                
                Please ensure you have an active internet connection.
            """.trimIndent()
        }
    }

    suspend fun chatSession(
        chatHistory: List<Pair<String, String>>, // list of pair (role to message)
        newMessage: String,
        settingsManager: AISettingsManager
    ): String = withContext(Dispatchers.IO) {
        val provider = settingsManager.defaultProvider
        val selectedModel = settingsManager.defaultModel
        val apiKey = settingsManager.getApiKey(provider)

        if (apiKey.trim().isEmpty() && !provider.equals("Gemini", ignoreCase = true)) {
            return@withContext "API Key missing. Please set your $provider API key in Browser Settings -> AI Settings."
        }

        val route = AIModelRouter.resolveRoute(provider, selectedModel, settingsManager)

        try {
            val jsonRequest = buildChatRequestBody(provider, route.resolvedModelName, chatHistory, newMessage)
            val requestBuilder = Request.Builder()
                .url(route.endpointUrl)
                .post(jsonRequest.toString().toRequestBody(MEDIA_TYPE_JSON))

            if (provider.equals("Gemini", ignoreCase = true)) {
                requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
            } else {
                if (route.useBearerToken) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                } else {
                    requestBuilder.addHeader(route.apiKeyHeaderName, apiKey)
                }
            }

            if (provider.equals("Claude", ignoreCase = true)) {
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Error: HTTP ${response.code} - ${response.message}"
                }
                return@withContext parseResponse(provider, bodyString)
            }
        } catch (e: Exception) {
            return@withContext "Network error: ${e.localizedMessage}"
        }
    }

    private fun buildRequestBody(provider: String, model: String, prompt: String): JSONObject {
        return when (provider) {
            "Gemini" -> {
                val part = JSONObject().put("text", prompt)
                val responseSchema = JSONObject()
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
                // OpenAI / ChatGPT / DeepSeek / Mistral / Qwen / Llama / OpenRouter
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
                // Add the current message
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
                    // OpenAI, ChatGPT, DeepSeek, etc.
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
