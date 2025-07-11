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
import java.util.stream.Stream

@SpringBootTest(classes = [KoogDefaultPropertiesTest.TestConfig::class])
class KoogDefaultPropertiesTest {

    @EnableConfigurationProperties(KoogProperties::class)
    class TestConfig

    @Autowired
    private lateinit var properties: KoogProperties

    companion object {
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
    @MethodSource("defaultValuesTestData")
    fun `should use default values when properties are not set`(provider: String, expectedValues: Array<String>) {
        when (provider) {
            "ollama" -> {
                assertNotNull(properties.ollamaClientProperties)
                assertEquals(expectedValues[1], properties.ollamaClientProperties.baseUrl)
            }

            else -> {
                val props = when (provider) {
                    "anthropic" -> properties.anthropicClientProperties
                    "google" -> properties.googleClientProperties
                    "openai" -> properties.openAIClientProperties
                    "openrouter" -> properties.openRouterClientProperties
                    else -> throw IllegalArgumentException("Unknown provider: $provider")
                }
                assertNotNull(props)
                assertEquals(expectedValues[0], props.apiKey)
                assertEquals(expectedValues[1], props.baseUrl)
            }
        }
    }
}