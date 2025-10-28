package com.jetbrains.example.koog.compose

import androidx.compose.runtime.Composable
import com.jetbrains.example.koog.compose.agents.calculator.CalculatorAgentProvider
import com.jetbrains.example.koog.compose.agents.common.AgentProvider
import com.jetbrains.example.koog.compose.agents.weather.WeatherAgentProvider
import com.jetbrains.example.koog.compose.screens.agentdemo.AgentDemoViewModel
import com.jetbrains.example.koog.compose.screens.settings.SettingsViewModel
import com.jetbrains.example.koog.compose.screens.start.StartViewModel
import org.koin.compose.KoinMultiplatformApplication
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.module

@OptIn(KoinExperimentalAPI::class)
@Composable
fun KoinApp() = KoinMultiplatformApplication(
    config = KoinConfiguration {
        modules(
            appPlatformModule,
            module {
                single<AgentProvider>(named("calculator")) { CalculatorAgentProvider() }
                single<AgentProvider>(named("weather")) { WeatherAgentProvider() }
                factory { SettingsViewModel(appSettings = get()) }
                factory { StartViewModel() }
                factory { params ->
                    AgentDemoViewModel(
                        agentProvider = params.get(),
                        appSettings = get()
                    )
                }
            }
        )
    }
) {
    ComposeApp()
}

expect val appPlatformModule: Module
