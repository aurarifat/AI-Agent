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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val apiKey: String,
    override val currentModel: String = "gemini-3.5-flash"
) : AiProvider {

    override val name: String = "Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun planTask(
        command: String,
        availableApps: List<AppInfo>,
        context: DeviceContext
    ): Result<AgentTaskPlan> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please enter your API key in Settings or Secrets panel.")
            )
        }

        val systemPrompt = AiPromptHelper.buildSystemPrompt(availableApps, context)
        val userPrompt = "Generate the action plan for this Android user command: \"$command\""

        try {
            val rawResponse = callGeminiApi(systemPrompt, userPrompt, currentModel)
            val jsonCandidate = AiPromptHelper.extractJson(rawResponse)

            when (val validation = ActionValidator.parseAndValidate(jsonCandidate)) {
                is ValidationResult.Success -> Result.success(validation.plan)
                is ValidationResult.Error -> {
                    // Attempt 1 safe repair pass
                    val repairPrompt = "The previous output was invalid JSON (${validation.reason}). Fix and return only the corrected valid JSON object conforming to the schema."
                    val repairedRaw = callGeminiApi(systemPrompt, "$userPrompt\n$repairPrompt", currentModel)
                    val repairedJson = AiPromptHelper.extractJson(repairedRaw)
                    when (val repairValidation = ActionValidator.parseAndValidate(repairedJson)) {
                        is ValidationResult.Success -> Result.success(repairValidation.plan)
                        is ValidationResult.Error -> Result.failure(
                            IllegalArgumentException("Plan validation failed: ${repairValidation.reason}")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("API key is empty"))
        }
        val startTime = System.currentTimeMillis()
        try {
            val response = callGeminiApi(
                systemInstruction = "You are an AI diagnostic assistant. Respond only with 'OK'.",
                userPrompt = "Ping test",
                model = currentModel
            )
            val latency = System.currentTimeMillis() - startTime
            Result.success("Connected to Gemini ($currentModel) in ${latency}ms")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGeminiApi(systemInstruction: String, userPrompt: String, model: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            // System instruction
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstruction) })
                })
            })
            // Contents
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })
            // Generation config
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response from Gemini")

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errorObj = JSONObject(responseBody).optJSONObject("error")
                errorObj?.optString("message") ?: "HTTP ${response.code}: $responseBody"
            } catch (_: Exception) {
                "HTTP ${response.code}: $responseBody"
            }
            throw IllegalStateException(errorMsg)
        }

        val jsonResponse = JSONObject(responseBody)
        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("No candidate responses returned by Gemini")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val textPart = parts.getJSONObject(0)
        return textPart.getString("text")
    }
}
