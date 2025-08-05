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

class ChoiceEventTest {

    //region Attributes

    @Test
    fun `test choice attributes verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val choiceEvent = ChoiceEvent(
            provider = llmProvider,
            message = expectedMessage,
            verbose = false,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, choiceEvent.attributes.size)
        assertContentEquals(expectedAttributes, choiceEvent.attributes)
    }

    @Test
    fun `test choice attributes verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val choiceEvent = ChoiceEvent(
            provider = llmProvider,
            message = expectedMessage,
            verbose = true,
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
    fun `test assistant message with verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test assistant message with finish reason verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent, "stop")

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.FinishReason("stop")
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test tool call message verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test assistant message verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.Message(
                role = null,
                content = expectedContent
            )
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test tool call message verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage))
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    //endregion Body Fields

    //region Arguments Tests

    @Test
    fun `test assistant message verbose true with arguments`() {
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
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.Message(
                role = null,
                content = expectedContent
            ),
            EventBodyFields.Arguments(args)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test assistant message verbose false with arguments`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestAssistantMessage(expectedContent)
        val args = buildJsonObject { put("x", "y") }

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0)
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test tool call message verbose true ignores arguments`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)
        val args = buildJsonObject { put("ignored", true) }

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0),
            EventBodyFields.ToolCalls(tools = listOf(expectedMessage))
        )

        assertEquals(expectedBodyFields.size, choiceEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, choiceEvent.bodyFields)
    }

    @Test
    fun `test tool call message verbose false ignores arguments`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestToolCallMessage("test-id", "test-tool", expectedContent)
        val args = buildJsonObject { put("ignored", true) }

        val choiceEvent = ChoiceEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            arguments = args,
            verbose = false,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Index(0)
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
