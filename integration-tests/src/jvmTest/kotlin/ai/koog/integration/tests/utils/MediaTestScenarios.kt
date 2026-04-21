package ai.koog.integration.tests.utils

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

object MediaTestScenarios {
    enum class ImageTestScenario {
        BASIC_PNG,
        BASIC_JPG,

        EMPTY_IMAGE,
        CORRUPTED_IMAGE,
    }

    enum class TextTestScenario {
        BASIC_TEXT,
        UTF8_ENCODING,
        ASCII_ENCODING,
        UNICODE_TEXT,

        EMPTY_TEXT,
        CORRUPTED_TEXT
    }

    enum class MarkdownTestScenario {
        BASIC_MARKDOWN,

        MALFORMED_SYNTAX,
        NESTED_FORMATTING,
        COMPLEX_NESTED_LISTS,
        EMBEDDED_HTML,
        EMPTY_CODE_BLOCKS,
        BROKEN_LINKS,
    }

    enum class AudioTestScenario {
        BASIC_WAV,
        BASIC_MP3,
        CORRUPTED_AUDIO
    }

    val models = listOf(
        AnthropicModels.Sonnet_4_6,
        GoogleModels.Gemini2_5Flash,
        OpenAIModels.Chat.GPT5_1,
    )

    @JvmStatic
    fun markdownScenarioModelCombinations(): Stream<Arguments> {
        val scenarios = MarkdownTestScenario.entries.toTypedArray()
        return scenarios.flatMap { scenario ->
            models.map { model ->
                Arguments.of(scenario, model)
            }
        }.stream()
    }

    @JvmStatic
    fun textScenarioModelCombinations(): Stream<Arguments> {
        val scenarios = TextTestScenario.entries.toTypedArray()
        return scenarios.flatMap { scenario ->
            models.map { model ->
                Arguments.of(scenario, model)
            }
        }.stream()
    }

    @JvmStatic
    fun audioScenarioModelCombinations(): Stream<Arguments> {
        val scenarios = AudioTestScenario.entries.toTypedArray()
        val models = listOf(
            OpenAIModels.Audio.GptAudio,
            GoogleModels.Gemini2_5Pro
        )
        return scenarios.flatMap { scenario ->
            models.map { model ->
                Arguments.of(scenario, model)
            }
        }.stream()
    }
}
