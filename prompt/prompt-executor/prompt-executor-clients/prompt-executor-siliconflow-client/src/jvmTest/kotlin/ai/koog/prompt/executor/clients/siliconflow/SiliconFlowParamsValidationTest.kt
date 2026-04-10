package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SiliconFlowParamsValidationTest {

    @Test
    fun testTopPAndTopLogprobsValidation() {
        SiliconFlowParams(topP = 0.0)
        SiliconFlowParams(topP = 1.0)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(topP = -0.1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(topP = 1.1) }

        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(logprobs = null, topLogprobs = 1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(logprobs = false, topLogprobs = 1) }
        SiliconFlowParams(logprobs = true, topLogprobs = 0)
        SiliconFlowParams(logprobs = true, topLogprobs = 20)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(logprobs = true, topLogprobs = -1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(logprobs = true, topLogprobs = 21) }
    }

    @Test
    fun testTopKAndPenaltyValidation() {
        SiliconFlowParams(topK = 1)
        SiliconFlowParams(topK = 10)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(topK = 0) }

        SiliconFlowParams(frequencyPenalty = -2.0)
        SiliconFlowParams(frequencyPenalty = 2.0)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(frequencyPenalty = -2.1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(frequencyPenalty = 2.1) }

        SiliconFlowParams(presencePenalty = -2.0)
        SiliconFlowParams(presencePenalty = 2.0)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(presencePenalty = -2.1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(presencePenalty = 2.1) }

        SiliconFlowParams(repetitionPenalty = 0.0)
        SiliconFlowParams(repetitionPenalty = 2.0)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(repetitionPenalty = -0.1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(repetitionPenalty = 2.1) }
    }

    @Test
    fun testMinPAndTopAValidation() {
        SiliconFlowParams(minP = 0.0)
        SiliconFlowParams(minP = 1.0)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(minP = -0.1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(minP = 1.1) }

        SiliconFlowParams(topA = 0.0)
        SiliconFlowParams(topA = 1.0)
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(topA = -0.1) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(topA = 1.1) }
    }

    @Test
    fun testStopValidation() {
        SiliconFlowParams(stop = listOf("END"))
        SiliconFlowParams(stop = listOf("A", "B", "C", "D"))

        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(stop = emptyList()) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(stop = listOf("A", "B", "C", "D", "E")) }
        assertFailsWith<IllegalArgumentException> { SiliconFlowParams(stop = listOf("   ")) }
    }

    @Test
    fun testLlmParamsToSiliconFlowParamsPreservesBaseFields() {
        val base = LLMParams(
            temperature = 1.1,
            maxTokens = 321,
            numberOfChoices = 1,
            speculation = "router",
            user = "user",
        )

        val target = base.toSiliconFlowParams()

        assertEquals(base.temperature, target.temperature)
        assertEquals(base.maxTokens, target.maxTokens)
        assertEquals(base.numberOfChoices, target.numberOfChoices)
        assertEquals(base.speculation, target.speculation)
        assertEquals(base.user, target.user)
        assertNull(target.topK)
        assertNull(target.topP)
        assertNull(target.topLogprobs)
        assertNull(target.logprobs)
    }

    @Test
    fun testCopyCreatesEqualInstance() {
        val source = SiliconFlowParams(
            temperature = 1.0,
            maxTokens = 321,
            numberOfChoices = 1,
            speculation = "copy",
            user = "user",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            topP = 1.0,
            topK = 3,
            topLogprobs = 3,
            logprobs = true,
            frequencyPenalty = 0.3,
            presencePenalty = -0.3,
            repetitionPenalty = 1.1,
            minP = 0.2,
            topA = 0.4,
            stop = listOf("END"),
            transforms = listOf("middle-out"),
            models = listOf("Pro/deepseek-ai/DeepSeek-R1"),
            route = "test-route",
            toolChoice = LLMParams.ToolChoice.Named("calculator")
        )

        val target = source.copy()

        assertEquals(source, target)
    }
}
