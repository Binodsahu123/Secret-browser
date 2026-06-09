package com.example.browser

object AIProviderManager {
    val providers = listOf(
        "Gemini",
        "ChatGPT",
        "Claude",
        "DeepSeek",
        "Qwen",
        "Mistral",
        "Llama",
        "OpenRouter"
    )

    fun getModelsForProvider(provider: String): List<String> {
        return when (provider) {
            "Gemini" -> listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-2.5-flash-image")
            "ChatGPT" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
            "Claude" -> listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus")
            "DeepSeek" -> listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
            "Qwen" -> listOf("qwen-turbo", "qwen-plus", "qwen-max")
            "Mistral" -> listOf("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest")
            "Llama" -> listOf("llama-3-70b-instruct", "llama-3-8b-instruct")
            "OpenRouter" -> listOf("auto", "google/gemini-2.5-flash", "anthropic/claude-3.5-sonnet", "meta-llama/llama-3-8b")
            else -> listOf("default")
        }
    }
}
