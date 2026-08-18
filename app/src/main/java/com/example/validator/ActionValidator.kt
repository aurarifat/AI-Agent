package com.example.validator

import com.example.model.ActionType
import com.example.model.AgentAction
import com.example.model.AgentTaskPlan
import org.json.JSONArray
import org.json.JSONObject

object ActionValidator {

    private val FORBIDDEN_KEYWORDS = listOf(
        "rm -rf", "su ", "chmod", "sh ", "bash", "reboot", "factory_reset",
        "drop table", "exec", "eval", "bitcoin", "wallet", "extract_credentials"
    )

    fun parseAndValidate(jsonString: String, maxActions: Int = 15): ValidationResult {
        return try {
            val root = JSONObject(jsonString)
            val task = root.optString("task", "Agent Automation Task")
            val summary = root.optString("summary", "")

            val actionsArray: JSONArray = when {
                root.has("actions") -> root.getJSONArray("actions")
                root.has("steps") -> root.getJSONArray("steps")
                else -> return ValidationResult.Error("Missing 'actions' array in plan JSON")
            }

            if (actionsArray.length() == 0) {
                return ValidationResult.Error("No actions proposed by AI agent")
            }

            val sanitizedActions = mutableListOf<AgentAction>()
            var requiresConfirmation = false
            var confirmationPrompt: String? = null

            val count = minOf(actionsArray.length(), maxActions)
            for (i in 0 until count) {
                val actionObj = actionsArray.getJSONObject(i)
                val typeStr = actionObj.optString("type", "")
                val actionType = ActionType.fromString(typeStr)
                    ?: return ValidationResult.Error("Unsupported action type: '$typeStr' at step ${i + 1}")

                val action = parseAction(actionType, actionObj)
                    ?: return ValidationResult.Error("Malformed parameters for action '$typeStr' at step ${i + 1}")

                // Check security forbidden terms in any string parameter
                val allText = "${action.packageName} ${action.url} ${action.text} ${action.name} ${action.target} ${action.message}"
                if (FORBIDDEN_KEYWORDS.any { allText.contains(it, ignoreCase = true) }) {
                    return ValidationResult.Error("Security violation: Dangerous operation detected at step ${i + 1}")
                }

                if (action.type.isSensitive || action.type == ActionType.CALL_CONTACT) {
                    requiresConfirmation = true
                    if (action.type == ActionType.CALL_CONTACT) {
                        confirmationPrompt = "Call ${action.name ?: action.phoneNumber ?: "Contact"}?"
                    } else if (action.type == ActionType.REQUEST_CONFIRMATION) {
                        confirmationPrompt = action.message ?: "Do you wish to proceed with this task?"
                    }
                }

                sanitizedActions.add(action)
            }

            val plan = AgentTaskPlan(task = task, summary = summary, actions = sanitizedActions)
            ValidationResult.Success(
                plan = plan,
                sanitizedActions = sanitizedActions,
                requiresConfirmation = requiresConfirmation,
                confirmationPrompt = confirmationPrompt
            )
        } catch (e: Exception) {
            ValidationResult.Error("Invalid JSON structure: ${e.localizedMessage}")
        }
    }

    private fun parseAction(type: ActionType, obj: JSONObject): AgentAction? {
        return when (type) {
            ActionType.OPEN_APP -> {
                val pkg = obj.optString("packageName", obj.optString("package", "")).trim()
                val app = obj.optString("appName", obj.optString("app", "")).trim()
                if (pkg.isEmpty() && app.isEmpty()) null
                else AgentAction(type = type, packageName = pkg.ifEmpty { null }, appName = app.ifEmpty { null })
            }
            ActionType.OPEN_URL -> {
                val url = obj.optString("url", "").trim()
                if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) null
                else AgentAction(type = type, url = url)
            }
            ActionType.WAIT -> {
                val ms = obj.optLong("milliseconds", obj.optLong("ms", 1500L))
                val clampedMs = ms.coerceIn(100L, 10000L)
                AgentAction(type = type, milliseconds = clampedMs)
            }
            ActionType.CLICK -> {
                val target = obj.optString("target", obj.optString("text", "")).trim()
                if (target.isEmpty()) null
                else AgentAction(type = type, target = target)
            }
            ActionType.LONG_CLICK -> {
                val target = obj.optString("target", obj.optString("text", "")).trim()
                if (target.isEmpty()) null
                else AgentAction(type = type, target = target)
            }
            ActionType.TYPE_TEXT -> {
                val text = obj.optString("text", "").trim()
                val target = obj.optString("target", "").trim().ifEmpty { null }
                if (text.isEmpty()) null
                else AgentAction(type = type, text = text, target = target)
            }
            ActionType.SWIPE -> {
                val dir = obj.optString("direction", "UP").uppercase().trim()
                val validDir = if (dir in listOf("UP", "DOWN", "LEFT", "RIGHT")) dir else "UP"
                AgentAction(type = type, direction = validDir)
            }
            ActionType.SCROLL -> {
                val dir = obj.optString("direction", "FORWARD").uppercase().trim()
                val validDir = if (dir in listOf("FORWARD", "BACKWARD", "UP", "DOWN")) dir else "FORWARD"
                AgentAction(type = type, direction = validDir)
            }
            ActionType.PRESS_BACK -> AgentAction(type = type)
            ActionType.PRESS_HOME -> AgentAction(type = type)
            ActionType.SEARCH -> {
                val text = obj.optString("text", obj.optString("query", "")).trim()
                val app = obj.optString("appName", obj.optString("app", "")).trim().ifEmpty { null }
                if (text.isEmpty()) null
                else AgentAction(type = type, text = text, appName = app)
            }
            ActionType.FIND_CONTACT -> {
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) null
                else AgentAction(type = type, name = name)
            }
            ActionType.REQUEST_CONFIRMATION -> {
                val msg = obj.optString("message", "Please confirm this action").trim()
                val title = obj.optString("title", "Confirm Action").trim()
                AgentAction(type = type, message = msg, title = title)
            }
            ActionType.CALL_CONTACT -> {
                val name = obj.optString("name", "").trim().ifEmpty { null }
                val phone = obj.optString("phoneNumber", obj.optString("phone", "")).trim().ifEmpty { null }
                if (name == null && phone == null) null
                else AgentAction(type = type, name = name, phoneNumber = phone)
            }
            ActionType.STOP_TASK -> AgentAction(type = type)
        }
    }
}
