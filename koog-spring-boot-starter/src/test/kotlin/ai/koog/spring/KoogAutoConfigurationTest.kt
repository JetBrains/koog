package ai.koog.spring

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.spring.prompt.executor.MultiLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.anthropic.AnthropicLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.deepseek.DeepSeekLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.google.GoogleLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.mistralai.MistralAILLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.ollama.OllamaLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.openai.OpenAILLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.openrouter.OpenRouterLLMAutoConfiguration
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

private const val PROVIDERS = """
    openai, ai.koog.prompt.executor.clients.openai.OpenAILLMClient,
    google, ai.koog.prompt.executor.clients.google.GoogleLLMClient,
    mistral, ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient,
    openrouter, ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient,
    deepseek, ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient,
    ollama, ai.koog.prompt.executor.ollama.client.OllamaClient,
"""

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KoogAutoConfigurationTest {
    private val defaultRetryConfig = RetryConfig.DEFAULT

    private fun createApplicationContextRunner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                AnthropicLLMAutoConfiguration::class.java,
                GoogleLLMAutoConfiguration::class.java,
                MistralAILLMAutoConfiguration::class.java,
                DeepSeekLLMAutoConfiguration::class.java,
                OllamaLLMAutoConfiguration::class.java,
                OpenAILLMAutoConfiguration::class.java,
                OpenRouterLLMAutoConfiguration::class.java,
                MultiLLMAutoConfiguration::class.java,
            )
        )

    @Test
    fun `should send OpenAI request with configured baseUrl and authorization header`() {
        val requestPath = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/v1/chat/completions") { exchange ->
                requestPath.set(exchange.requestURI.toString())
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                requestBody.set(exchange.requestBody.reader().readText())

                val response = """
                    {
                      "id": "chatcmpl-spring",
                      "object": "chat.completion",
                      "created": 1716920005,
                      "model": "gpt-4o",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "Spring says hi"
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"total_tokens": 9, "prompt_tokens": 4, "completion_tokens": 5}
                    }
                """.trimIndent().toByteArray()

                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            createApplicationContextRunner()
                .withPropertyValues(
                    "ai.koog.openai.enabled=true",
                    "ai.koog.openai.api-key=some_api_key",
                    "ai.koog.openai.base-url=http://localhost:${server.address.port}",
                    "ai.koog.openai.retry.enabled=false"
                )
                .run { context ->
                    val executor = context.getBean<MultiLLMPromptExecutor>()

                    val responses = runBlocking {
                        executor.execute(
                            prompt = prompt("spring-test") { user("Hello from Spring?") },
                            model = OpenAIModels.Chat.GPT4o
                        )
                    }

                    assertEquals("/v1/chat/completions", requestPath.get())
                    assertEquals("Bearer some_api_key", authorization.get())
                    assertTrue(requestBody.get().contains("Hello from Spring?"))
                    assertEquals("Spring says hi", responses.single().content)
                }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `should not supply executor beans if no api key property is provided`() {
        createApplicationContextRunner()
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClients = getLlmClients(executor)
                assertTrue(llmClients.isEmpty())
            }
    }

    @Test
    fun `should supply OpenAI executor bean with default baseUrl`() {
        val configApiKey = "some_api_key"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.openai.api-key=$configApiKey"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "openai")
                assertInstanceOf<OpenAILLMClient>(llmClient)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.openai.com", baseUrl)
            }
    }

    @Test
    fun `should supply OpenAI executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.openai.api-key=some_api_key",
                "ai.koog.openai.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "openai") as OpenAILLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @ParameterizedTest
    @CsvSource(textBlock = PROVIDERS)
    fun `should supply OpenAI executor bean with retry client and default config`(
        provider: String,
        clazz: Class<LLMClient>
    ) {
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.$provider.enabled=true",
                "ai.koog.$provider.api-key=some_api_key",
                "ai.koog.$provider.retry.enabled=true",
                "ai.koog.$provider.base-url=http://localhost:9876"
            )
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
    @CsvSource(textBlock = PROVIDERS)
    fun `should supply executor bean with retry client and full custom config`(
        provider: String,
        clazz: Class<LLMClient>
    ) {
        val maxAttempts = 5
        val initialDelay = 10
        val maxDelay = 60
        val backoffMultiplier = 5.0
        val jitterFactor = 0.5
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.$provider.enabled=true",
                "ai.koog.$provider.api-key=some_api_key",
                "ai.koog.$provider.base-url=http://localhost:9876",
                "ai.koog.$provider.retry.enabled=true",
                "ai.koog.$provider.retry.max-attempts=$maxAttempts",
                "ai.koog.$provider.retry.initial-delay=$initialDelay",
                "ai.koog.$provider.retry.max-delay=$maxDelay",
                "ai.koog.$provider.retry.backoff-multiplier=$backoffMultiplier",
                "ai.koog.$provider.retry.jitter-factor=$jitterFactor"
            )
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
    @CsvSource(textBlock = PROVIDERS)
    fun `Should not create beans when provider is DISABLED`(
        provider: String,
    ) {
        val maxAttempts = 5
        val initialDelay = 10
        val maxDelay = 60
        val backoffMultiplier = 5.0
        val jitterFactor = 0.5
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.$provider.enabled=false",
                "ai.koog.$provider.api-key=some_api_key",
                "ai.koog.$provider.base-url=http://localhost:9876",
                "ai.koog.$provider.retry.enabled=true",
                "ai.koog.$provider.retry.max-attempts=$maxAttempts",
                "ai.koog.$provider.retry.initial-delay=$initialDelay",
                "ai.koog.$provider.retry.max-delay=$maxDelay",
                "ai.koog.$provider.retry.backoff-multiplier=$backoffMultiplier",
                "ai.koog.$provider.retry.jitter-factor=$jitterFactor"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                assertTrue(getLlmClients(executor).isEmpty())
                assertTrue { context.getBeansOfType(RetryingLLMClient::class.java).isEmpty() }
                assertTrue { context.getBeansOfType(LLMClient::class.java).isEmpty() }
            }
    }

    @ParameterizedTest
    @CsvSource(textBlock = PROVIDERS)
    fun `should supply executor bean with retry client and partial custom config`(
        provider: String,
        clazz: Class<LLMClient>
    ) {
        val maxAttempts = 5
        val initialDelay = 10
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.$provider.enabled=true",
                "ai.koog.$provider.api-key=some_api_key",
                "ai.koog.$provider.retry.enabled=true",
                "ai.koog.$provider.retry.max-attempts=$maxAttempts",
                "ai.koog.$provider.retry.initial-delay=$initialDelay"
            )
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
    fun `should supply Anthropic executor bean with default baseUrl`() {
        val configApiKey = "some_api_key"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.anthropic.api-key=$configApiKey"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "anthropic")
                assertInstanceOf<AnthropicLLMClient>(llmClient)

                val settings = getPrivateFieldValue(llmClient, "settings") as AnthropicClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.anthropic.com", baseUrl)
            }
    }

    @Test
    fun `should supply Anthropic executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AnthropicLLMAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.anthropic.api-key=some_api_key",
                "ai.koog.anthropic.retry.enabled=true"
            )
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
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.anthropic.api-key=some_api_key",
                "ai.koog.anthropic.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "anthropic") as AnthropicLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as AnthropicClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply Google executor bean with default baseUrl`() {
        val configApiKey = "some_api_key"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.google.api-key=$configApiKey"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "google")
                assertInstanceOf<GoogleLLMClient>(llmClient)

                val settings = getPrivateFieldValue(llmClient, "settings") as GoogleClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://generativelanguage.googleapis.com", baseUrl)
            }
    }

    @Test
    fun `should supply Google executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.google.api-key=some_api_key",
                "ai.koog.google.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "google") as GoogleLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as GoogleClientSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply Google executor bean with retry client and default config`() {
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.google.api-key=some_api_key",
                "ai.koog.google.retry.enabled=true"
            )
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
    fun `should supply OpenRouter executor bean with default baseUrl`() {
        val configApiKey = "some_api_key"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.openrouter.enabled=true",
                "ai.koog.openrouter.api-key=$configApiKey"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "openrouter")
                assertInstanceOf<OpenRouterLLMClient>(llmClient)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://openrouter.ai", baseUrl)
            }
    }

    @Test
    fun `should supply OpenRouter executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.openrouter.enabled=true",
                "ai.koog.openrouter.api-key=some_api_key",
                "ai.koog.openrouter.base-url=$configBaseUrl",
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "openrouter") as OpenRouterLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply OpenRouter executor bean with retry client and default config`() {
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.openrouter.enabled=true",
                "ai.koog.openrouter.api-key=some_api_key",
                "ai.koog.openrouter.retry.enabled=true"
            )
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
    fun `should supply DeepSeek executor bean with default baseUrl`() {
        val configApiKey = "some_api_key"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.deepseek.api-key=$configApiKey"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "deepseek")
                assertInstanceOf<DeepSeekLLMClient>(llmClient)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.deepseek.com", baseUrl)
            }
    }

    @Test
    fun `should supply DeepSeek executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        createApplicationContextRunner().withPropertyValues(
            "ai.koog.deepseek.api-key=some_api_key",
            "ai.koog.deepseek.base-url=$configBaseUrl",
        )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "deepseek") as DeepSeekLLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply DeepSeek executor bean with retry client and default config`() {
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.deepseek.api-key=some_api_key",
                "ai.koog.deepseek.retry.enabled=true"
            )
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
    fun `should supply MistralAI executor bean with default baseUrl`() {
        val configApiKey = "some_api_key"
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.mistral.api-key=$configApiKey"
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "mistralai")
                assertInstanceOf<MistralAILLMClient>(llmClient)

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals("https://api.mistral.ai", baseUrl)
            }
    }

    @Test
    fun `should supply MistralAI executor bean with provided baseUrl`() {
        val configBaseUrl = "https://some-url.com"
        createApplicationContextRunner().withPropertyValues(
            "ai.koog.mistral.api-key=some_api_key",
            "ai.koog.mistral.base-url=$configBaseUrl",
        )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClient = getLlmClient(executor, "mistralai") as MistralAILLMClient

                val settings = getPrivateFieldValue(llmClient, "settings") as OpenAIBaseSettings
                val baseUrl = getPrivateFieldValue(settings, "baseUrl")

                assertEquals(configBaseUrl, baseUrl)
            }
    }

    @Test
    fun `should supply MistralAI executor bean with retry client and default config`() {
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.mistral.api-key=some_api_key",
                "ai.koog.mistral.retry.enabled=true"
            )
            .run { context ->
                val executor = context.getBean<SingleLLMPromptExecutor>()
                val retryingClient = getPrivateFieldValue(executor, "llmClient")
                assertInstanceOf<RetryingLLMClient>(retryingClient)

                val config = getPrivateFieldValue(retryingClient, "config")
                assertInstanceOf<RetryConfig>(config)

                val llmClient = getPrivateFieldValue(retryingClient, "delegate")
                assertInstanceOf<MistralAILLMClient>(llmClient)
            }
    }

    @Test
    fun `should send Ollama request with configured baseUrl`() {
        val requestPath = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/api/chat") { exchange ->
                requestPath.set(exchange.requestURI.toString())
                requestBody.set(exchange.requestBody.reader().readText())

                val response = """
                    {
                      "model": "llama3.2:latest",
                      "message": {
                        "role": "assistant",
                        "content": "Ollama says hi"
                      },
                      "done": true,
                      "prompt_eval_count": 4,
                      "eval_count": 5
                    }
                """.trimIndent().toByteArray()

                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            createApplicationContextRunner().withPropertyValues(
                "ai.koog.ollama.enabled=true",
                "ai.koog.ollama.base-url=http://localhost:${server.address.port}",
            )
                .run { context ->
                    val executor = context.getBean<MultiLLMPromptExecutor>()
                    val llmClient = getLlmClient(executor, "ollama")
                    assertInstanceOf<OllamaClient>(llmClient)

                    val responses = runBlocking {
                        executor.execute(
                            prompt = prompt("spring-ollama-test") { user("Hello from Spring?") },
                            model = OllamaModels.Meta.LLAMA_3_2,
                        )
                    }

                    assertEquals("/api/chat", requestPath.get())
                    assertTrue(requestBody.get().contains("Hello from Spring?"))
                    assertEquals("Ollama says hi", responses.single().content)
                }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `should supply Ollama executor bean with retry client and default config`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OllamaLLMAutoConfiguration::class.java))
            .withPropertyValues(
                "ai.koog.ollama.enabled=true",
                "ai.koog.ollama.base-url=https://some-url.com",
                "ai.koog.ollama.retry.enabled=true"
            )
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
        createApplicationContextRunner()
            .withPropertyValues(
                "ai.koog.openai.api-key=some_api_key",
                "ai.koog.anthropic.api-key=some_api_key",
                "ai.koog.google.api-key=some_api_key",
                "ai.koog.mistral.api-key=some_api_key",
                "ai.koog.deepseek.api-key=some_api_key",
                "ai.koog.ollama.enabled=true",
            )
            .run { context ->
                val executor = context.getBean<MultiLLMPromptExecutor>()
                val llmClients = getLlmClients(executor)
                assertEquals(6, llmClients.size)
                assertTrue(llmClients.keys.any { it.id == "openai" })
                assertTrue(llmClients.keys.any { it.id == "anthropic" })
                assertTrue(llmClients.keys.any { it.id == "google" })
                assertTrue(llmClients.keys.any { it.id == "mistralai" })
                assertTrue(llmClients.keys.any { it.id == "deepseek" })
                assertTrue(llmClients.keys.any { it.id == "ollama" })
            }
    }

    private fun getLlmClients(executor: MultiLLMPromptExecutor): Map<LLMProvider, LLMClient> {
        @Suppress("UNCHECKED_CAST")
        return getPrivateFieldValue(executor, "llmClients") as Map<LLMProvider, LLMClient>
    }

    private fun getLlmClient(executor: MultiLLMPromptExecutor, providerId: String): LLMClient {
        return getLlmClients(executor).entries.firstOrNull { it.key.id == providerId }?.value
            ?: error("No client registered for provider $providerId")
    }

    private inline fun <reified T> getPrivateFieldValue(instance: T, fieldName: String): Any? {
        val field = T::class.java.getDeclaredField(fieldName)
        field.trySetAccessible()
        return field.get(instance)
    }
}
