package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
import ai.koog.agents.utils.HiddenString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBodyFieldExtTest {

    @Test
    fun `toCustomAttribute returns CustomAttribute when creator is not specified`() {
        val field: EventBodyField = MockEventBodyField("key", 42)

        val attribute = field.toCustomAttribute()

        assertEquals("key", attribute.key)
        assertEquals(42, attribute.value)
    }

    @Test
    fun `toCustomAttribute uses provided attribute creator parameter`() {
        val bodyField: EventBodyField = MockEventBodyField("key", "value")

        val attribute = bodyField.toCustomAttribute { field ->
            CustomAttribute("custom.${field.key}", "custom.${field.value}")
        }

        assertEquals("custom.key", attribute.key)
        assertEquals("custom.value", attribute.value)
    }

    @Test
    fun `valueString returns a valid JSON for tool calling events`() {
        val functionCallEvent: EventBodyField = MockEventBodyField(
            "key",
            listOf(
                mapOf(
                    "function" to mapOf(
                        "name" to HiddenString("HelloTool"),
                        "arguments" to HiddenString("""{"name" : "Bob"}""")
                    ),
                    "id" to "call_1234567890",
                    "type" to "function"
                )
            )
        )

        val value = functionCallEvent.valueString(true)
        val jsonBuilder = Json { prettyPrint = true }

        assertDoesNotThrow("Invalid JSON") {
            jsonBuilder.parseToJsonElement(value)
        }
    }
}
