package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.AgentViewModel
import com.example.ui.AppScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionScreen
import com.example.ui.screens.ProviderConfigScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.AiAgentTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()

            AiAgentTheme(
                themeMode = uiState.settings.themeMode,
                accentTheme = uiState.settings.accentTheme
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = uiState.currentScreen,
                        label = "screen_crossfade"
                    ) { screen ->
                        when (screen) {
                            AppScreen.SPLASH -> SplashScreen(
                                uiState = uiState,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            AppScreen.WELCOME -> WelcomeScreen(
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            AppScreen.PERMISSIONS -> PermissionScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            AppScreen.PROVIDER_CONFIG -> ProviderConfigScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigate = { viewModel.navigateTo(it) },
                                isOnboarding = !uiState.settings.isFirstLaunchCompleted
                            )
                            AppScreen.HOME -> HomeScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            AppScreen.HISTORY -> HistoryScreen(
                                viewModel = viewModel,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
