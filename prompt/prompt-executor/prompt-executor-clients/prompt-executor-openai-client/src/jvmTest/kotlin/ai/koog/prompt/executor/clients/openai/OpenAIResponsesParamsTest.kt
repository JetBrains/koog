package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary
import ai.koog.prompt.executor.clients.openai.models.Truncation
import ai.koog.prompt.params.LLMParams
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class OpenAIResponsesParamsTest {

    @ParameterizedTest
    @ValueSource(doubles = [0.1, 1.0])
    fun `OpenAIResponsesParams topP bounds`(value: Double) {
        OpenAIResponsesParams(topP = value).shouldNotBeNull()
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 1.1])
    fun `OpenAIResponsesParams invalid topP`(value: Double) {
        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(topP = value)
        }.message shouldBe "topP must be in (0.0, 1.0], but was $value"
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = [false])
    fun `OpenAIResponsesParams topLogprobs requires logprobs=true`(logprobsValue: Boolean?) {
        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                logprobs = logprobsValue,
                topLogprobs = 1
            )
        }.message shouldBe "`topLogprobs` requires `logprobs=true`."
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
        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                logprobs = true,
                topLogprobs = value
            )
        }.message shouldBe "`topLogprobs` must be in [0, 20], but was $value"
    }

    @Test
    fun `LLMParams to OpenAIResponsesParams conversions preserve base fields`() {
        val base = LLMParams(
            temperature = 0.7,
            maxTokens = 123,
            numberOfChoices = 2,
            speculation = "spec",
            user = "user-id",
        )

        base.toOpenAIResponsesParams().shouldNotBeNull {
            assertSoftly {
                temperature shouldBe base.temperature
                maxTokens shouldBe base.maxTokens
                numberOfChoices shouldBe base.numberOfChoices
                speculation shouldBe base.speculation
                user shouldBe base.user
                additionalProperties shouldBe base.additionalProperties
            }
        }
    }

    @Test
    fun `temperature and topP are mutually exclusive in Responses`() {
        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                temperature = 0.5,
                topP = 0.5
            )
        }.message shouldBe "temperature and topP are mutually exclusive"
    }

    @Test
    fun `non-blank identifiers validated`() {
        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                promptCacheKey = " "
            )
        }.message shouldBe "promptCacheKey must be non-blank"

        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                safetyIdentifier = ""
            )
        }.message shouldBe "safetyIdentifier must be non-blank"

        OpenAIChatParams(promptCacheKey = "key", safetyIdentifier = "sid")
    }

    @Test
    fun `responses include and maxToolCalls validations`() {
        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                include = emptyList()
            )
        }.message shouldBe "include must not be empty when provided."

        shouldThrow<IllegalArgumentException> {
            OpenAIResponsesParams(
                maxToolCalls = -1
            )
        }.message shouldBe "maxToolCalls must be >= 0"
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
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            background = true,
            include = listOf(OpenAIInclude.REASONING_ENCRYPTED_CONTENT, OpenAIInclude.OUTPUT_TEXT_LOGPROBS),
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
