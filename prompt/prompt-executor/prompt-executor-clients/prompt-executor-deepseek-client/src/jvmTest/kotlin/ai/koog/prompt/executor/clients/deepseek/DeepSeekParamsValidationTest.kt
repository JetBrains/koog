package ai.koog.prompt.executor.clients.deepseek

import ai.koog.prompt.params.LLMParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DeepSeekParamsValidationTest {

    @Test
    fun `topP bounds`() {
        DeepSeekParams(topP = 0.0)
        DeepSeekParams(topP = 1.0)
        assertThrows<IllegalArgumentException> { DeepSeekParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(topP = 1.1) }
    }

    @Test
    fun `topLogprobs requires logprobs=true`() {
        assertThrows<IllegalArgumentException> { DeepSeekParams(logprobs = null, topLogprobs = 1) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(logprobs = false, topLogprobs = 1) }
        DeepSeekParams(logprobs = true, topLogprobs = 0)
        DeepSeekParams(logprobs = true, topLogprobs = 20)
        assertThrows<IllegalArgumentException> { DeepSeekParams(logprobs = true, topLogprobs = -1) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(logprobs = true, topLogprobs = 21) }
    }

    @Test
    fun `frequency and presence penalties bounds`() {
        DeepSeekParams(frequencyPenalty = -2.0, presencePenalty = -2.0)
        DeepSeekParams(frequencyPenalty = 2.0, presencePenalty = 2.0)
        assertThrows<IllegalArgumentException> { DeepSeekParams(frequencyPenalty = -2.1) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(presencePenalty = -2.1) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(frequencyPenalty = 2.1) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(presencePenalty = 2.1) }
    }

    @Test
    fun `stop sequences constraints`() {
        assertThrows<IllegalArgumentException> { DeepSeekParams(stop = emptyList()) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(stop = listOf("")) }
        assertThrows<IllegalArgumentException> { DeepSeekParams(stop = listOf("a", "b", "c", "d", "e")) }
        DeepSeekParams(stop = listOf("a"))
        DeepSeekParams(stop = listOf("a", "b", "c", "d"))
    }

    @Test
    fun `LLMParams to DeepSeek conversion preserves base fields`() {
        val base = LLMParams(
            temperature = 0.5,
            maxTokens = 42,
            numberOfChoices = 3,
            speculation = "sp",
            user = "uid",
            includeThoughts = false,
        )
        val ds = base.toDeepSeekParams()
        assertEquals(base.temperature, ds.temperature)
        assertEquals(base.maxTokens, ds.maxTokens)
        assertEquals(base.numberOfChoices, ds.numberOfChoices)
        assertEquals(base.speculation, ds.speculation)
        assertEquals(base.user, ds.user)
        assertEquals(base.includeThoughts, ds.includeThoughts)
    }
}
