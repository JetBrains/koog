package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.mock.MockEventBodyField
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
}
