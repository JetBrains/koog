package ai.koog.spring

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBasedSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanNamesForType
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.time.Duration.Companion.seconds

private const val PROVIDERS = """
    openai, ai.koog.prompt.executor.clients.openai.OpenAILLMClient,
    google, ai.koog.prompt.executor.clients.google.GoogleLLMClient,
    openrouter, ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient,
    deepseek, ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient,
    ollama, ai.koog.prompt.executor.ollama.client.OllamaClient,
"""

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KoogAutoConfigurationTest {
    private val defaultRetryConfig = RetryConfig()

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

    @ParameterizedTest
    @CsvSource(PROVIDERS)
    fun `should supply OpenAI executor bean with retry client and default config`(
        provider: String,
        clazz: Class<LLMClient>
    ) {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.$provider.api-key=some_api_key")
            .withPropertyValues("ai.koog.$provider.retry.enabled=true")
            .withPropertyValues("ai.koog.$provider.base-url=http://localhost:9876")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config") as RetryConfig
                assertEquals(defaultRetryConfig.maxAttempts, config.maxAttempts)
                assertEquals(defaultRetryConfig.initialDelay, config.initialDelay)
                assertEquals(defaultRetryConfig.maxDelay, config.maxDelay)
                assertEquals(defaultRetryConfig.backoffMultiplier, config.backoffMultiplier)
                assertEquals(defaultRetryConfig.jitterFactor, config.jitterFactor)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertEquals(clazz, llmClient!!.javaClass)
            }
    }

    @ParameterizedTest
    @CsvSource(PROVIDERS)
    fun `should supply executor bean with retry client and full custom config`(
        provider: String,
        clazz: Class<LLMClient>
    ) {
        val maxAttempts = 5
        val initialDelay = 10
        val maxDelay = 60
        val backoffMultiplier = 5.0
        val jitterFactor = 0.5
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.$provider.api-key=some_api_key")
            .withPropertyValues("ai.koog.$provider.base-url=http://localhost:9876")
            .withPropertyValues("ai.koog.$provider.retry.enabled=true")
            .withPropertyValues("ai.koog.$provider.retry.max-attempts=$maxAttempts")
            .withPropertyValues("ai.koog.$provider.retry.initial-delay=$initialDelay")
            .withPropertyValues("ai.koog.$provider.retry.max-delay=$maxDelay")
            .withPropertyValues("ai.koog.$provider.retry.backoff-multiplier=$backoffMultiplier")
            .withPropertyValues("ai.koog.$provider.retry.jitter-factor=$jitterFactor")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config") as RetryConfig
                assertEquals(maxAttempts, config.maxAttempts)
                assertEquals(initialDelay.seconds, config.initialDelay)
                assertEquals(maxDelay.seconds, config.maxDelay)
                assertEquals(backoffMultiplier, config.backoffMultiplier)
                assertEquals(jitterFactor, config.jitterFactor)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertEquals(clazz, llmClient!!.javaClass)
            }
    }

    @ParameterizedTest
    @CsvSource(PROVIDERS)
    fun `should supply executor bean with retry client and partial custom config`(
        provider: String,
        clazz: Class<LLMClient>
    ) {
        val maxAttempts = 5
        val initialDelay = 10
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.$provider.api-key=some_api_key")
            .withPropertyValues("ai.koog.$provider.retry.enabled=true")
            .withPropertyValues("ai.koog.$provider.retry.max-attempts=$maxAttempts")
            .withPropertyValues("ai.koog.$provider.retry.initial-delay=$initialDelay")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config") as RetryConfig
                assertEquals(maxAttempts, config.maxAttempts)
                assertEquals(initialDelay.seconds, config.initialDelay)
                assertEquals(defaultRetryConfig.maxDelay, config.maxDelay)
                assertEquals(defaultRetryConfig.backoffMultiplier, config.backoffMultiplier)
                assertEquals(defaultRetryConfig.jitterFactor, config.jitterFactor)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertEquals(clazz, llmClient!!.javaClass)
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
    fun `should supply Anthropic executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.anthropic.api-key=some_api_key")
            .withPropertyValues("ai.koog.anthropic.retry.enabled=true")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config")
                assertInstanceOf<RetryConfig>(config)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertInstanceOf<AnthropicLLMClient>(llmClient)
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
    fun `should supply Google executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.google.api-key=some_api_key")
            .withPropertyValues("ai.koog.google.retry.enabled=true")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config")
                assertInstanceOf<RetryConfig>(config)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertInstanceOf<GoogleLLMClient>(llmClient)
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
    fun `should supply OpenRouter executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.openrouter.api-key=some_api_key")
            .withPropertyValues("ai.koog.openrouter.retry.enabled=true")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config")
                assertInstanceOf<RetryConfig>(config)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertInstanceOf<OpenRouterLLMClient>(llmClient)
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
    fun `should supply DeepSeek executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.deepseek.api-key=some_api_key")
            .withPropertyValues("ai.koog.deepseek.retry.enabled=true")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config")
                assertInstanceOf<RetryConfig>(config)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertInstanceOf<DeepSeekLLMClient>(llmClient)
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
    fun `should supply Ollama executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))
            .withPropertyValues("ai.koog.ollama.base-url=https://some-url.com")
            .withPropertyValues("ai.koog.ollama.retry.enabled=true")
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config")
                assertInstanceOf<RetryConfig>(config)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertInstanceOf<OllamaClient>(llmClient)
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
                "ai.koog.ollama.base-url=http://localhist:8765",
            )
            .run { context ->
                val beanNames = context.getBeanNamesForType<SingleLLMPromptExecutor>()
                assertEquals(5, beanNames.size)
                assertTrue("openAIExecutor" in beanNames)
                assertTrue("anthropicExecutor" in beanNames)
                assertTrue("googleExecutor" in beanNames)
                assertTrue("deepSeekExecutor" in beanNames)
                assertTrue("ollamaExecutor" in beanNames)
            }
    }

    private inline fun <reified T> getPrivateFieldValue(instance: T, fieldName: String): Any? {
        val field = T::class.java.getDeclaredField(fieldName)
        field.trySetAccessible()
        return field.get(instance)
    }
}
