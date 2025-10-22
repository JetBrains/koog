package ai.koog.ktor

import ai.koog.ktor.utils.loadAgentsConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigurationLoadingTest {

    @Test
    fun testEmptyConfiguration() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        install(Koog)
        startApplication()
    }

    @Test
    fun testOllamaConfig() = testApplication {
        environment { config = buildOllamaConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testOpenAI() = testApplication {
        environment { config = buildOpenAIConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testAnthropic() = testApplication {
        environment { config = buildAnthropicConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testGoogle() = testApplication {
        environment { config = buildGoogleConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testMistral() = testApplication {
        environment { config = buildMistralConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testOpenRouter() = testApplication {
        environment { config = buildOpenAIConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testComplete() = testApplication {
        environment { config = buildCompleteConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testDeepSeek() = testApplication {
        environment { config = buildDeepSeekConfig() }
        install(Koog)
        startApplication()
    }

    @Test
    fun testInvalid() {
        val message = assertFailsWith<IllegalArgumentException> {
            testApplication {
                environment { config = buildInvalidConfig() }
                install(Koog)
            }
        }.message
        assertEquals(
            "Found koog.openai but apiKey was missing.",
            message
        )
    }

    @Test
    fun testLoadCompleteConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildCompleteConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenAI configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenAI])

        // Verify Anthropic configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Anthropic])

        // Verify Google configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Google])

        // Verify MistralAI configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.MistralAI])

        // Verify OpenRouter configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenRouter])

        // Verify DeepSeek configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.DeepSeek])

        // Verify Ollama configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Ollama])

        // Verify fallback settings
        assertNotNull(koogConfig.fallbackLLMSettings)
        assertEquals(LLMProvider.Anthropic, koogConfig.fallbackLLMSettings?.fallbackProvider)
        assertEquals(AnthropicModels.Sonnet_3_5, koogConfig.fallbackLLMSettings?.fallbackModel)
    }

    @Test
    fun testLoadEmptyConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = MapApplicationConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify no providers are configured
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])

        // Verify no fallback settings
        assertNull(koogConfig.fallbackLLMSettings)
    }

    @Test
    fun testLoadFallbackConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            this.config = buildFallbackConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify fallback settings
        assertNotNull(koogConfig.fallbackLLMSettings)
        assertEquals(LLMProvider.Anthropic, koogConfig.fallbackLLMSettings?.fallbackProvider)
        assertEquals(AnthropicModels.Sonnet_3_5, koogConfig.fallbackLLMSettings?.fallbackModel)
    }

    @Test
    fun testLoadOpenAIConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildOpenAIConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenAI configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenAI])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadAnthropicConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildAnthropicConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify Anthropic configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Anthropic])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadGoogleConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            this.config = buildGoogleConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify Google configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Google])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadMistralConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildMistralConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify Google configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.MistralAI])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadOpenRouterConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildOpenRouterConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenRouter configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenRouter])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadDeepSeekConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildDeepSeekConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify DeepSeek configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.DeepSeek])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadOllamaConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = buildOllamaConfig()
        }.loadAgentsConfig(GlobalScope)

        // Verify Ollama configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Ollama])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.MistralAI])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.DeepSeek])
    }

    @Test
    fun testLoadInvalidConfiguration() = runTest {
        val message = assertFailsWith(IllegalArgumentException::class) {
            applicationEnvironment {
                config = buildInvalidConfig()
            }.loadAgentsConfig(GlobalScope)
        }.message
        assertEquals(
            "Found koog.openai but apiKey was missing.",
            message
        )
    }

    private fun buildCompleteConfig() =
        buildOpenAIConfig()
            .mergeWith(buildAnthropicConfig())
            .mergeWith(buildGoogleConfig())
            .mergeWith(buildMistralConfig())
            .mergeWith(buildOpenRouterConfig())
            .mergeWith(buildDeepSeekConfig())
            .mergeWith(buildOllamaConfig())
            .mergeWith(buildFallbackConfig())
            .mergeWith(MapApplicationConfig("koog.llm.default" to "openai.chat.gpt4o"))

    private fun buildFallbackConfig() = MapApplicationConfig(
        "koog.llm.fallback.provider" to "anthropic",
        "koog.llm.fallback.model" to "sonnet_3_5"
    )

    private fun buildOpenAIConfig() = MapApplicationConfig(
        "koog.openai.apikey" to "test-openai-api-key",
        "koog.openai.baseUrl" to "https://api.openai.com/v1",
        "koog.openai.timeout.requestTimeoutMillis" to "60000",
        "koog.openai.timeout.connectTimeoutMillis" to "30000",
        "koog.openai.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildAnthropicConfig() = MapApplicationConfig(
        "koog.anthropic.apikey" to "test-anthropic-api-key",
        "koog.anthropic.baseUrl" to "https://api.anthropic.com",
        "koog.anthropic.timeout.requestTimeoutMillis" to "60000",
        "koog.anthropic.timeout.connectTimeoutMillis" to "30000",
        "koog.anthropic.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildGoogleConfig() = MapApplicationConfig(
        "koog.google.apikey" to "test-google-api-key",
        "koog.google.baseUrl" to "https://generativelanguage.googleapis.com",
        "koog.google.timeout.requestTimeoutMillis" to "60000",
        "koog.google.timeout.connectTimeoutMillis" to "30000",
        "koog.google.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildMistralConfig() = MapApplicationConfig(
        "koog.mistral.apikey" to "test-mistralai-api-key",
        "koog.mistral.baseUrl" to "https://api.mistral.ai",
        "koog.mistral.timeout.requestTimeoutMillis" to "60000",
        "koog.mistral.timeout.connectTimeoutMillis" to "30000",
        "koog.mistral.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildOpenRouterConfig() = MapApplicationConfig(
        "koog.openrouter.apikey" to "test-openrouter-api-key",
        "koog.openrouter.baseUrl" to "https://openrouter.ai/api/v1",
        "koog.openrouter.timeout.requestTimeoutMillis" to "60000",
        "koog.openrouter.timeout.connectTimeoutMillis" to "30000",
        "koog.openrouter.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildDeepSeekConfig() = MapApplicationConfig(
        "koog.deepseek.apikey" to "test-deepseek-api-key",
        "koog.deepseek.baseUrl" to "https://api.deepseek.com",
        "koog.deepseek.timeout.requestTimeoutMillis" to "60000",
        "koog.deepseek.timeout.connectTimeoutMillis" to "30000",
        "koog.deepseek.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildOllamaConfig() = MapApplicationConfig(
        "koog.ollama.enable" to "true",
        "koog.ollama.baseUrl" to "http://localhost:11434",
        "koog.ollama.timeout.requestTimeoutMillis" to "60000",
        "koog.ollama.timeout.connectTimeoutMillis" to "30000",
        "koog.ollama.timeout.socketTimeoutMillis" to "60000"
    )

    private fun buildInvalidConfig() = MapApplicationConfig(
        // Missing API key for OpenAI - should not load
        "koog.openai.baseUrl" to "https://api.openai.com/v1",
        // Invalid timeout for Anthropic - should load with defaults
        "koog.anthropic.apikey" to "test-anthropic-api-key",
        "koog.anthropic.timeout.requestTimeoutMillis" to "invalid-timeout",
        // Invalid fallback configuration - missing model
        "koog.llm.fallback.provider" to "google"
    )

    private fun buildTimeoutConfig() = MapApplicationConfig(
        // All providers with custom timeouts (900000ms for request/socket, 60000ms for connect)
        "koog.openai.apikey" to "test-openai-api-key",
        "koog.openai.timeout.requestTimeoutMillis" to "900000",
        "koog.openai.timeout.connectTimeoutMillis" to "60000",
        "koog.openai.timeout.socketTimeoutMillis" to "900000",
        "koog.anthropic.apikey" to "test-anthropic-api-key",
        "koog.anthropic.timeout.requestTimeoutMillis" to "900000",
        "koog.anthropic.timeout.connectTimeoutMillis" to "60000",
        "koog.anthropic.timeout.socketTimeoutMillis" to "900000",
        "koog.google.apikey" to "test-google-api-key",
        "koog.google.timeout.requestTimeoutMillis" to "900000",
        "koog.google.timeout.connectTimeoutMillis" to "60000",
        "koog.google.timeout.socketTimeoutMillis" to "900000",
        "koog.openrouter.apikey" to "test-openrouter-api-key",
        "koog.openrouter.timeout.requestTimeoutMillis" to "900000",
        "koog.openrouter.timeout.connectTimeoutMillis" to "60000",
        "koog.openrouter.timeout.socketTimeoutMillis" to "900000",
        "koog.deepseek.apikey" to "test-deepseek-api-key",
        "koog.deepseek.timeout.requestTimeoutMillis" to "900000",
        "koog.deepseek.timeout.connectTimeoutMillis" to "60000",
        "koog.deepseek.timeout.socketTimeoutMillis" to "900000",
        "koog.ollama.enable" to "true",
        "koog.ollama.timeout.requestTimeoutMillis" to "900000",
        "koog.ollama.timeout.connectTimeoutMillis" to "60000",
        "koog.ollama.timeout.socketTimeoutMillis" to "900000"
    )

    private fun buildMixedTimeoutConfig() = MapApplicationConfig(
        // OpenAI with custom timeout configuration
        "koog.openai.apikey" to "test-openai-api-key",
        "koog.openai.timeout.requestTimeoutMillis" to "900000",
        "koog.openai.timeout.connectTimeoutMillis" to "60000",
        "koog.openai.timeout.socketTimeoutMillis" to "900000",
        // Anthropic without timeout config (should use defaults)
        "koog.anthropic.apikey" to "test-anthropic-api-key",
        // Google without API key but with timeout config (should not load)
        "koog.google.timeout.requestTimeoutMillis" to "900000"
    )
}
