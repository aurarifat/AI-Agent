package com.example.provider

import com.example.model.AgentTaskPlan
import com.example.model.AppInfo
import com.example.model.DeviceContext
import com.example.validator.ActionValidator
import com.example.validator.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OllamaProvider(
    private val hostUrl: String = "http://10.0.2.2:11434",
    private val apiKey: String = "",
    override val currentModel: String = "llama3.2"
) : AiProvider {

    override val name: String = "Ollama"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun normalizeUrl(url: String): String {
        var clean = url.trim().removeSuffix("/")
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean
    }

    override suspend fun planTask(
        command: String,
        availableApps: List<AppInfo>,
        context: DeviceContext
    ): Result<AgentTaskPlan> = withContext(Dispatchers.IO) {
        val base = normalizeUrl(hostUrl)
        val systemPrompt = AiPromptHelper.buildSystemPrompt(availableApps, context)
        val userPrompt = "Generate the action plan for this Android user command: \"$command\""

        try {
            val rawResponse = callOllamaGenerate(base, systemPrompt, userPrompt, currentModel)
            val jsonCandidate = AiPromptHelper.extractJson(rawResponse)

            when (val validation = ActionValidator.parseAndValidate(jsonCandidate)) {
                is ValidationResult.Success -> Result.success(validation.plan)
                is ValidationResult.Error -> {
                    // One safe repair pass
                    val repairPrompt = "The previous output was invalid (${validation.reason}). Output ONLY a valid JSON object matching the automation schema for \"$command\"."
                    val repairedRaw = callOllamaGenerate(base, systemPrompt, repairPrompt, currentModel)
                    val repairedJson = AiPromptHelper.extractJson(repairedRaw)
                    when (val repairValidation = ActionValidator.parseAndValidate(repairedJson)) {
                        is ValidationResult.Success -> Result.success(repairValidation.plan)
                        is ValidationResult.Error -> Result.failure(
                            IllegalArgumentException("Ollama plan validation failed: ${repairValidation.reason}")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val base = normalizeUrl(hostUrl)
        val startTime = System.currentTimeMillis()
        try {
            val tagsUrl = "$base/api/tags"
            val reqBuilder = Request.Builder().url(tagsUrl).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                val models = json.optJSONArray("models")
                val count = models?.length() ?: 0
                Result.success("Connected to Ollama ($count models available) in ${latency}ms")
            } else {
                Result.failure(IllegalStateException("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not connect to Ollama at $base: ${e.localizedMessage}", e))
        }
    }

    private fun callOllamaGenerate(baseUrl: String, system: String, prompt: String, model: String): String {
        val generateUrl = "$baseUrl/api/generate"
        val jsonPayload = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("system", system)
            put("stream", false)
            put("format", "json")
            put("options", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder()
            .url(generateUrl)
            .post(body)

        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(reqBuilder.build()).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response from Ollama")

        if (!response.isSuccessful) {
            throw IllegalStateException("Ollama error HTTP ${response.code}: $responseBody")
        }

        val json = JSONObject(responseBody)
        return json.optString("response", "")
    }
}
