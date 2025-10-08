package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioConfig
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioVoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIUserLocation
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIWebSearchOptions
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class OpenAIChatParamsTest {

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 1.0])
    fun `OpenAIChatParams topP within bounds`(topP: Double) {
        OpenAIChatParams(topP = topP)
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1])
    fun `OpenAIChatParams invalid topP`(value: Double) {
        val expected = if (value < 0) "TopP must be positive" else "TopP must be <= 1"
        assertThrows<IllegalArgumentException>(expected) { OpenAIChatParams(topP = value) }
    }

    @Test
    fun `OpenAIChatParams other validations`() {
        // non-parametric checks remain here
        OpenAIChatParams(logprobs = true, topLogprobs = 0)
        OpenAIChatParams(logprobs = true, topLogprobs = 20)
    }

    @Test
    fun `OpenAIChatParams topLogprobs requires logprobs=true`() {
        assertThrows<IllegalArgumentException> {
            OpenAIChatParams(
                logprobs = false,
                topLogprobs = 1
            )
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 21])
    fun `OpenAIChatParams invalid topLogprobs values when logprobs=true`(value: Int) {
        assertThrows<IllegalArgumentException>("Chat: `topLogprobs` must be in [0, 20]") {
            OpenAIChatParams(
                logprobs = true,
                topLogprobs = value
            )
        }
    }

    @Test
    fun `LLMParams to OpenAIChatParams conversions preserve base fields`() {
        val base = LLMParams(
            temperature = 0.7,
            maxTokens = 123,
            numberOfChoices = 2,
            speculation = "spec",
            user = "user-id",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar"))
        )

        val chat = base.toOpenAIChatParams()

        assertEquals(base.temperature, chat.temperature)
        assertEquals(base.maxTokens, chat.maxTokens)
        assertEquals(base.numberOfChoices, chat.numberOfChoices)
        assertEquals(base.speculation, chat.speculation)
        assertEquals(base.user, chat.user)
        assertEquals(base.additionalProperties, chat.additionalProperties)
    }

    @Test
    fun `temperature and topP are mutually exclusive in Chat`() {
        assertThrows<IllegalArgumentException>("Chat: temperature and topP are mutually exclusive") {
            OpenAIChatParams(
                temperature = 0.5,
                topP = 0.5
            )
        }
    }

    @Test
    fun `non-blank identifiers validated`() {
        assertThrows<IllegalArgumentException>("Chat: promptCacheKey must be non-blank") {
            OpenAIChatParams(
                promptCacheKey = " "
            )
        }
        assertThrows<IllegalArgumentException>("Chat: safetyIdentifier must be non-blank") {
            OpenAIChatParams(
                safetyIdentifier = ""
            )
        }

        OpenAIChatParams(promptCacheKey = "key", safetyIdentifier = "sid")
    }

    @Test
    fun `Should make a full copy`() {
        val source = OpenAIChatParams(
            temperature = 0.7,
            maxTokens = 1232,
            numberOfChoices = 23,
            speculation = "spec",
            schema = LLMParams.Schema.JSON.Basic("test", JsonObject(mapOf())),
            toolChoice = LLMParams.ToolChoice.Required,
            user = "user-id",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            frequencyPenalty = 0.43,
            presencePenalty = 0.523,
            parallelToolCalls = true,
            promptCacheKey = "key",
            safetyIdentifier = "safe-identifier",
            serviceTier = ServiceTier.PRIORITY,
            store = true,
            audio = OpenAIAudioConfig(format = OpenAIAudioFormat.OPUS, voice = OpenAIAudioVoice.Ash),
            logprobs = true,
            reasoningEffort = ReasoningEffort.HIGH,
            stop = listOf("aaa", "bbb", "ccc"),
            topLogprobs = 13,
            webSearchOptions = OpenAIWebSearchOptions(
                "high",
                userLocation = OpenAIUserLocation(
                    OpenAIUserLocation.ApproximateLocation(
                        city = "New York"
                    )
                )
            )
        )

        val target = source.copy()
        target shouldBeEqualToComparingFields source
    }
}
