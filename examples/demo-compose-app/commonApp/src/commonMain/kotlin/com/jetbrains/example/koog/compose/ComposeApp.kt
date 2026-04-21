package com.jetbrains.example.koog.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.jetbrains.example.koog.compose.screens.agentdemo.AgentDemoScreen
import com.jetbrains.example.koog.compose.screens.settings.SettingsScreen
import com.jetbrains.example.koog.compose.screens.start.StartScreen
import com.jetbrains.example.koog.compose.theme.AppTheme
import kotlinx.serialization.Serializable
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/**
 * Main navigation graph for the app
 */

@Composable
fun ComposeApp() = AppTheme {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val koin = getKoin()
        val backStack = mutableStateListOf<NavKey>(NavRoute.StartScreen)
        val appNavigation = AppNavigation(backStack = backStack)
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<NavRoute.StartScreen> {
                    StartScreen(
                        viewModel = koin.get { parametersOf(appNavigation) }
                    )
                }

                entry<NavRoute.SettingsScreen> {
                    SettingsScreen(
                        viewModel = koin.get { parametersOf(appNavigation) }
                    )
                }

                entry<NavRoute.AgentDemoRoute.CalculatorScreen> {
                    AgentDemoScreen(
                        viewModel = koin.get {
                            parametersOf(
                                appNavigation,
                                "calculator",
                            )
                        }
                    )
                }

                entry<NavRoute.AgentDemoRoute.WeatherScreen> {
                    AgentDemoScreen(
                        viewModel = koin.get {
                            parametersOf(
                                appNavigation,
                                "weather",
                            )
                        }
                    )
                }
            }
        )
    }
}

/**
 * Navigation routes for the app
 */
@Serializable
sealed interface NavRoute : NavKey {
    @Serializable
    data object StartScreen : NavRoute

    @Serializable
    data object SettingsScreen : NavRoute

    /**
     * Screens with agent demos
     */
    @Serializable
    sealed interface AgentDemoRoute : NavRoute {
        @Serializable
        data object CalculatorScreen : AgentDemoRoute

        @Serializable
        data object WeatherScreen : AgentDemoRoute
    }
}
