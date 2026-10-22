package com.jetbrains.example.koog.compose

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.compose.runtime.Composable
import com.jetbrains.example.koog.compose.agents.calculator.CalculatorAgentProvider
import com.jetbrains.example.koog.compose.agents.common.AgentProvider
import com.jetbrains.example.koog.compose.agents.weather.WeatherAgentProvider
import com.jetbrains.example.koog.compose.screens.agentdemo.AgentDemoViewModel
import com.jetbrains.example.koog.compose.screens.settings.SettingsViewModel
import com.jetbrains.example.koog.compose.screens.start.StartViewModel
import com.jetbrains.example.koog.compose.settings.AppSettings
import com.jetbrains.example.koog.compose.settings.SelectedOption
import org.koin.compose.KoinApplication
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.module

@OptIn(KoinExperimentalAPI::class)
@Composable
fun KoinApp() = KoinApplication(
    configuration = KoinConfiguration {
        modules(
            appPlatformModule,
            module {
                factory<suspend () -> Pair<LLMClient, LLModel>> {
                    {
                        val appSettings: AppSettings = get()
                        val currentSettings = appSettings.getCurrentSettings()
                        val httpClientFactory = KtorKoogHttpClient.Factory()
                        when (currentSettings.selectedOption) {
                            SelectedOption.Ollama -> {
                                val model = LLModel(
                                    provider = LLMProvider.Ollama,
                                    id = "qwen3.5:0.8b",
                                    capabilities =
                                    listOf(
                                        LLMCapability.Schema.JSON.Basic,
                                        LLMCapability.Temperature,
                                        LLMCapability.ToolChoice,
                                        LLMCapability.Tools,
                                        LLMCapability.Vision.Image,
                                    ),
                                    contextLength = 256_000,
                                )
                                val baseUrl = currentSettings.ollamaUrl
                                println("Container baseUrl: $baseUrl")
                                require(baseUrl.isNotEmpty()) { "Ollama baseUrl is not configured." }
                                val client = OllamaClient(httpClientFactory = httpClientFactory, baseUrl = baseUrl)
                                println("Model loading: $model")
                                client.getModelOrNull(model.id, pullIfMissing = true)
                                println("Model is ready: $model")
                                Pair(client, model)
                            }
                            SelectedOption.OpenAI -> {
                                val openAiToken = appSettings.getCurrentSettings().openAiToken
                                require(openAiToken.isNotEmpty()) { "OpenAI token is not configured." }
                                Pair(OpenAILLMClient(apiKey = openAiToken, httpClientFactory = httpClientFactory), OpenAIModels.Chat.GPT4o)
                            }
                            SelectedOption.Anthropic -> {
                                val anthropicToken = appSettings.getCurrentSettings().anthropicToken
                                require(anthropicToken.isNotEmpty()) { "Anthropic token is not configured." }
                                Pair(AnthropicLLMClient(apiKey = anthropicToken, httpClientFactory = httpClientFactory), AnthropicModels.Sonnet_4)
                            }
                            SelectedOption.Gemini -> {
                                val geminiToken = appSettings.getCurrentSettings().geminiToken
                                require(geminiToken.isNotEmpty()) { "Gemini token is not configured." }
                                Pair(GoogleLLMClient(geminiToken, httpClientFactory = httpClientFactory), GoogleModels.Gemini2_5FlashLite)
                            }
                        }
                    }
                }
                single<AgentProvider>(named("calculator")) { CalculatorAgentProvider(provideLLMClient = get()) }
                single<AgentProvider>(named("weather")) { WeatherAgentProvider(provideLLMClient = get()) }
                factory { params ->
                    StartViewModel(
                        navigationCallback = params[0],
                    )
                }
                factory { params ->
                    SettingsViewModel(
                        navigationCallback = params[0],
                        appSettings = get(),
                    )
                }
                factory { params ->
                    val agentProviderName: String = params[1]
                    val agentProvider: AgentProvider = koin.get(named(agentProviderName))
                    AgentDemoViewModel(
                        navigationCallback = params[0],
                        agentProvider = agentProvider
                    )
                }
            }
        )
    }
) {
    ComposeApp()
}

expect val appPlatformModule: Module
