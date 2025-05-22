package com.jetbrains.example.kotlin_agents_demo_app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jetbrains.example.kotlin_agents_demo_app.agents.calculator.CalculatorAgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.agents.weather.WeatherAgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.screens.agentdemo.AgentDemoScreen
import com.jetbrains.example.kotlin_agents_demo_app.screens.agentdemo.AgentDemoViewModel
import com.jetbrains.example.kotlin_agents_demo_app.screens.settings.SettingsScreen
import com.jetbrains.example.kotlin_agents_demo_app.screens.start.StartScreen
import com.jetbrains.example.kotlin_agents_demo_app.theme.AppTheme
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
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

/**
 * Main navigation graph for the app
 */
@Composable
fun NavGraph(navController: NavHostController) {
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
                }
            )
        }

        composable<NavRoute.SettingsScreen> {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSettings = {
                    navController.popBackStack()
                }
            )
        }

        composable<NavRoute.AgentDemoRoute.CalculatorScreen> {
            val context = LocalContext.current
            val provider = CalculatorAgentProvider
            val viewModel = viewModel<AgentDemoViewModel>(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return AgentDemoViewModel(
                            application = context.applicationContext as Application,
                            agentProvider = provider
                        ) as T
                    }
                }
            )

            AgentDemoScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable<NavRoute.AgentDemoRoute.WeatherScreen> {
            val context = LocalContext.current
            val provider = WeatherAgentProvider
            val viewModel = viewModel<AgentDemoViewModel>(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return AgentDemoViewModel(
                            application = context.applicationContext as Application,
                            agentProvider = provider
                        ) as T
                    }
                }
            )

            AgentDemoScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
