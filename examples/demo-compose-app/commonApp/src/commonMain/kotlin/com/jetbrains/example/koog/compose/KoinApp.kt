package com.jetbrains.example.koog.compose

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import androidx.compose.runtime.Composable
import com.jetbrains.example.koog.compose.agents.calculator.CalculatorAgentProvider
import com.jetbrains.example.koog.compose.agents.common.AgentProvider
import com.jetbrains.example.koog.compose.agents.weather.WeatherAgentProvider
import com.jetbrains.example.koog.compose.screens.agentdemo.AgentDemoViewModel
import com.jetbrains.example.koog.compose.screens.settings.SettingsViewModel
import com.jetbrains.example.koog.compose.screens.start.StartViewModel
import com.jetbrains.example.koog.compose.settings.AppSettings
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
                factory<suspend () -> Pair<LLMClient, LLModel>> {
                    {
                        val appSettings: AppSettings = get()
                        val openAiToken = appSettings.getCurrentSettings().openAiToken
                        require(openAiToken.isNotEmpty()) { "OpenAI token is not configured." }
                        Pair(OpenAILLMClient(openAiToken), OpenAIModels.Chat.GPT4o)
                    }
                }
                single<AgentProvider>(named("calculator")) { CalculatorAgentProvider(provideLLMClient = get()) }
                single<AgentProvider>(named("weather")) { WeatherAgentProvider(provideLLMClient = get()) }
                factory { SettingsViewModel(appSettings = get()) }
                factory { StartViewModel() }
                factory { params ->
                    val agentProviderName: String = params.get()
                    val agentProvider: AgentProvider = koin.get(named(agentProviderName))
                    AgentDemoViewModel(agentProvider = agentProvider)
                }
            }
        )
    }
) {
    ComposeApp()
}

expect val appPlatformModule: Module
