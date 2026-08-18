package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccentTheme
import com.example.data.AiProviderType
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.TaskHistoryEntity
import com.example.data.TaskHistoryRepository
import com.example.data.ThemeMode
import com.example.model.AgentAction
import com.example.model.AgentTaskPlan
import com.example.model.DeviceContext
import com.example.model.TaskStatus
import com.example.provider.ProviderFactory
import com.example.service.AndroidActionExecutor
import com.example.service.ExecutionStepStatus
import com.example.service.PermissionHelper
import com.example.service.TimelineStep
import com.example.service.VoiceRecognitionHelper
import com.example.service.VoiceState
import com.example.validator.ActionValidator
import com.example.validator.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val database = AppDatabase.getInstance(application)
    private val historyRepo = TaskHistoryRepository(database.taskHistoryDao())
    private val actionExecutor = AndroidActionExecutor(application)
    private val voiceHelper = VoiceRecognitionHelper(application)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    val taskHistory = historyRepo.allHistory

    private var activeExecutionJob: Job? = null

    init {
        // Collect settings
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        // Collect voice state
        viewModelScope.launch {
            voiceHelper.voiceState.collect { state ->
                _uiState.update { it.copy(voiceState = state) }
                if (state is VoiceState.Recognized) {
                    // Auto-fill or run voice command
                    submitCommand(state.text)
                    voiceHelper.resetState()
                }
            }
        }

        refreshPermissions()
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                permissions = PermissionStatuses(
                    accessibilityGranted = PermissionHelper.isAccessibilityGranted(context),
                    recordAudioGranted = PermissionHelper.isRecordAudioGranted(context),
                    readContactsGranted = PermissionHelper.isReadContactsGranted(context),
                    callPhoneGranted = PermissionHelper.isCallPhoneGranted(context),
                    notificationGranted = PermissionHelper.isNotificationGranted(context)
                )
            )
        }
    }

    fun completeOnboarding() {
        settingsRepo.setFirstLaunchCompleted(true)
        navigateTo(AppScreen.HOME)
    }

    fun submitCommand(commandText: String) {
        val text = commandText.trim()
        if (text.isBlank() || _uiState.value.isExecutingTask) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = text
        )

        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMessage,
                isExecutingTask = true,
                currentTaskCommand = text,
                errorMessage = null,
                activeInfoNotice = null
            )
        }

        activeExecutionJob?.cancel()
        activeExecutionJob = viewModelScope.launch {
            executeTaskPipeline(text)
        }
    }

    private suspend fun executeTaskPipeline(command: String) {
        val startTime = System.currentTimeMillis()
        val settings = _uiState.value.settings
        val primaryProvider = ProviderFactory.createProvider(settings)

        // Step 1: Initial Planning timeline state
        val initialTimeline = listOf(
            TimelineStep(0, "Understanding command", "Analyzing intent: \"$command\"", ExecutionStepStatus.Running("Processing")),
            TimelineStep(1, "Planning actions", "Consulting ${primaryProvider.name}...", ExecutionStepStatus.Pending)
        )
        _uiState.update { it.copy(currentTimelineSteps = initialTimeline) }

        val availableApps = actionExecutor.getInstalledAppsList()
        val context = DeviceContext(
            isAccessibilityEnabled = _uiState.value.permissions.accessibilityGranted,
            hasContactsPermission = _uiState.value.permissions.readContactsGranted,
            hasPhonePermission = _uiState.value.permissions.callPhoneGranted
        )

        var planResult = primaryProvider.planTask(command, availableApps, context)
        var usedProvider = primaryProvider.name
        var usedModel = primaryProvider.currentModel

        // Step 2: Fallback check if enabled and primary failed
        if (planResult.isFailure && settings.fallbackEnabled) {
            val fallbackProvider = ProviderFactory.createProvider(settings, settings.fallbackProvider)
            _uiState.update {
                it.copy(
                    activeInfoNotice = "Primary provider ${primaryProvider.name} failed. Falling back to ${fallbackProvider.name}."
                )
            }
            planResult = fallbackProvider.planTask(command, availableApps, context)
            if (planResult.isSuccess) {
                usedProvider = fallbackProvider.name
                usedModel = fallbackProvider.currentModel
            }
        }

        if (planResult.isFailure) {
            val errorMsg = planResult.exceptionOrNull()?.message ?: "AI planning failed"
            handleTaskFailure(command, errorMsg, usedProvider, usedModel, startTime)
            return
        }

        val plan = planResult.getOrThrow()

        // Step 3: Validate Plan Actions
        _uiState.update {
            it.copy(
                currentTimelineSteps = listOf(
                    TimelineStep(0, "Understanding command", "Intent understood", ExecutionStepStatus.Success("Done")),
                    TimelineStep(1, "Planning actions", "Generated ${plan.actions.size} actions", ExecutionStepStatus.Success("Planned")),
                    TimelineStep(2, "Validating actions", "Checking safety allowlist", ExecutionStepStatus.Running("Validating"))
                )
            )
        }

        val validation = ActionValidator.parseAndValidate(
            org.json.JSONObject().apply {
                put("task", plan.task)
                put("summary", plan.summary)
                put("actions", org.json.JSONArray().apply {
                    plan.actions.forEach { action ->
                        put(org.json.JSONObject().apply {
                            put("type", action.type.name)
                            action.packageName?.let { put("packageName", it) }
                            action.appName?.let { put("appName", it) }
                            action.url?.let { put("url", it) }
                            action.milliseconds?.let { put("milliseconds", it) }
                            action.target?.let { put("target", it) }
                            action.text?.let { put("text", it) }
                            action.direction?.let { put("direction", it) }
                            action.name?.let { put("name", it) }
                            action.phoneNumber?.let { put("phoneNumber", it) }
                            action.message?.let { put("message", it) }
                        })
                    }
                })
            }.toString(),
            maxActions = settings.maxActionsPerTask
        )

        if (validation is ValidationResult.Error) {
            handleTaskFailure(command, "Validation error: ${validation.reason}", usedProvider, usedModel, startTime)
            return
        }

        val validSuccess = validation as ValidationResult.Success
        val finalPlan = validSuccess.plan

        // Check if sensitive action confirmation is needed
        if (settings.confirmSensitiveActions && validSuccess.requiresConfirmation) {
            _uiState.update {
                it.copy(
                    waitingConfirmation = true,
                    confirmationPrompt = validSuccess.confirmationPrompt ?: "Do you confirm executing this sensitive action?",
                    pendingPlan = finalPlan
                )
            }
            return // Pauses here; resumeExecutionAfterConfirmation() will continue
        }

        // Execute actions
        proceedWithExecution(finalPlan, command, usedProvider, usedModel, startTime)
    }

    fun confirmPendingAction() {
        val plan = _uiState.value.pendingPlan ?: return
        val command = _uiState.value.currentTaskCommand
        val settings = _uiState.value.settings
        val provider = settings.activeProvider.name
        val model = if (settings.activeProvider == AiProviderType.GEMINI) settings.geminiModel else settings.ollamaModel
        val startTime = System.currentTimeMillis()

        _uiState.update {
            it.copy(
                waitingConfirmation = false,
                confirmationPrompt = "",
                pendingPlan = null
            )
        }

        activeExecutionJob?.cancel()
        activeExecutionJob = viewModelScope.launch {
            proceedWithExecution(plan, command, provider, model, startTime)
        }
    }

    fun dismissPendingAction() {
        val command = _uiState.value.currentTaskCommand
        _uiState.update {
            it.copy(
                waitingConfirmation = false,
                confirmationPrompt = "",
                pendingPlan = null,
                isExecutingTask = false
            )
        }
        stopTask()
    }

    private suspend fun proceedWithExecution(
        plan: AgentTaskPlan,
        command: String,
        provider: String,
        model: String,
        startTime: Long
    ) {
        val execResult = actionExecutor.executePlan(plan) { updatedTimeline ->
            _uiState.update { it.copy(currentTimelineSteps = updatedTimeline) }
        }

        val duration = System.currentTimeMillis() - startTime

        if (execResult.isSuccess) {
            val responseMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = "✓ ${execResult.getOrDefault("Task completed successfully")}",
                provider = provider,
                model = model,
                actions = plan.actions,
                status = TaskStatus.COMPLETED,
                durationMs = duration
            )

            // Save to room db
            historyRepo.insertTask(
                TaskHistoryEntity(
                    command = command,
                    provider = provider,
                    model = model,
                    durationMs = duration,
                    status = TaskStatus.COMPLETED.name,
                    actionCount = plan.actions.size,
                    summary = plan.summary.ifEmpty { "Executed ${plan.actions.size} actions" },
                    actionsSummary = plan.actions.joinToString("\n") { "• ${it.toReadableDescription()}" }
                )
            )

            _uiState.update {
                it.copy(
                    isExecutingTask = false,
                    chatMessages = it.chatMessages + responseMessage
                )
            }
        } else {
            val errorMsg = execResult.exceptionOrNull()?.message ?: "Execution failed"
            handleTaskFailure(command, errorMsg, provider, model, startTime, plan.actions)
        }
    }

    private suspend fun handleTaskFailure(
        command: String,
        errorMessage: String,
        provider: String,
        model: String,
        startTime: Long,
        actions: List<AgentAction> = emptyList()
    ) {
        val duration = System.currentTimeMillis() - startTime
        val isCancelled = errorMessage.contains("cancelled", ignoreCase = true) || errorMessage.contains("stopped", ignoreCase = true)
        val status = if (isCancelled) TaskStatus.CANCELLED else TaskStatus.FAILED

        val responseMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = if (isCancelled) "Task stopped by user." else "Task could not be completed: $errorMessage",
            provider = provider,
            model = model,
            actions = actions,
            status = status,
            durationMs = duration,
            error = errorMessage
        )

        historyRepo.insertTask(
            TaskHistoryEntity(
                command = command,
                provider = provider,
                model = model,
                durationMs = duration,
                status = status.name,
                actionCount = actions.size,
                summary = "Failed: $errorMessage",
                actionsSummary = actions.joinToString("\n") { "• ${it.toReadableDescription()}" },
                errorMessage = errorMessage
            )
        )

        _uiState.update {
            it.copy(
                isExecutingTask = false,
                errorMessage = if (!isCancelled) errorMessage else null,
                chatMessages = it.chatMessages + responseMessage
            )
        }
    }

    fun stopTask() {
        actionExecutor.cancel()
        activeExecutionJob?.cancel()
        _uiState.update {
            it.copy(
                isExecutingTask = false,
                waitingConfirmation = false,
                pendingPlan = null
            )
        }
    }

    fun startVoiceInput() {
        refreshPermissions()
        if (!_uiState.value.permissions.recordAudioGranted) {
            _uiState.update { it.copy(errorMessage = "Microphone permission is required for voice input") }
            return
        }
        voiceHelper.startListening()
    }

    fun stopVoiceInput() {
        voiceHelper.stopListening()
    }

    fun testProviderConnection(providerType: AiProviderType? = null) {
        val targetProviderType = providerType ?: _uiState.value.settings.activeProvider
        _uiState.update {
            it.copy(
                connectionTestState = ConnectionTestState(
                    isTesting = true,
                    testedProvider = targetProviderType
                )
            )
        }

        viewModelScope.launch {
            val provider = ProviderFactory.createProvider(_uiState.value.settings, targetProviderType)
            val result = provider.testConnection()
            _uiState.update {
                it.copy(
                    connectionTestState = ConnectionTestState(
                        isTesting = false,
                        isSuccess = result.isSuccess,
                        message = result.getOrElse { err -> err.message ?: "Connection failed" },
                        testedProvider = targetProviderType
                    )
                )
            }
        }
    }

    fun updateActiveProvider(provider: AiProviderType) {
        settingsRepo.updateActiveProvider(provider)
    }

    fun updateGeminiSettings(apiKey: String, model: String) {
        settingsRepo.updateGeminiConfig(apiKey, model)
    }

    fun updateOllamaSettings(url: String, apiKey: String, model: String) {
        settingsRepo.updateOllamaConfig(url, apiKey, model)
    }

    fun updateFallbackSettings(enabled: Boolean, fallbackProvider: AiProviderType) {
        settingsRepo.updateFallbackSettings(enabled, fallbackProvider)
    }

    fun updateAgentBehavior(confirmSensitive: Boolean, maxActions: Int, timeoutSeconds: Int) {
        settingsRepo.updateAgentBehavior(confirmSensitive, maxActions, timeoutSeconds)
    }

    fun updateVoiceEnabled(enabled: Boolean) {
        settingsRepo.updateVoiceEnabled(enabled)
    }

    fun updateTheme(themeMode: ThemeMode, accentTheme: AccentTheme) {
        settingsRepo.updateTheme(themeMode, accentTheme)
    }

    fun clearCredentials() {
        settingsRepo.clearCredentials()
        _uiState.update { it.copy(activeInfoNotice = "API Credentials cleared securely.") }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearAll()
            _uiState.update {
                it.copy(
                    chatMessages = emptyList(),
                    activeInfoNotice = "Task history cleared."
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(activeInfoNotice = null) }
    }
}
