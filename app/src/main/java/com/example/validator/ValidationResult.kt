package com.example.validator

import com.example.model.AgentAction
import com.example.model.AgentTaskPlan

sealed class ValidationResult {
    data class Success(
        val plan: AgentTaskPlan,
        val sanitizedActions: List<AgentAction>,
        val requiresConfirmation: Boolean = false,
        val confirmationPrompt: String? = null
    ) : ValidationResult()

    data class Error(val reason: String) : ValidationResult()
}
