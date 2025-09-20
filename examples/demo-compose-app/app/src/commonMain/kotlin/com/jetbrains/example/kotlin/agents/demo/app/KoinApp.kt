package com.jetbrains.example.kotlin.agents.demo.app

import androidx.compose.runtime.Composable
import com.jetbrains.example.kotlin.agents.demo.app.agents.calculator.CalculatorAgentProvider
import com.jetbrains.example.kotlin.agents.demo.app.agents.common.AgentProvider
import com.jetbrains.example.kotlin.agents.demo.app.agents.weather.WeatherAgentProvider
import com.jetbrains.example.kotlin.agents.demo.app.screens.agentdemo.AgentDemoViewModel
import com.jetbrains.example.kotlin.agents.demo.app.screens.settings.SettingsViewModel
import com.jetbrains.example.kotlin.agents.demo.app.screens.start.StartViewModel
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
