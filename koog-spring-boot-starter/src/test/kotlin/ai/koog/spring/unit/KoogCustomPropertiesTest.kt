package ai.koog.spring.unit

import ai.koog.spring.KoogAutoConfiguration
import ai.koog.spring.KoogProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.stream.Stream

class KoogCustomPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KoogAutoConfiguration::class.java))

    companion object {
        @JvmStatic
        fun providerTestData(): Stream<Arguments> = Stream.of(
            Arguments.of("anthropic", "test-anthropic-key", "https://test-anthropic.com"),
            Arguments.of("google", "test-google-key", "https://test-google.com"),
            Arguments.of("openai", "test-openai-key", "https://test-openai.com"),
            Arguments.of("openrouter", "test-openrouter-key", "https://test-openrouter.com")
        )
    }

    @ParameterizedTest
    @MethodSource("providerTestData")
    fun `should bind custom provider properties correctly`(
        provider: String,
        expectedApiKey: String,
        expectedBaseUrl: String
    ) {
        contextRunner
            .withPropertyValues(
                "ai.koog.$provider.api-key=$expectedApiKey",
                "ai.koog.$provider.base-url=$expectedBaseUrl"
            )
            .run { context ->
                val properties = context.getBean(KoogProperties::class.java)
                val props = when (provider) {
                    "anthropic" -> properties.anthropicClientProperties
                    "google" -> properties.googleClientProperties
                    "openai" -> properties.openAIClientProperties
                    "openrouter" -> properties.openRouterClientProperties
                    else -> throw IllegalArgumentException("Unknown provider: $provider")
                }
                assertNotNull(props)
                assertEquals(expectedApiKey, props.apiKey)
                assertEquals(expectedBaseUrl, props.baseUrl)
            }
    }

    @Test
    fun `should bind custom ollama properties correctly`() {
        val ollamaUpdatedUrl = "http://test-ollama:11434"
        contextRunner
            .withPropertyValues(
                "ai.koog.ollama.base-url=$ollamaUpdatedUrl"
            )
            .run { context ->
                val properties = context.getBean(KoogProperties::class.java)
                assertNotNull(properties.ollamaClientProperties)
                assertEquals(ollamaUpdatedUrl, properties.ollamaClientProperties.baseUrl)
            }
    }
}