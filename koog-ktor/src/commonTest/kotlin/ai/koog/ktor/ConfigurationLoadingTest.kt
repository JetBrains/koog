package ai.koog.ktor

import ai.koog.ktor.utils.loadAgentsConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.applicationEnvironment
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DelicateCoroutinesApi
class ConfigurationLoadingTest {

    @Test
    fun testLoadCompleteConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("complete_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenAI configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenAI])

        // Verify Anthropic configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Anthropic])

        // Verify Google configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Google])

        // Verify OpenRouter configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenRouter])

        // Verify Ollama configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Ollama])

        // Verify fallback settings
        assertNotNull(koogConfig.fallbackLLMSettings)
        assertEquals(LLMProvider.Anthropic, koogConfig.fallbackLLMSettings?.fallbackProvider)
        assertEquals(AnthropicModels.Sonnet_3_5, koogConfig.fallbackLLMSettings?.fallbackModel)
    }

    @Test
    fun testLoadDefaultModelConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("default_model_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify no fallback settings
        assertNull(koogConfig.fallbackLLMSettings)
    }

    @Test
    fun testLoadFallbackConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("fallback_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify fallback settings
        assertNotNull(koogConfig.fallbackLLMSettings)
        assertEquals(LLMProvider.Anthropic, koogConfig.fallbackLLMSettings?.fallbackProvider)
        assertEquals(AnthropicModels.Sonnet_3_5, koogConfig.fallbackLLMSettings?.fallbackModel)
    }

    @Test
    fun testLoadOpenAIConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("openai_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenAI configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenAI])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadAnthropicConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("anthropic_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify Anthropic configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Anthropic])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadGoogleConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("google_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify Google configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Google])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadOpenRouterConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("openrouter_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenRouter configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.OpenRouter])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.Ollama])
    }

    @Test
    fun testLoadOllamaConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("ollama_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify Ollama configuration
        assertNotNull(koogConfig.llmConnections[LLMProvider.Ollama])

        // Verify no other providers
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])
        assertNull(koogConfig.llmConnections[LLMProvider.Anthropic])
        assertNull(koogConfig.llmConnections[LLMProvider.Google])
        assertNull(koogConfig.llmConnections[LLMProvider.OpenRouter])
    }

    @Test
    fun testLoadInvalidConfiguration() = runTest {
        val koogConfig = applicationEnvironment {
            config = createConfigFromResource("invalid_config.yaml")
        }.loadAgentsConfig(GlobalScope)

        // Verify OpenAI configuration is not loaded due to missing API key
        assertNull(koogConfig.llmConnections[LLMProvider.OpenAI])

        // Verify Anthropic configuration is loaded despite invalid timeout
        assertNotNull(koogConfig.llmConnections[LLMProvider.Anthropic])

        // Verify fallback settings are not set due to missing model
        assertNull(koogConfig.fallbackLLMSettings)
    }

    // For testing purposes, we'll create a simplified configuration directly
    // In a real implementation, we would use a proper YAML parser
    private fun createConfigFromResource(resourceName: String): ApplicationConfig {
        val config = MapApplicationConfig()

        // Create configurations based on the resource name
        when (resourceName) {
            "complete_config.yaml" -> {
                // OpenAI configuration
                config.put("koog.openai.apikey", "test-openai-api-key")
                config.put("koog.openai.baseUrl", "https://api.openai.com/v1")
                config.put("koog.openai.timeout.requestTimeoutMillis", "60000")
                config.put("koog.openai.timeout.connectTimeoutMillis", "30000")
                config.put("koog.openai.timeout.socketTimeoutMillis", "60000")

                // Anthropic configuration
                config.put("koog.anthropic.apikey", "test-anthropic-api-key")
                config.put("koog.anthropic.baseUrl", "https://api.anthropic.com")
                config.put("koog.anthropic.timeout.requestTimeoutMillis", "60000")
                config.put("koog.anthropic.timeout.connectTimeoutMillis", "30000")
                config.put("koog.anthropic.timeout.socketTimeoutMillis", "60000")

                // Google configuration
                config.put("koog.google.apikey", "test-google-api-key")
                config.put("koog.google.baseUrl", "https://generativelanguage.googleapis.com")
                config.put("koog.google.timeout.requestTimeoutMillis", "60000")
                config.put("koog.google.timeout.connectTimeoutMillis", "30000")
                config.put("koog.google.timeout.socketTimeoutMillis", "60000")

                // OpenRouter configuration
                config.put("koog.openrouter.apikey", "test-openrouter-api-key")
                config.put("koog.openrouter.baseUrl", "https://openrouter.ai/api/v1")
                config.put("koog.openrouter.timeout.requestTimeoutMillis", "60000")
                config.put("koog.openrouter.timeout.connectTimeoutMillis", "30000")
                config.put("koog.openrouter.timeout.socketTimeoutMillis", "60000")

                // Ollama configuration
                config.put("koog.ollama.enable", "true")
                config.put("koog.ollama.baseUrl", "http://localhost:11434")
                config.put("koog.ollama.timeout.requestTimeoutMillis", "60000")
                config.put("koog.ollama.timeout.connectTimeoutMillis", "30000")
                config.put("koog.ollama.timeout.socketTimeoutMillis", "60000")

                // Default LLM configuration
                config.put("koog.llm.default", "openai.chat.gpt4o")

                // Fallback configuration
                config.put("koog.llm.fallback.provider", "anthropic")
                config.put("koog.llm.fallback.model", "sonnet_3_5")
            }

            "default_model_config.yaml" -> {
                // Only default LLM configuration
                config.put("koog.llm.default", "openai.chat.gpt4o")
            }

            "fallback_config.yaml" -> {
                // Only fallback configuration
                config.put("koog.llm.fallback.provider", "anthropic")
                config.put("koog.llm.fallback.model", "sonnet_3_5")
            }

            "openai_config.yaml" -> {
                // OpenAI configuration
                config.put("koog.openai.apikey", "test-openai-api-key")
                config.put("koog.openai.baseUrl", "https://api.openai.com/v1")
                config.put("koog.openai.timeout.requestTimeoutMillis", "60000")
                config.put("koog.openai.timeout.connectTimeoutMillis", "30000")
                config.put("koog.openai.timeout.socketTimeoutMillis", "60000")
            }

            "anthropic_config.yaml" -> {
                // Anthropic configuration
                config.put("koog.anthropic.apikey", "test-anthropic-api-key")
                config.put("koog.anthropic.baseUrl", "https://api.anthropic.com")
                config.put("koog.anthropic.timeout.requestTimeoutMillis", "60000")
                config.put("koog.anthropic.timeout.connectTimeoutMillis", "30000")
                config.put("koog.anthropic.timeout.socketTimeoutMillis", "60000")
            }

            "google_config.yaml" -> {
                // Google configuration
                config.put("koog.google.apikey", "test-google-api-key")
                config.put("koog.google.baseUrl", "https://generativelanguage.googleapis.com")
                config.put("koog.google.timeout.requestTimeoutMillis", "60000")
                config.put("koog.google.timeout.connectTimeoutMillis", "30000")
                config.put("koog.google.timeout.socketTimeoutMillis", "60000")
            }

            "openrouter_config.yaml" -> {
                // OpenRouter configuration
                config.put("koog.openrouter.apikey", "test-openrouter-api-key")
                config.put("koog.openrouter.baseUrl", "https://openrouter.ai/api/v1")
                config.put("koog.openrouter.timeout.requestTimeoutMillis", "60000")
                config.put("koog.openrouter.timeout.connectTimeoutMillis", "30000")
                config.put("koog.openrouter.timeout.socketTimeoutMillis", "60000")
            }

            "ollama_config.yaml" -> {
                // Ollama configuration
                config.put("koog.ollama.enable", "true")
                config.put("koog.ollama.baseUrl", "http://localhost:11434")
                config.put("koog.ollama.timeout.requestTimeoutMillis", "60000")
                config.put("koog.ollama.timeout.connectTimeoutMillis", "30000")
                config.put("koog.ollama.timeout.socketTimeoutMillis", "60000")
            }

            "invalid_config.yaml" -> {
                // Invalid OpenAI configuration (missing API key)
                config.put("koog.openai.baseUrl", "https://api.openai.com/v1")

                // Invalid Anthropic configuration (invalid timeout)
                config.put("koog.anthropic.apikey", "test-anthropic-api-key")
                config.put("koog.anthropic.timeout.requestTimeoutMillis", "invalid-timeout")

                // Invalid default LLM configuration (invalid model identifier)
                config.put("koog.llm.default", "invalid-model-identifier")

                // Invalid fallback configuration (missing model)
                config.put("koog.llm.fallback.provider", "google")
            }

            else -> throw IllegalArgumentException("Resource not found: $resourceName")
        }

        return config
    }
}
