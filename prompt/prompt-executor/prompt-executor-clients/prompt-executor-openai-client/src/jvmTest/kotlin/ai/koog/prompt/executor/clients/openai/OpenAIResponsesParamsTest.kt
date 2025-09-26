package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.executor.clients.openai.models.Truncation
import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenAIResponsesParamsTest {

    @ParameterizedTest
    @ValueSource(doubles = [0.1, 1.0])
    fun `OpenAIResponsesParams topP bounds`(value: Double) {
        assertNotNull(OpenAIResponsesParams(topP = value))
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

    @Test
    fun `LLMParams to OpenAIResponsesParams conversions preserve base fields`() {
        val base = LLMParams(
            temperature = 0.7,
            maxTokens = 123,
            numberOfChoices = 2,
            speculation = "spec",
            user = "user-id",
            includeThoughts = true,
        )

        val resp = base.toOpenAIResponsesParams()

        assertEquals(base.temperature, resp.temperature)
        assertEquals(base.maxTokens, resp.maxTokens)
        assertEquals(base.numberOfChoices, resp.numberOfChoices)
        assertEquals(base.speculation, resp.speculation)
        assertEquals(base.user, resp.user)
        assertEquals(base.includeThoughts, resp.includeThoughts)
        assertEquals(base.additionalProperties, resp.additionalProperties)
    }

    @Test
    fun `temperature and topP are mutually exclusive in Responses`() {
        assertThrows<IllegalArgumentException>("Responses: temperature and topP are mutually exclusive") {
            OpenAIResponsesParams(
                temperature = 0.5,
                topP = 0.5
            )
        }
    }

    @Test
    fun `non-blank identifiers validated`() {
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

    @Test
    fun `Should make a full copy`() {
        val source = OpenAIResponsesParams(
            temperature = 0.75,
            maxTokens = 123424,
            numberOfChoices = 10,
            speculation = "spec",
            schema = LLMParams.Schema.JSON.Basic("test", JsonObject(mapOf())),
            toolChoice = LLMParams.ToolChoice.Required,
            user = "user-id",
            includeThoughts = true,
            thinkingBudget = 123,
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            background = true,
            include = listOf("a", "b", "c"),
            maxToolCalls = 10,
            parallelToolCalls = true,
            reasoning = ReasoningConfig(effort = ReasoningEffort.HIGH, summary = ReasoningSummary.DETAILED),
            truncation = Truncation.DISABLED,
            promptCacheKey = "abcdefghijklmnop",
            safetyIdentifier = "key",
            serviceTier = ServiceTier.FLEX,
            store = true,
            logprobs = true,
            topLogprobs = 14,
        )

        val target = source.copy()
        target shouldBeEqualToComparingFields source
    }
}
