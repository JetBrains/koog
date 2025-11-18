package com.jetbrains.example.koog.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = NavRoute.StartScreen,
        ) {
            composable<NavRoute.StartScreen> {
                StartScreen(
                    onNavigateToSettings = {
                        navController.navigate(NavRoute.SettingsScreen)
                    },
                    onNavigateToAgentDemo = { demoRoute ->
                        navController.navigate(demoRoute)
                    },
                    viewModel = koin.get()
                )
            }

            composable<NavRoute.SettingsScreen> {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSaveSettings = {
                        navController.popBackStack()
                    },
                    viewModel = koin.get()
                )
            }

            composable<NavRoute.AgentDemoRoute.CalculatorScreen> {
                AgentDemoScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = koin.get { parametersOf("calculator") }
                )
            }

            composable<NavRoute.AgentDemoRoute.WeatherScreen> {
                AgentDemoScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = koin.get { parametersOf("weather") }
                )
            }
        }
    }
}

/**
 * Navigation routes for the app
 */
@Serializable
sealed interface NavRoute {
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
