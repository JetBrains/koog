package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ToolMessageEventTest {

    //region Attributes

    @Test
    fun `test tool message attributes`() {
        val toolCallId = "test-id"
        val toolResult = createTestToolResult("Test result")
        val llmProvider = MockLLMProvider()

        val toolMessageEvent = ToolMessageEvent(
            provider = llmProvider,
            toolCallId = toolCallId,
            content = toolResult.toStringDefault(),
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, toolMessageEvent.attributes.size)
        assertContentEquals(expectedAttributes, toolMessageEvent.attributes)
    }

    //endregion Attributes

    //region Body Fields

    @Test
    fun `test tool message body fields with id`() {
        val toolCallId = "test-id"
        val toolResult = createTestToolResult("Test result")

        val toolMessageEvent = ToolMessageEvent(
            provider = MockLLMProvider(),
            toolCallId = toolCallId,
            content = toolResult.toStringDefault(),
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.Content(content = toolResult.toStringDefault()),
            EventBodyFields.Id(id = toolCallId)
        )

        assertEquals(expectedBodyFields.size, toolMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, toolMessageEvent.bodyFields)
    }

    @Test
    fun `test tool message body fields without id`() {
        val toolCallId = null
        val toolResult = createTestToolResult("Test result")

        val toolMessageEvent = ToolMessageEvent(
            provider = MockLLMProvider(),
            toolCallId = toolCallId,
            content = toolResult.toStringDefault(),
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.Content(content = toolResult.toStringDefault())
        )

        assertEquals(expectedBodyFields.size, toolMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, toolMessageEvent.bodyFields)
    }

    //endregion Body Fields

    //region Private Methods

    private fun createTestToolResult(content: String): ToolResult = object : ToolResult {
        override fun toStringDefault(): String = content
    }

    //endregion Private Methods
}
