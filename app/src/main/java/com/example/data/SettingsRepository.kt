package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiProviderType {
    GEMINI,
    OLLAMA
}

enum class ThemeMode {
    DARK,
    AMOLED,
    LIGHT,
    SYSTEM
}

enum class AccentTheme(val label: String) {
    CYAN_NEON("Cyan Neon"),
    CYBER_VIOLET("Cyber Violet"),
    MATRIX_EMERALD("Matrix Emerald"),
    SOLAR_AMBER("Solar Amber")
}

data class AgentSettings(
    val activeProvider: AiProviderType = AiProviderType.GEMINI,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.5-flash",
    val ollamaUrl: String = "http://10.0.2.2:11434",
    val ollamaApiKey: String = "",
    val ollamaModel: String = "llama3.2",
    val fallbackEnabled: Boolean = false,
    val fallbackProvider: AiProviderType = AiProviderType.OLLAMA,
    val confirmSensitiveActions: Boolean = true,
    val maxActionsPerTask: Int = 10,
    val actionTimeoutSeconds: Int = 15,
    val voiceInputEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentTheme: AccentTheme = AccentTheme.CYAN_NEON,
    val isFirstLaunchCompleted: Boolean = false
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_agent_secure_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    private fun loadSettings(): AgentSettings {
        val geminiKey = prefs.getString("gemini_api_key", null)
            ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" }
            ?: ""

        val providerStr = prefs.getString("active_provider", AiProviderType.GEMINI.name)
        val provider = try {
            AiProviderType.valueOf(providerStr ?: AiProviderType.GEMINI.name)
        } catch (_: Exception) {
            AiProviderType.GEMINI
        }

        val themeStr = prefs.getString("theme_mode", ThemeMode.DARK.name)
        val theme = try {
            ThemeMode.valueOf(themeStr ?: ThemeMode.DARK.name)
        } catch (_: Exception) {
            ThemeMode.DARK
        }

        val accentStr = prefs.getString("accent_theme", AccentTheme.CYAN_NEON.name)
        val accent = try {
            AccentTheme.valueOf(accentStr ?: AccentTheme.CYAN_NEON.name)
        } catch (_: Exception) {
            AccentTheme.CYAN_NEON
        }

        val fallbackProviderStr = prefs.getString("fallback_provider", AiProviderType.OLLAMA.name)
        val fallbackProvider = try {
            AiProviderType.valueOf(fallbackProviderStr ?: AiProviderType.OLLAMA.name)
        } catch (_: Exception) {
            AiProviderType.OLLAMA
        }

        return AgentSettings(
            activeProvider = provider,
            geminiApiKey = geminiKey,
            geminiModel = prefs.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash",
            ollamaUrl = prefs.getString("ollama_url", "http://10.0.2.2:11434") ?: "http://10.0.2.2:11434",
            ollamaApiKey = prefs.getString("ollama_api_key", "") ?: "",
            ollamaModel = prefs.getString("ollama_model", "llama3.2") ?: "llama3.2",
            fallbackEnabled = prefs.getBoolean("fallback_enabled", false),
            fallbackProvider = fallbackProvider,
            confirmSensitiveActions = prefs.getBoolean("confirm_sensitive", true),
            maxActionsPerTask = prefs.getInt("max_actions", 10),
            actionTimeoutSeconds = prefs.getInt("action_timeout", 15),
            voiceInputEnabled = prefs.getBoolean("voice_enabled", true),
            themeMode = theme,
            accentTheme = accent,
            isFirstLaunchCompleted = prefs.getBoolean("first_launch_done", false)
        )
    }

    fun updateActiveProvider(provider: AiProviderType) {
        prefs.edit().putString("active_provider", provider.name).apply()
        _settings.value = _settings.value.copy(activeProvider = provider)
    }

    fun updateGeminiConfig(apiKey: String, model: String) {
        prefs.edit()
            .putString("gemini_api_key", apiKey.trim())
            .putString("gemini_model", model.trim())
            .apply()
        _settings.value = _settings.value.copy(
            geminiApiKey = apiKey.trim(),
            geminiModel = model.trim()
        )
    }

    fun updateOllamaConfig(url: String, apiKey: String, model: String) {
        prefs.edit()
            .putString("ollama_url", url.trim())
            .putString("ollama_api_key", apiKey.trim())
            .putString("ollama_model", model.trim())
            .apply()
        _settings.value = _settings.value.copy(
            ollamaUrl = url.trim(),
            ollamaApiKey = apiKey.trim(),
            ollamaModel = model.trim()
        )
    }

    fun updateFallbackSettings(enabled: Boolean, fallbackProvider: AiProviderType) {
        prefs.edit()
            .putBoolean("fallback_enabled", enabled)
            .putString("fallback_provider", fallbackProvider.name)
            .apply()
        _settings.value = _settings.value.copy(
            fallbackEnabled = enabled,
            fallbackProvider = fallbackProvider
        )
    }

    fun updateAgentBehavior(confirmSensitive: Boolean, maxActions: Int, timeoutSeconds: Int) {
        prefs.edit()
            .putBoolean("confirm_sensitive", confirmSensitive)
            .putInt("max_actions", maxActions)
            .putInt("action_timeout", timeoutSeconds)
            .apply()
        _settings.value = _settings.value.copy(
            confirmSensitiveActions = confirmSensitive,
            maxActionsPerTask = maxActions,
            actionTimeoutSeconds = timeoutSeconds
        )
    }

    fun updateVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("voice_enabled", enabled).apply()
        _settings.value = _settings.value.copy(voiceInputEnabled = enabled)
    }

    fun updateTheme(themeMode: ThemeMode, accentTheme: AccentTheme) {
        prefs.edit()
            .putString("theme_mode", themeMode.name)
            .putString("accent_theme", accentTheme.name)
            .apply()
        _settings.value = _settings.value.copy(
            themeMode = themeMode,
            accentTheme = accentTheme
        )
    }

    fun setFirstLaunchCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean("first_launch_done", completed).apply()
        _settings.value = _settings.value.copy(isFirstLaunchCompleted = completed)
    }

    fun clearCredentials() {
        prefs.edit()
            .remove("gemini_api_key")
            .remove("ollama_api_key")
            .apply()
        _settings.value = _settings.value.copy(
            geminiApiKey = "",
            ollamaApiKey = ""
        )
    }
}
