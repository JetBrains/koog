package ai.koog.prompt.executor.clients.mistralai

import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MistralAIParamsTest {

    @Test
    fun `topP bounds`() {
        MistralAIParams(topP = 0.0)
        MistralAIParams(topP = 1.0)
        assertThrows<IllegalArgumentException> { MistralAIParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { MistralAIParams(topP = 1.1) }
    }

    @Test
    fun `frequency and presence penalties bounds`() {
        MistralAIParams(frequencyPenalty = -2.0, presencePenalty = -2.0)
        MistralAIParams(frequencyPenalty = 2.0, presencePenalty = 2.0)
        assertThrows<IllegalArgumentException> { MistralAIParams(frequencyPenalty = -2.1) }
        assertThrows<IllegalArgumentException> { MistralAIParams(presencePenalty = -2.1) }
        assertThrows<IllegalArgumentException> { MistralAIParams(frequencyPenalty = 2.1) }
        assertThrows<IllegalArgumentException> { MistralAIParams(presencePenalty = 2.1) }
    }

    @Test
    fun `stop sequences constraints`() {
        assertThrows<IllegalArgumentException> { MistralAIParams(stop = emptyList()) }
        assertThrows<IllegalArgumentException> { MistralAIParams(stop = listOf("")) }
        assertThrows<IllegalArgumentException> { MistralAIParams(stop = listOf("a", "b", "c", "d", "e")) }
        MistralAIParams(stop = listOf("a"))
        MistralAIParams(stop = listOf("a", "b", "c", "d"))
    }

    @Test
    fun `Should make a full copy`() {
        val source = MistralAIParams(
            temperature = 0.43,
            maxTokens = 100500,
            numberOfChoices = 42,
            speculation = "forex",
            schema = LLMParams.Schema.JSON.Basic("test", JsonObject(mapOf())),
            toolChoice = LLMParams.ToolChoice.Named("calculator"),
            user = "alice",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            frequencyPenalty = 0.5,
            presencePenalty = 0.6,
            stop = listOf("cancel"),
            topP = 0.87,
            randomSeed = 123,
            parallelToolCalls = true,
            promptMode = "reasoning",
            safePrompt = false
        )

        val target = source.copy()
        target shouldBeEqualToComparingFields source
    }

    @Test
    fun `LLMParams to MistralAI conversion preserves base fields`() {
        val base = LLMParams(
            temperature = 0.5,
            maxTokens = 42,
            numberOfChoices = 3,
            speculation = "sp",
            user = "uid",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar"))
        )
        val mistralAI = base.toMistralAIParams()
        assertEquals(base.temperature, mistralAI.temperature)
        assertEquals(base.maxTokens, mistralAI.maxTokens)
        assertEquals(base.numberOfChoices, mistralAI.numberOfChoices)
        assertEquals(base.speculation, mistralAI.speculation)
        assertEquals(base.user, mistralAI.user)
    }
}
