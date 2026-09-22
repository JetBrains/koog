package ai.koog.prompt.executor.clients.requesty

import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RequestyParamsTest {

    @Test
    fun `topP bounds`() {
        RequestyParams(topP = 0.0)
        RequestyParams(topP = 1.0)
        assertThrows<IllegalArgumentException> { RequestyParams(topP = -0.1) }
        assertThrows<IllegalArgumentException> { RequestyParams(topP = 1.1) }
    }

    @Test
    fun `topLogprobs requires logprobs=true`() {
        assertThrows<IllegalArgumentException> { RequestyParams(logprobs = null, topLogprobs = 1) }
        assertThrows<IllegalArgumentException> { RequestyParams(logprobs = false, topLogprobs = 1) }
        RequestyParams(logprobs = true, topLogprobs = 0)
        RequestyParams(logprobs = true, topLogprobs = 20)
        assertThrows<IllegalArgumentException> { RequestyParams(logprobs = true, topLogprobs = -1) }
        assertThrows<IllegalArgumentException> { RequestyParams(logprobs = true, topLogprobs = 21) }
    }

    @Test
    fun `frequency and presence penalties bounds`() {
        RequestyParams(frequencyPenalty = -2.0, presencePenalty = -2.0)
        RequestyParams(frequencyPenalty = 2.0, presencePenalty = 2.0)
        assertThrows<IllegalArgumentException> { RequestyParams(frequencyPenalty = -2.1) }
        assertThrows<IllegalArgumentException> { RequestyParams(presencePenalty = -2.1) }
        assertThrows<IllegalArgumentException> { RequestyParams(frequencyPenalty = 2.1) }
        assertThrows<IllegalArgumentException> { RequestyParams(presencePenalty = 2.1) }
    }

    @Test
    fun `stop sequences constraints`() {
        assertThrows<IllegalArgumentException> { RequestyParams(stop = emptyList()) }
        assertThrows<IllegalArgumentException> { RequestyParams(stop = listOf("")) }
        assertThrows<IllegalArgumentException> { RequestyParams(stop = listOf("a", "b", "c", "d", "e")) }
        RequestyParams(stop = listOf("a"))
        RequestyParams(stop = listOf("a", "b", "c", "d"))
    }

    @Test
    fun `Should make a full copy`() {
        val source = RequestyParams(
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
            logprobs = true,
            stop = listOf("cancel"),
            topLogprobs = 15,
            topP = 0.87
        )

        val target = source.copy()
        target shouldBeEqualToComparingFields source
        assertEquals(source, target)
        assertEquals(source.hashCode(), target.hashCode())
    }

    @Test
    fun `LLMParams to Requesty conversion preserves base fields`() {
        val base = LLMParams(
            temperature = 0.5,
            maxTokens = 42,
            numberOfChoices = 3,
            speculation = "sp",
            user = "uid",
            additionalProperties = mapOf("foo" to JsonPrimitive("bar"))
        )
        val rq = base.toRequestyParams()
        assertEquals(base.temperature, rq.temperature)
        assertEquals(base.maxTokens, rq.maxTokens)
        assertEquals(base.numberOfChoices, rq.numberOfChoices)
        assertEquals(base.speculation, rq.speculation)
        assertEquals(base.user, rq.user)
        assertEquals(base.additionalProperties, rq.additionalProperties)
    }

    @Test
    fun `RequestyParams to Requesty conversion returns the same instance`() {
        val params = RequestyParams(topP = 0.5)
        assertSame(params, params.toRequestyParams())
    }
}
