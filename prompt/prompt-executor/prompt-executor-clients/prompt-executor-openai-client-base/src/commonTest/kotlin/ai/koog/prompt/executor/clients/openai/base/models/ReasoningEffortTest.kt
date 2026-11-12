package ai.koog.prompt.executor.clients.openai.base.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ReasoningEffortTest {
    @Test
    fun testExtendedReasoningEffortsRoundTrip() {
        val efforts = mapOf(
            ReasoningEffort.XHIGH to "xhigh",
            ReasoningEffort.MAX to "max",
        )

        efforts.forEach { (effort, wireValue) ->
            val encoded = Json.encodeToString(ReasoningEffort.serializer(), effort)

            assertEquals("\"$wireValue\"", encoded)
            assertEquals(effort, Json.decodeFromString(ReasoningEffort.serializer(), encoded))
        }
    }
}
