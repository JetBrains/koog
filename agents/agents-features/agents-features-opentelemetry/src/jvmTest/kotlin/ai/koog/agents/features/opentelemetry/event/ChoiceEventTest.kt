package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChoiceEventTest {

    //region Attributes

    @Test
    fun `test choice attributes`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val choiceEvent = ChoiceEvent(
            provider = llmProvider,
            message = expectedMessage,
            index = 0,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, choiceEvent.attributes.size)
        assertContentEquals(expectedAttributes, choiceEvent.attributes)
    }

    //endregion Attributes

    //region Body Fields

    @Test
    fun `test assistant message with finish reason`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent, "stop")

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            index = 0,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.FinishReason("stop"),
            EventBodyFields.Message(expectedMessage.role, expectedMessage.content)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test tool call message`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            index = 0,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage)),
            EventBodyFields.FinishReason(reason = GenAIAttributes.Response.FinishReasonType.ToolCalls.id)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test assistant message`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            index = 0,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.Message(
                role = expectedMessage.role,
                content = expectedMessage.content
            )
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    //endregion Body Fields

    //region Arguments Tests

    @Test
    fun `test assistant message with arguments`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val args = buildJsonObject {
            put("string", "value")
            put("integer", 42)
        }

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
            index = 0,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.Message(expectedMessage.role, expectedMessage.content),
            EventBodyFields.Arguments(args)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test tool call message ignores arguments`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)
        val args = buildJsonObject { put("ignored", true) }

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
            index = 0,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.Role(role = Message.Role.Tool),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage)),
            EventBodyFields.FinishReason(reason = GenAIAttributes.Response.FinishReasonType.ToolCalls.id)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    //endregion Arguments Tests

    //region Private Methods

    private fun createTestAssistantMessage(content: String, finishReason: String? = null): Message.Assistant =
        Message.Assistant(
            content = content,
            metaInfo = ResponseMetaInfo(Clock.System.now()),
            finishReason = finishReason
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
