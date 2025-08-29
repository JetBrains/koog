package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.params.LLMParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class OpenAIParamsValidationTest {

    @Test
    fun `OpenAIResponsesParams topP bounds`() {
        OpenAIResponsesParams(topP = 0.0)
        OpenAIResponsesParams(topP = 1.0)
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1])
    fun `OpenAIResponsesParams invalid topP`(value: Double) {
        assertThrows<IllegalArgumentException>("Responses: topP must be in (0.0, 1.0]") {
            OpenAIResponsesParams(topP = value)
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = [false])
    fun `OpenAIResponsesParams topLogprobs requires logprobs=true`(logprobsValue: Boolean?) {
        assertThrows<IllegalArgumentException>("Responses: `topLogprobs` requires `logprobs=true`.") {
            OpenAIResponsesParams(
                logprobs = logprobsValue,
                topLogprobs = 1
            )
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 20])
    fun `OpenAIResponsesParams topLogprobs bounds`(topLogprobs: Int) {
        // With logprobs=true the allowed range is [0, 20]
        OpenAIResponsesParams(logprobs = true, topLogprobs = topLogprobs)
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 21])
    fun `OpenAIResponsesParams invalid topLogprobs values when logprobs=true`(value: Int) {
        assertThrows<IllegalArgumentException>("Responses: `topLogprobs` must be in [0, 20]") {
            OpenAIResponsesParams(
                logprobs = true,
                topLogprobs = value
            )
        }
    }

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
    fun `LLMParams to OpenAI conversions preserve base fields`() {
        val base = LLMParams(
            temperature = 0.7,
            maxTokens = 123,
            numberOfChoices = 2,
            speculation = "spec",
            user = "user-id",
            includeThoughts = true,
        )

        val chat = base.toOpenAIChatParams()
        val resp = base.toOpenAIResponsesParams()

        assertEquals(base.temperature, chat.temperature)
        assertEquals(base.maxTokens, chat.maxTokens)
        assertEquals(base.numberOfChoices, chat.numberOfChoices)
        assertEquals(base.speculation, chat.speculation)
        assertEquals(base.user, chat.user)
        assertEquals(base.includeThoughts, chat.includeThoughts)

        assertEquals(base.temperature, resp.temperature)
        assertEquals(base.maxTokens, resp.maxTokens)
        assertEquals(base.numberOfChoices, resp.numberOfChoices)
        assertEquals(base.speculation, resp.speculation)
        assertEquals(base.user, resp.user)
        assertEquals(base.includeThoughts, resp.includeThoughts)
    }

    @Test
    fun `temperature and topP are mutually exclusive in Chat and Responses`() {
        assertThrows<IllegalArgumentException>("Chat: temperature and topP are mutually exclusive") {
            OpenAIChatParams(
                temperature = 0.5,
                topP = 0.5
            )
        }
        assertThrows<IllegalArgumentException>("Responses: temperature and topP are mutually exclusive") {
            OpenAIResponsesParams(
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
        assertThrows<IllegalArgumentException>("Responses: promptCacheKey must be non-blank") {
            OpenAIResponsesParams(
                promptCacheKey = " "
            )
        }
        assertThrows<IllegalArgumentException>("Responses: safetyIdentifier must be non-blank") {
            OpenAIResponsesParams(
                safetyIdentifier = ""
            )
        }
        OpenAIChatParams(promptCacheKey = "key", safetyIdentifier = "sid")
        OpenAIResponsesParams(promptCacheKey = "key", safetyIdentifier = "sid")
    }

    @Test
    fun `responses include and maxToolCalls validations`() {
        assertThrows<IllegalArgumentException>("Responses: include must not be empty when provided.") {
            OpenAIResponsesParams(
                include = emptyList()
            )
        }
        assertThrows<IllegalArgumentException>("Responses: include entries must be non-blank") {
            OpenAIResponsesParams(
                include = listOf("")
            )
        }
        assertThrows<IllegalArgumentException>("Responses: maxToolCalls must be >= 0") {
            OpenAIResponsesParams(
                maxToolCalls = -1
            )
        }
        OpenAIResponsesParams(include = listOf("output_text"), maxToolCalls = 0)
    }
}
