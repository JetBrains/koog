package ai.koog.spring

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIBasedSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanNamesForType
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class KoogAutoConfigurationTest {
    @Test
    fun `should not supply executor beans if no apiKey is provided`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<SingleLLMPromptExecutor>() }
            }
    }

    @Test
    fun `should supply OpenAI executor bean with provided apiKey and default baseUrl`() {
        val configApiKey = "some_api_key"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.openai.api-key=$configApiKey")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<OpenAILLMClient>(llmClient)

                val apiKey = getPrivateFieldValue(llmClient as AbstractOpenAILLMClient<*, *>, "apiKey")
                assertEquals(configApiKey, apiKey)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBasedSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.openai.com", baseUrl)
            }
    }

    @Test
    fun `should supply OpenAI executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.openai.api-key=some_api_key",
                "ai.koog.openai.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient") as OpenAILLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBasedSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply Anthropic executor bean with provided apiKey and default baseUrl`() {
        val configApiKey = "some_api_key"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.anthropic.api-key=$configApiKey")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<AnthropicLLMClient>(llmClient)

                val apiKey = getPrivateFieldValue(llmClient, "apiKey")
                assertEquals(configApiKey, apiKey)

                val settings = getPrivateFieldValue(llmClient, "settings") as AnthropicClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.anthropic.com", baseUrl)
            }
    }

    @Test
    fun `should supply Anthropic executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.anthropic.api-key=some_api_key",
                "ai.koog.anthropic.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient") as AnthropicLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as AnthropicClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply Google executor bean with provided apiKey and default baseUrl`() {
        val configApiKey = "some_api_key"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.google.api-key=$configApiKey")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<GoogleLLMClient>(llmClient)

                val apiKey = getPrivateFieldValue(llmClient, "apiKey")
                assertEquals(configApiKey, apiKey)

                val settings = getPrivateFieldValue(llmClient, "settings") as GoogleClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://generativelanguage.googleapis.com", baseUrl)
            }
    }

    @Test
    fun `should supply Google executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.google.api-key=some_api_key",
                "ai.koog.google.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient") as GoogleLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as GoogleClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply OpenRouter executor bean with provided apiKey and default baseUrl`() {
        val configApiKey = "some_api_key"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.openrouter.api-key=$configApiKey")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<OpenRouterLLMClient>(llmClient)

                val apiKey = getPrivateFieldValue(llmClient as AbstractOpenAILLMClient<*, *>, "apiKey")
                assertEquals(configApiKey, apiKey)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBasedSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://openrouter.ai", baseUrl)
            }
    }

    @Test
    fun `should supply OpenRouter executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.openrouter.api-key=some_api_key",
                "ai.koog.openrouter.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient") as OpenRouterLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBasedSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply DeepSeek executor bean with provided apiKey and default baseUrl`() {
        val configApiKey = "some_api_key"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.deepseek.api-key=$configApiKey")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<DeepSeekLLMClient>(llmClient)

                val apiKey = getPrivateFieldValue(llmClient as AbstractOpenAILLMClient<*, *>, "apiKey")
                assertEquals(configApiKey, apiKey)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBasedSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.deepseek.com", baseUrl)
            }
    }

    @Test
    fun `should supply DeepSeek executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.deepseek.api-key=some_api_key",
                "ai.koog.deepseek.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient") as DeepSeekLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBasedSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply Ollama executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.ollama.base-url=$configBaseUrl")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val llmClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<OllamaClient>(llmClient)

                val baseUrl = getPrivateFieldValue(llmClient, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply multiple executor beans`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.openai.api-key=some_api_key",
                "ai.koog.anthropic.api-key=some_api_key",
                "ai.koog.google.api-key=some_api_key",
                "ai.koog.deepseek.api-key=some_api_key",
            )
            .run { context ->
                val beanNames = context.getBeanNamesForType<SingleLLMPromptExecutor>()
                assertEquals(4, beanNames.size)
                assertTrue("openAIExecutor" in beanNames)
                assertTrue("anthropicExecutor" in beanNames)
                assertTrue("googleExecutor" in beanNames)
                assertTrue("deepSeekExecutor" in beanNames)
            }
    }

    private inline fun <reified T> getPrivateFieldValue(instance: T, fieldName: String): Any? {
        val field = T::class.java.getDeclaredField(fieldName)
        field.trySetAccessible()
        return field.get(instance)
    }
}
