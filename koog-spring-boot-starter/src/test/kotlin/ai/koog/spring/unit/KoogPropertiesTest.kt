package ai.koog.spring.unit

import ai.koog.spring.KoogProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.util.stream.Stream
import kotlin.test.Test

const val ANTHROPIC_KEY = "test-anthropic-key"
const val ANTHROPIC_URL = "https://test-anthropic.com"
const val GOOGLE_KEY = "test-google-key"
const val GOOGLE_URL = "https://test-google.com"
const val OLLAMA_URL = "http://localhost:11434"
const val OPENAI_KEY = "test-openai-key"
const val OPENAI_URL = "https://test-openai.com"
const val OPENROUTER_KEY = "test-openrouter-key"
const val OPENROUTER_URL = "https://test-openrouter.com"

@SpringBootTest(classes = [KoogPropertiesTest.TestConfig::class])
@TestPropertySource(
    properties = [
        "ai.koog.anthropicClientProperties.apiKey=$ANTHROPIC_KEY",
        "ai.koog.anthropicClientProperties.baseUrl=$ANTHROPIC_URL",
        "ai.koog.googleClientProperties.apiKey=$GOOGLE_KEY",
        "ai.koog.googleClientProperties.baseUrl=$GOOGLE_URL",
        "ai.koog.ollamaClientProperties.baseUrl=$OLLAMA_URL",
        "ai.koog.openAIClientProperties.apiKey=$OPENAI_KEY",
        "ai.koog.openAIClientProperties.baseUrl=$OPENAI_URL",
        "ai.koog.openRouterClientProperties.apiKey=$OPENROUTER_KEY",
        "ai.koog.openRouterClientProperties.baseUrl=$OPENROUTER_URL"
    ]
)
class KoogPropertiesTest {

    @EnableConfigurationProperties(KoogProperties::class)
    class TestConfig

    @Autowired
    private lateinit var properties: KoogProperties

    companion object {
        @JvmStatic
        fun providerTestData(): Stream<Arguments> = Stream.of(
            Arguments.of("anthropic", ANTHROPIC_KEY, ANTHROPIC_URL),
            Arguments.of("google", GOOGLE_KEY, GOOGLE_URL),
            Arguments.of("openai", OPENAI_KEY, OPENAI_URL),
            Arguments.of("openrouter", OPENROUTER_KEY, OPENROUTER_URL)
        )

        @JvmStatic
        fun defaultValuesTestData(): Stream<Arguments> = Stream.of(
            Arguments.of("anthropic", arrayOf("", "https://api.anthropic.com")),
            Arguments.of("google", arrayOf("", "https://generativelanguage.googleapis.com")),
            Arguments.of("ollama", arrayOf("", "http://localhost:11434")),
            Arguments.of("openai", arrayOf("", "https://api.openai.com")),
            Arguments.of("openrouter", arrayOf("", "https://openrouter.ai"))
        )
    }

    @ParameterizedTest
    @MethodSource("providerTestData")
    fun `should bind provider properties correctly`(provider: String, expectedApiKey: String, expectedBaseUrl: String) {
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

    @Test
    fun `should bind ollama properties correctly`() {
        assertNotNull(properties.ollamaClientProperties)
        assertEquals(OLLAMA_URL, properties.ollamaClientProperties.baseUrl)
    }

    @ParameterizedTest
    @MethodSource("defaultValuesTestData")
    fun `should use default values when properties are not set`(provider: String, expectedValues: Array<String>) {
        val defaultProperties = KoogProperties()

        when (provider) {
            "ollama" -> {
                assertNotNull(defaultProperties.ollamaClientProperties)
                assertEquals(expectedValues[1], defaultProperties.ollamaClientProperties.baseUrl)
            }

            else -> {
                val props = when (provider) {
                    "anthropic" -> defaultProperties.anthropicClientProperties
                    "google" -> defaultProperties.googleClientProperties
                    "openai" -> defaultProperties.openAIClientProperties
                    "openrouter" -> defaultProperties.openRouterClientProperties
                    else -> throw IllegalArgumentException("Unknown provider: $provider")
                }
                assertNotNull(props)
                assertEquals(expectedValues[0], props.apiKey)
                assertEquals(expectedValues[1], props.baseUrl)
            }
        }
    }
}