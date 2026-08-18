package com.example.provider

import com.example.model.AgentTaskPlan
import com.example.model.AppInfo
import com.example.model.DeviceContext

interface AiProvider {
    val name: String
    val currentModel: String

    suspend fun planTask(
        command: String,
        availableApps: List<AppInfo>,
        context: DeviceContext
    ): Result<AgentTaskPlan>

    suspend fun testConnection(): Result<String>
}
