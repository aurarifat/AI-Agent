package com.example.ui

import com.example.data.AgentSettings
import com.example.data.AiProviderType
import com.example.model.AgentAction
import com.example.model.AgentTaskPlan
import com.example.model.TaskStatus
import com.example.service.TimelineStep
import com.example.service.VoiceState

enum class AppScreen {
    SPLASH,
    WELCOME,
    PERMISSIONS,
    PROVIDER_CONFIG,
    HOME,
    HISTORY,
    SETTINGS
}

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String? = null,
    val model: String? = null,
    val actions: List<AgentAction> = emptyList(),
    val status: TaskStatus = TaskStatus.COMPLETED,
    val durationMs: Long = 0L,
    val error: String? = null
)

data class ConnectionTestState(
    val isTesting: Boolean = false,
    val isSuccess: Boolean? = null,
    val message: String? = null,
    val testedProvider: AiProviderType? = null
)

data class PermissionStatuses(
    val accessibilityGranted: Boolean = false,
    val recordAudioGranted: Boolean = false,
    val readContactsGranted: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val notificationGranted: Boolean = false
)

data class AgentUiState(
    val currentScreen: AppScreen = AppScreen.SPLASH,
    val settings: AgentSettings = AgentSettings(),
    val isExecutingTask: Boolean = false,
    val currentTaskCommand: String = "",
    val currentTimelineSteps: List<TimelineStep> = emptyList(),
    val pendingPlan: AgentTaskPlan? = null,
    val waitingConfirmation: Boolean = false,
    val confirmationPrompt: String = "",
    val chatMessages: List<ChatMessage> = emptyList(),
    val connectionTestState: ConnectionTestState = ConnectionTestState(),
    val permissions: PermissionStatuses = PermissionStatuses(),
    val voiceState: VoiceState = VoiceState.Idle,
    val activeInfoNotice: String? = null,
    val errorMessage: String? = null
)
