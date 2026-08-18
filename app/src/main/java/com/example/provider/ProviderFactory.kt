package com.example.provider

import com.example.data.AgentSettings
import com.example.data.AiProviderType

object ProviderFactory {
    fun createProvider(settings: AgentSettings, overrideType: AiProviderType? = null): AiProvider {
        val type = overrideType ?: settings.activeProvider
        return when (type) {
            AiProviderType.GEMINI -> GeminiProvider(
                apiKey = settings.geminiApiKey,
                currentModel = settings.geminiModel
            )
            AiProviderType.OLLAMA -> OllamaProvider(
                hostUrl = settings.ollamaUrl,
                apiKey = settings.ollamaApiKey,
                currentModel = settings.ollamaModel
            )
        }
    }
}
