package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.mock.MockAttribute
import ai.koog.agents.features.opentelemetry.mock.MockGenAIAgentEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GenAIAgentEventTest {

    @Test
    fun `default name should be gen_ai`() {
        val event = object : GenAIAgentEvent() { }
        assertEquals("gen_ai", event.name)
    }

    @Test
    fun `custom name should override default`() {
        val customName = "custom_event"
        val event = MockGenAIAgentEvent(name = customName)

        assertEquals(customName, event.name)
    }

    @Test
    fun `attributes should be accessible`() {
        val attributes = listOf(
            MockAttribute("key1", "value1"),
            MockAttribute("key2", 42),
            MockAttribute("key3", true)
        )

        val event = MockGenAIAgentEvent()
        event.addAttributes(attributes)

        assertEquals(attributes, event.attributes)
        assertEquals(3, event.attributes.size)
        assertEquals("key1", event.attributes[0].key)
        assertEquals("value1", event.attributes[0].value)
        assertEquals("key2", event.attributes[1].key)
        assertEquals(42, event.attributes[1].value)
        assertEquals("key3", event.attributes[2].key)
        assertEquals(true, event.attributes[2].value)
    }

    @Test
    fun `concatName should concatenate names correctly`() {
        val event = MockGenAIAgentEvent()

        // Use the extension function from the interface
        val result = with(event) { "base".concatName("extension") }

        assertEquals("base.extension", result)
    }

    @Test
    fun `concatName should handle empty strings`() {
        val event = MockGenAIAgentEvent()

        val result1 = with(event) { "base".concatName("") }
        val result2 = with(event) { "".concatName("extension") }
        val result3 = with(event) { "".concatName("") }

        assertEquals("base.", result1)
        assertEquals(".extension", result2)
        assertEquals(".", result3)
    }
}
