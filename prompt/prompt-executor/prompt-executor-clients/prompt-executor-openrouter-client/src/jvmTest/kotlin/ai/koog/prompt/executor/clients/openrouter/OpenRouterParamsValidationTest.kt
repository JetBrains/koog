package ai.koog.prompt.executor.clients.openrouter

import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OpenRouterParamsValidationTest {

    @Test
    fun `topP and topLogprobs validations`() {
        OpenRouterParams(topP = 0.0)
        OpenRouterParams(topP = 1.0)
        assertThrows<IllegalArgumentException> { OpenRouterParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { OpenRouterParams(topP = 1.1) }

        assertThrows<IllegalArgumentException> { OpenRouterParams(logprobs = null, topLogprobs = 1) }
        assertThrows<IllegalArgumentException> { OpenRouterParams(logprobs = false, topLogprobs = 1) }
        OpenRouterParams(logprobs = true, topLogprobs = 0)
        OpenRouterParams(logprobs = true, topLogprobs = 20)
        assertThrows<IllegalArgumentException> { OpenRouterParams(logprobs = true, topLogprobs = -1) }
        assertThrows<IllegalArgumentException> { OpenRouterParams(logprobs = true, topLogprobs = 21) }
    }

    @Test
    fun `topK and repetitionPenalty validations`() {
        assertThrows<IllegalArgumentException> { OpenRouterParams(topK = 0) }
        OpenRouterParams(topK = 1)
        OpenRouterParams(topK = 10)

        OpenRouterParams(repetitionPenalty = 0.0)
        OpenRouterParams(repetitionPenalty = 2.0)
        assertThrows<IllegalArgumentException> { OpenRouterParams(repetitionPenalty = -0.1) }
        assertThrows<IllegalArgumentException> { OpenRouterParams(repetitionPenalty = 2.1) }
    }

    @Test
    fun `LLMParams to OpenRouter conversion preserves base fields`() {
        val base = LLMParams(
            temperature = 1.1,
            maxTokens = 321,
            numberOfChoices = 1,
            speculation = "router",
            user = "user",
        )
        val or = base.toOpenRouterParams()
        assert(base.temperature == or.temperature)
        assert(base.maxTokens == or.maxTokens)
        assert(base.numberOfChoices == or.numberOfChoices)
        assert(base.speculation == or.speculation)
        assert(base.user == or.user)
    }

    @Test
    fun `Should make a full copy`() {
        val source = OpenRouterParams(
            temperature = 1.0,
            maxTokens = 321,
            numberOfChoices = 1,
            speculation = "copy",
            user = "user",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            topP = 1.0,
            topK = 3,
            toolChoice = LLMParams.ToolChoice.Named("calculator")
        )

        val target = source.copy()
        target shouldBeEqualToComparingFields source
    }

    @Test
    fun `Should create from LLMParams`() {
        val source = LLMParams(
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            maxTokens = 321,
            numberOfChoices = 54,
            speculation = "copy",
            temperature = 0.43,
            toolChoice = LLMParams.ToolChoice.Named("calculator"),
            user = "user",
        )

        // when
        val target = source.toOpenRouterParams()

        // then
        target.additionalProperties shouldBe source.additionalProperties
        target.maxTokens shouldBe source.maxTokens
        target.numberOfChoices shouldBe source.numberOfChoices
        target.speculation shouldBe source.speculation
        target.temperature shouldBe source.temperature
        target.toolChoice shouldBe source.toolChoice
        target.topK shouldBe null
        target.topP shouldBe null
        target.user shouldBe source.user
    }
}
