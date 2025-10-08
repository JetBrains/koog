package ai.koog.prompt.executor.clients.dashscope

import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DashscopeParamsTest {

    @Test
    fun `topP bounds`() {
        DashscopeParams(topP = 0.0)
        DashscopeParams(topP = 1.0)
        assertThrows<IllegalArgumentException> { DashscopeParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { DashscopeParams(topP = 1.1) }
    }

    @Test
    fun `topLogprobs requires logprobs=true`() {
        assertThrows<IllegalArgumentException> { DashscopeParams(logprobs = null, topLogprobs = 1) }
        assertThrows<IllegalArgumentException> { DashscopeParams(logprobs = false, topLogprobs = 1) }
        DashscopeParams(logprobs = true, topLogprobs = 0)
        DashscopeParams(logprobs = true, topLogprobs = 20)
        assertThrows<IllegalArgumentException> { DashscopeParams(logprobs = true, topLogprobs = -1) }
        assertThrows<IllegalArgumentException> { DashscopeParams(logprobs = true, topLogprobs = 21) }
    }

    @Test
    fun `frequency and presence penalties bounds`() {
        DashscopeParams(frequencyPenalty = -2.0, presencePenalty = -2.0)
        DashscopeParams(frequencyPenalty = 2.0, presencePenalty = 2.0)
        assertThrows<IllegalArgumentException> { DashscopeParams(frequencyPenalty = -2.1) }
        assertThrows<IllegalArgumentException> { DashscopeParams(presencePenalty = -2.1) }
        assertThrows<IllegalArgumentException> { DashscopeParams(frequencyPenalty = 2.1) }
        assertThrows<IllegalArgumentException> { DashscopeParams(presencePenalty = 2.1) }
    }

    @Test
    fun `stop sequences constraints`() {
        assertThrows<IllegalArgumentException> { DashscopeParams(stop = emptyList()) }
        assertThrows<IllegalArgumentException> { DashscopeParams(stop = listOf("")) }
        assertThrows<IllegalArgumentException> { DashscopeParams(stop = listOf("a", "b", "c", "d", "e")) }
        DashscopeParams(stop = listOf("a"))
        DashscopeParams(stop = listOf("a", "b", "c", "d"))
    }

    @Test
    fun `Should make a full copy`() {
        val source = DashscopeParams(
            temperature = 0.43,
            maxTokens = 100500,
            numberOfChoices = 42,
            speculation = "forex",
            schema = LLMParams.Schema.JSON.Basic("test", JsonObject(mapOf())),
            toolChoice = LLMParams.ToolChoice.Named("calculator"),
            user = "alice",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar")),
            enableSearch = true,
            parallelToolCalls = false,
            enableThinking = true,
            frequencyPenalty = 0.5,
            presencePenalty = 0.6,
            logprobs = true,
            stop = listOf("cancel"),
            topLogprobs = 15,
            topP = 0.87
        )

        val target = source.copy()
        target shouldBeEqualToComparingFields source
    }

    @Test
    fun `LLMParams to DashScope conversion preserves base fields`() {
        val base = LLMParams(
            temperature = 0.5,
            maxTokens = 42,
            numberOfChoices = 3,
            speculation = "sp",
            user = "uid",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar"))
        )
        val ds = base.toDashscopeParams()
        assertEquals(base.temperature, ds.temperature)
        assertEquals(base.maxTokens, ds.maxTokens)
        assertEquals(base.numberOfChoices, ds.numberOfChoices)
        assertEquals(base.speculation, ds.speculation)
        assertEquals(base.user, ds.user)
    }
}
