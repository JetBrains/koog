package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.params.LLMParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class OpenAIParamsValidationTest {

    @Test
    fun `OpenAIResponsesParams topP bounds`() {
        OpenAIResponsesParams(topP = 0.0)
        OpenAIResponsesParams(topP = 1.0)
    }

    @Test
    fun `OpenAIResponsesParams invalid topP`() {
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(topP = 1.1) }
    }

    @Test
    fun `OpenAIResponsesParams topLogprobs requires logprobs=true`() {
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(logprobs = null, topLogprobs = 1) }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(logprobs = false, topLogprobs = 1) }
    }

    @Test
    fun `OpenAIResponsesParams topLogprobs bounds`() {
        // With logprobs=true the allowed range is [0, 20]
        OpenAIResponsesParams(logprobs = true, topLogprobs = 0)
        OpenAIResponsesParams(logprobs = true, topLogprobs = 20)
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(logprobs = true, topLogprobs = -1) }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(logprobs = true, topLogprobs = 21) }
    }

    @Test
    fun `OpenAIChatParams validations smoke`() {
        OpenAIChatParams(topP = 0.0)
        OpenAIChatParams(topP = 1.0)
        assertThrows<IllegalArgumentException> { OpenAIChatParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { OpenAIChatParams(topP = 1.1) }

        // topLogprobs requires logprobs=true
        assertThrows<IllegalArgumentException> { OpenAIChatParams(logprobs = null, topLogprobs = 1) }
        assertThrows<IllegalArgumentException> { OpenAIChatParams(logprobs = false, topLogprobs = 1) }

        OpenAIChatParams(logprobs = true, topLogprobs = 0)
        OpenAIChatParams(logprobs = true, topLogprobs = 20)
        assertThrows<IllegalArgumentException> { OpenAIChatParams(logprobs = true, topLogprobs = -1) }
        assertThrows<IllegalArgumentException> { OpenAIChatParams(logprobs = true, topLogprobs = 21) }
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
        assertThrows<IllegalArgumentException> { OpenAIChatParams(temperature = 0.5, topP = 0.5) }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(temperature = 0.5, topP = 0.5) }
    }

    @Test
    fun `non-blank identifiers validated`() {
        assertThrows<IllegalArgumentException> { OpenAIChatParams(promptCacheKey = " ") }
        assertThrows<IllegalArgumentException> { OpenAIChatParams(safetyIdentifier = "") }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(promptCacheKey = " ") }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(safetyIdentifier = "") }
        OpenAIChatParams(promptCacheKey = "key", safetyIdentifier = "sid")
        OpenAIResponsesParams(promptCacheKey = "key", safetyIdentifier = "sid")
    }

    @Test
    fun `responses include and maxToolCalls validations`() {
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(include = emptyList()) }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(include = listOf("")) }
        assertThrows<IllegalArgumentException> { OpenAIResponsesParams(maxToolCalls = -1) }
        OpenAIResponsesParams(include = listOf("output_text"), maxToolCalls = 0)
    }
}
