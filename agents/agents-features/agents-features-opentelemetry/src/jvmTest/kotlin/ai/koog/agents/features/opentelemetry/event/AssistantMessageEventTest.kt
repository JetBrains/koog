package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AssistantMessageEventTest {

    //region Attributes

    @Test
    fun `test assistant message attributes`() {
        val expectedMessage = createTestAssistantMessage("Test message")
        val llmProvider = MockLLMProvider()

        val assistantMessageEvent = AssistantMessageEvent(
            provider = llmProvider,
            message = expectedMessage
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, assistantMessageEvent.attributes.size)
        assertContentEquals(expectedAttributes, assistantMessageEvent.attributes)
    }

    //endregion Attributes

    //region Body Fields

    @Test
    fun `test tool call message`() {
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", "Test message")

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage))
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    @Test
    fun `test assistant message`() {
        val expectedMessage = createTestAssistantMessage("Test message")

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = expectedMessage.role),
            EventBodyFields.Content(content = expectedMessage.content)
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    //endregion Body Fields

    //region Arguments Tests

    @Test
    fun `test assistant message with arguments`() {
        val expectedMessage = createTestAssistantMessage("Test message")
        val args = buildJsonObject {
            put("string", "value")
            put("integer", 42)
        }

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = expectedMessage.role),
            EventBodyFields.Content(content = expectedMessage.content),
            EventBodyFields.Arguments(args)
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    @Test
    fun `test tool call message ignores arguments`() {
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", "Test message")
        val args = buildJsonObject { put("ignored", true) }

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage))
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    //endregion Arguments Tests

    //region Private Methods

    private fun createTestAssistantMessage(content: String): Message.Response = Message.Assistant(
        content = content,
        metaInfo = ResponseMetaInfo(Clock.System.now())
    )

    private fun createTestToolCallMessage(id: String, tool: String, content: String): Message.Tool.Call =
        Message.Tool.Call(
            id = id,
            tool = tool,
            content = content,
            metaInfo = ResponseMetaInfo(Clock.System.now())
        )

    //endregion Private Methods
}
