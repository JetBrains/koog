package ai.koog.spring.unit

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.KoogAutoConfiguration
import ai.koog.spring.KoogProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.stream.Stream

class KoogAutoConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))

    @Test
    fun `should create all executor beans when all properties are set`() {
        contextRunner
            .withPropertyValues(
                "ai.koog.anthropic.api-key=test-key",
                "ai.koog.google.api-key=test-key",
                "ai.koog.ollama=true",
                "ai.koog.openai.api-key=test-key",
                "ai.koog.openrouter.api-key=test-key"
            )
            .run { context ->
                val anthropicExecutor = context.getBean("anthropicExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(anthropicExecutor)

                val googleExecutor = context.getBean("googleExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(googleExecutor)

                val ollamaExecutor = context.getBean("ollamaExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(ollamaExecutor)

                val openAIExecutor = context.getBean("openAIExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(openAIExecutor)

                val openRouterExecutor = context.getBean("openRouterExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(openRouterExecutor)
            }
    }

    @ParameterizedTest(name = "should not create {0} executor bean when no properties are set")
    @MethodSource("providerTestData")
    fun `should not create executor bean when no properties are set`(
        beanName: String,
        property: String
    ) {
        contextRunner.run { context ->
            assertThrows(NoSuchBeanDefinitionException::class.java) {
                context.getBean(beanName, SingleLLMPromptExecutor::class.java)
            }
        }
    }

    companion object {
        @JvmStatic
        fun providerTestData(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("anthropicExecutor", "ai.koog.anthropic.api-key=test-key"),
                Arguments.of("googleExecutor", "ai.koog.google.api-key=test-key"),
                Arguments.of("ollamaExecutor", "ai.koog.ollama=true"),
                Arguments.of("openAIExecutor", "ai.koog.openai.api-key=test-key"),
                Arguments.of("openRouterExecutor", "ai.koog.openrouter.api-key=test-key")
            )
        }

        @JvmStatic
        fun urlTestData(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("anthropic", "https://api.anthropic.com", "https://custom-api.anthropic.com"),
                Arguments.of("google", "https://generativelanguage.googleapis.com", "https://custom-api.google.com"),
                Arguments.of("openai", "https://api.openai.com", "https://custom-api.openai.com"),
                Arguments.of("openrouter", "https://openrouter.ai", "https://custom-api.openrouter.com")
            )
        }

        fun getBaseUrl(providerName: String, properties: KoogProperties): String = when (providerName) {
            "anthropic" -> properties.anthropicClientProperties.baseUrl
            "google" -> properties.googleClientProperties.baseUrl
            "openai" -> properties.openAIClientProperties.baseUrl
            "openrouter" -> properties.openRouterClientProperties.baseUrl
            else -> throw IllegalArgumentException("Unknown provider: $providerName")
        }
    }

    @ParameterizedTest(name = "should create {0} bean when {0} properties are set")
    @MethodSource("providerTestData")
    fun `should create executor bean when properties are set`(
        beanName: String,
        property: String
    ) {
        contextRunner
            .withPropertyValues(property)
            .run { context ->
                val executor = context.getBean(beanName, SingleLLMPromptExecutor::class.java)
                assertNotNull(executor)
            }
    }

    @Test
    fun `should create multiple executor beans when multiple properties are set`() {
        contextRunner
            .withPropertyValues(
                "ai.koog.anthropic.api-key=test-key",
                "ai.koog.openai.api-key=test-key"
            )
            .run { context ->
                val anthropicExecutor = context.getBean("anthropicExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(anthropicExecutor)

                val openAIExecutor = context.getBean("openAIExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(openAIExecutor)
            }
    }


    @Test
    fun `should create bean with empty API key`() {
        contextRunner
            .withPropertyValues(
                "ai.koog.anthropic.api-key="
            )
            .run { context ->
                val executor = context.getBean("anthropicExecutor", SingleLLMPromptExecutor::class.java)
                assertNotNull(executor)
            }
    }

    @ParameterizedTest(name = "should use default base URL for {0} when not specified")
    @MethodSource("urlTestData")
    fun `should use default base URL when not specified`(
        providerName: String,
        defaultUrl: String,
        customUrl: String
    ) {
        contextRunner
            .withPropertyValues(
                "ai.koog.$providerName.api-key=test-key"
            )
            .run { context ->
                val properties = context.getBean(KoogProperties::class.java)
                val actualUrl = getBaseUrl(providerName, properties)
                assertEquals(defaultUrl, actualUrl)
            }
    }

    @ParameterizedTest(name = "should override default base URL for {0} when specified")
    @MethodSource("urlTestData")
    fun `should override default base URL when specified`(
        providerName: String,
        defaultUrl: String,
        customUrl: String
    ) {
        contextRunner
            .withPropertyValues(
                "ai.koog.$providerName.api-key=test-key",
                "ai.koog.$providerName.base-url=$customUrl"
            )
            .run { context ->
                val properties = context.getBean(KoogProperties::class.java)
                val actualUrl = getBaseUrl(providerName, properties)
                assertEquals(customUrl, actualUrl)
            }
    }

    @Test
    fun `should create multiple beans when multiple providers are configured`() {
        contextRunner
            .withPropertyValues(
                "ai.koog.anthropic.api-key=test-key",
                "ai.koog.google.api-key=test-key",
                "ai.koog.ollama=true",
                "ai.koog.openai.api-key=test-key",
                "ai.koog.openrouter.api-key=test-key"
            )
            .run { context ->
                assertNotNull(context.getBean("anthropicExecutor", SingleLLMPromptExecutor::class.java))
                assertNotNull(context.getBean("googleExecutor", SingleLLMPromptExecutor::class.java))
                assertNotNull(context.getBean("ollamaExecutor", SingleLLMPromptExecutor::class.java))
                assertNotNull(context.getBean("openAIExecutor", SingleLLMPromptExecutor::class.java))
                assertNotNull(context.getBean("openRouterExecutor", SingleLLMPromptExecutor::class.java))
            }
    }
}