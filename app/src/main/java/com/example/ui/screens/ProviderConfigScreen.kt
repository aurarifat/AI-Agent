package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AiProviderType
import com.example.ui.AgentUiState
import com.example.ui.AgentViewModel
import com.example.ui.AppScreen
import com.example.ui.components.AnimatedFuturisticButton
import com.example.ui.components.AnimatedOutlinedFuturisticButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    viewModel: AgentViewModel,
    uiState: AgentUiState,
    onNavigate: (AppScreen) -> Unit,
    isOnboarding: Boolean = true
) {
    val settings = uiState.settings

    var selectedProvider by remember(settings.activeProvider) {
        mutableStateOf(settings.activeProvider)
    }

    var geminiKey by remember(settings.geminiApiKey) {
        mutableStateOf(settings.geminiApiKey)
    }
    var geminiModel by remember(settings.geminiModel) {
        mutableStateOf(settings.geminiModel)
    }
    var isGeminiKeyVisible by remember { mutableStateOf(false) }

    var ollamaUrl by remember(settings.ollamaUrl) {
        mutableStateOf(settings.ollamaUrl)
    }
    var ollamaKey by remember(settings.ollamaApiKey) {
        mutableStateOf(settings.ollamaApiKey)
    }
    var ollamaModel by remember(settings.ollamaModel) {
        mutableStateOf(settings.ollamaModel)
    }

    var fallbackEnabled by remember(settings.fallbackEnabled) {
        mutableStateOf(settings.fallbackEnabled)
    }

    val geminiModels = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-flash-latest"
    )

    val ollamaModels = listOf(
        "llama3.2",
        "qwen2.5",
        "mistral",
        "phi3.5",
        "gemma2"
    )

    var geminiDropdownExpanded by remember { mutableStateOf(false) }
    var ollamaDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Header
            Text(
                text = "AI ENGINE CONFIGURATION",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Choose Your AI Brain",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Select whether tasks should be planned via Gemini API or your local Ollama server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Provider Switcher Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Gemini Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedProvider == AiProviderType.GEMINI)
                                MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            selectedProvider = AiProviderType.GEMINI
                            viewModel.updateActiveProvider(AiProviderType.GEMINI)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Gemini",
                            tint = if (selectedProvider == AiProviderType.GEMINI) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gemini API",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedProvider == AiProviderType.GEMINI) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Ollama Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedProvider == AiProviderType.OLLAMA)
                                MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            selectedProvider = AiProviderType.OLLAMA
                            viewModel.updateActiveProvider(AiProviderType.OLLAMA)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = "Ollama",
                            tint = if (selectedProvider == AiProviderType.OLLAMA) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ollama Local",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedProvider == AiProviderType.OLLAMA) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Provider Form
            if (selectedProvider == AiProviderType.GEMINI) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini API Configuration",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // API Key Input
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = {
                                geminiKey = it
                                viewModel.updateGeminiSettings(it, geminiModel)
                            },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("Enter AI Studio API Key...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (isGeminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isGeminiKeyVisible = !isGeminiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isGeminiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Model Selector Dropdown
                        ExposedDropdownMenuBox(
                            expanded = geminiDropdownExpanded,
                            onExpandedChange = { geminiDropdownExpanded = !geminiDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = geminiModel,
                                onValueChange = {
                                    geminiModel = it
                                    viewModel.updateGeminiSettings(geminiKey, it)
                                },
                                readOnly = true,
                                label = { Text("Model Version") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = geminiDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = geminiDropdownExpanded,
                                onDismissRequest = { geminiDropdownExpanded = false }
                            ) {
                                geminiModels.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName) },
                                        onClick = {
                                            geminiModel = modelName
                                            viewModel.updateGeminiSettings(geminiKey, modelName)
                                            geminiDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tip: Keys configured in Secrets panel or entered above are stored securely in on-device encrypted preferences.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Laptop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ollama Local Configuration",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // URL Input
                        OutlinedTextField(
                            value = ollamaUrl,
                            onValueChange = {
                                ollamaUrl = it
                                viewModel.updateOllamaSettings(it, ollamaKey, ollamaModel)
                            },
                            label = { Text("Ollama Host URL") },
                            placeholder = { Text("e.g. http://10.0.2.2:11434") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Model Selector Dropdown
                        ExposedDropdownMenuBox(
                            expanded = ollamaDropdownExpanded,
                            onExpandedChange = { ollamaDropdownExpanded = !ollamaDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = ollamaModel,
                                onValueChange = {
                                    ollamaModel = it
                                    viewModel.updateOllamaSettings(ollamaUrl, ollamaKey, it)
                                },
                                label = { Text("Ollama Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ollamaDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = ollamaDropdownExpanded,
                                onDismissRequest = { ollamaDropdownExpanded = false }
                            ) {
                                ollamaModels.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName) },
                                        onClick = {
                                            ollamaModel = modelName
                                            viewModel.updateOllamaSettings(ollamaUrl, ollamaKey, modelName)
                                            ollamaDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Optional API Key
                        OutlinedTextField(
                            value = ollamaKey,
                            onValueChange = {
                                ollamaKey = it
                                viewModel.updateOllamaSettings(ollamaUrl, it, ollamaModel)
                            },
                            label = { Text("Auth Token / Key (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "For Android Emulator use: http://10.0.2.2:11434. For physical devices on Wi-Fi, use your PC LAN IP (e.g. http://192.168.1.50:11434 with OLLAMA_HOST=0.0.0.0).",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Connection Action Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Test Connection",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ping endpoint & measure latency",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedOutlinedFuturisticButton(
                            onClick = { viewModel.testProviderConnection(selectedProvider) },
                            enabled = !uiState.connectionTestState.isTesting
                        ) {
                            if (uiState.connectionTestState.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Test",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Test")
                            }
                        }
                    }

                    // Test Results
                    uiState.connectionTestState.message?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (uiState.connectionTestState.isSuccess == true)
                                        StatusSuccess.copy(alpha = 0.12f)
                                    else
                                        StatusError.copy(alpha = 0.12f)
                                )
                                .border(
                                    1.dp,
                                    if (uiState.connectionTestState.isSuccess == true) StatusSuccess.copy(alpha = 0.4f)
                                    else StatusError.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.connectionTestState.isSuccess == true) Icons.Default.Check else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (uiState.connectionTestState.isSuccess == true) StatusSuccess else StatusError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (uiState.connectionTestState.isSuccess == true) StatusSuccess else StatusError
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fallback Provider Toggle
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Fallback",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedProvider == AiProviderType.GEMINI)
                                "If Gemini fails, seamlessly try Ollama"
                            else
                                "If Ollama fails, seamlessly try Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = fallbackEnabled,
                        onCheckedChange = {
                            fallbackEnabled = it
                            val targetFallback = if (selectedProvider == AiProviderType.GEMINI) AiProviderType.OLLAMA else AiProviderType.GEMINI
                            viewModel.updateFallbackSettings(it, targetFallback)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Finish Setup Button
            AnimatedFuturisticButton(
                onClick = {
                    if (isOnboarding) {
                        viewModel.completeOnboarding()
                    } else {
                        onNavigate(AppScreen.HOME)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = "Start",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnboarding) "Launch AI Agent" else "Save & Return",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
