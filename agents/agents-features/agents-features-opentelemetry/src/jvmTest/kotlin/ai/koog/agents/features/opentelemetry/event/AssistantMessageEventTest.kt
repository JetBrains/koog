package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistantMessageEventTest {

    //region Attributes

    @Test
    fun `test assistant message attributes verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val assistantMessageEvent = AssistantMessageEvent(
            provider = llmProvider,
            message = expectedMessage,
            verbose = false,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, assistantMessageEvent.attributes.size)
        assertContentEquals(expectedAttributes, assistantMessageEvent.attributes)
    }

    @Test
    fun `test assistant message attributes verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val assistantMessageEvent = AssistantMessageEvent(
            provider = llmProvider,
            message = expectedMessage,
            verbose = false,
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
    fun `test assistant message with verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = false,
        )

        assertTrue(
            assistantMessageEvent.bodyFields.isEmpty(),
            "No content message should be added with verbose set to 'false'"
        )
    }

    @Test
    fun `test tool call message verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Tool)
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    @Test
    fun `test assistant message verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Content(content = expectedContent)
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    @Test
    fun `test tool call message verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)

        val assistantMessageEvent = AssistantMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage))
        )

        assertEquals(expectedBodyFields.size, assistantMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, assistantMessageEvent.bodyFields)
    }

    //endregion Body Fields

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
