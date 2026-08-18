package com.example.model

data class AgentTaskPlan(
    val task: String,
    val summary: String = "",
    val actions: List<AgentAction> = emptyList()
)
