package ai.koog.agents.core.agent.config

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingToolsConversionStrategyTest {
    private companion object {
        private val testClock = object : Clock {
            override fun now() = fromEpochMilliseconds(123)
        }

        private val allStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        private val missingStrategy = MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

        private val testToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "Test tool description",
            requiredParameters = emptyList(),
        )

        private val anotherToolDescriptor = ToolDescriptor(
            name = "another-tool",
            description = "Another test tool description",
            requiredParameters = emptyList(),
        )

        private val testToolCall = Message.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            content = """{"param": "value"}""",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        private val anotherToolCall = Message.Tool.Call(
            id = "another-call-id",
            tool = "another-tool",
            content = """{"param": "another-value"}""",
            metaInfo = ResponseMetaInfo.create(testClock),
        )

        private val testToolResult = Message.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            content = "Test result content",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        private val anotherToolResult = Message.Tool.Result(
            id = "another-call-id",
            tool = "another-tool",
            content = "Another test result content",
            metaInfo = RequestMetaInfo.create(testClock),
        )

        private val regularMessage = Message.User(
            content = "Regular message content",
            metaInfo = RequestMetaInfo.create(testClock),
        )
    }

    @Test
    fun testConvertMessageWithToolCall() {
        val result = allStrategy.convertMessage(testToolCall)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        assertEquals(expectedContent, result.content)
    }

    @Test
    fun testConvertMessageWithToolResult() {
        val result = allStrategy.convertMessage(testToolResult)
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertEquals(expectedContent, result.content)
    }

    @Test
    fun testConvertMessageWithRegularMessage() {
        val result = allStrategy.convertMessage(regularMessage)

        assertEquals(regularMessage, result)
    }

    @Test
    fun testAllStrategyConvertPrompt() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            tool {
                call(testToolCall)
                result(testToolResult)
            }
        }

        val result = allStrategy.convertPrompt(testPrompt, listOf(testToolDescriptor))

        val messages = result.messages
        val expectedToolCallContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"
        val expectedToolResultContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertEquals("User message", messages[0].content)
        assertEquals("Assistant message", messages[1].content)
        assertTrue(messages[2] is Message.Assistant)
        assertTrue(messages[3] is Message.User)
        assertEquals(expectedToolCallContent, messages[2].content)
        assertEquals(expectedToolResultContent, messages[3].content)
    }

    @Test
    fun testMissingStrategyConvertPromptWithMissingTool() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            tool {
                call(testToolCall)
                result(testToolResult)
                call(anotherToolCall)
                result(anotherToolResult)
            }
        }

        // include only one tool
        val result = missingStrategy.convertPrompt(testPrompt, listOf(testToolDescriptor))
        val messages = result.messages

        val expectedAnotherToolCallContent =
            "{\"tool_call_id\":\"another-call-id\",\"tool_name\":\"another-tool\",\"tool_args\":{\"param\":\"another-value\"}}"
        val expectedAnotherToolResultContent =
            "{\"tool_call_id\":\"another-call-id\",\"tool_name\":\"another-tool\",\"tool_result\":\"Another test result content\"}"

        // first two messages should remain unchanged
        assertEquals("User message", messages[0].content)
        assertEquals("Assistant message", messages[1].content)

        // testToolCall and testToolResult should remain as tool messages
        assertTrue(messages[2] is Message.Tool.Call)
        assertTrue(messages[3] is Message.Tool.Result)
        assertEquals("test-tool", (messages[2] as Message.Tool.Call).tool)
        assertEquals("test-tool", (messages[3] as Message.Tool.Result).tool)

        assertTrue(messages[4] is Message.Assistant)
        assertTrue(messages[5] is Message.User)
        assertEquals(expectedAnotherToolCallContent, messages[4].content)
        assertEquals(expectedAnotherToolResultContent, messages[5].content)
    }

    @Test
    fun testMissingStrategyConvertPromptWithAllToolsPresent() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            tool {
                call(testToolCall)
                result(testToolResult)
                call(anotherToolCall)
                result(anotherToolResult)
            }
        }

        val result = missingStrategy.convertPrompt(testPrompt, listOf(testToolDescriptor, anotherToolDescriptor))

        val messages = result.messages
        assertEquals(6, messages.size)

        assertTrue(messages[2] is Message.Tool.Call)
        assertTrue(messages[3] is Message.Tool.Result)
        assertTrue(messages[4] is Message.Tool.Call)
        assertTrue(messages[5] is Message.Tool.Result)
    }

    @Test
    fun testMissingStrategyConvertPromptWithEmptyTools() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            tool {
                call(testToolCall)
                result(testToolResult)
                call(anotherToolCall)
                result(anotherToolResult)
            }
        }

        // empty tools
        val result = missingStrategy.convertPrompt(testPrompt, emptyList())
        val messages = result.messages

        assertTrue(messages[2] is Message.Assistant)
        assertTrue(messages[3] is Message.User)
        assertTrue(messages[4] is Message.Assistant)
        assertTrue(messages[5] is Message.User)
    }

    @Test
    fun testEmptyPrompt() {
        val emptyPrompt = prompt("empty-prompt") {}

        val allStrategyResult = allStrategy.convertPrompt(emptyPrompt, listOf(testToolDescriptor))
        val missingStrategyResult = missingStrategy.convertPrompt(emptyPrompt, listOf(testToolDescriptor))

        assertTrue(allStrategyResult.messages.isEmpty())
        assertTrue(missingStrategyResult.messages.isEmpty())
    }

    @Test
    fun testNullIdToolCall() {
        val nullIdToolCall = Message.Tool.Call(
            id = null,
            tool = "test-tool",
            content = """{"param": "value"}""",
            metaInfo = ResponseMetaInfo.create(testClock)
        )

        val result = allStrategy.convertMessage(nullIdToolCall)
        val expectedContent = "{\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        assertTrue(result is Message.Assistant)
        assertEquals(expectedContent, result.content)
    }

    @Test
    fun testNullIdToolResult() {
        val nullIdToolResult = Message.Tool.Result(
            id = null,
            tool = "test-tool",
            content = "Test result content",
            metaInfo = RequestMetaInfo.create(testClock)
        )

        val result = allStrategy.convertMessage(nullIdToolResult)
        val expectedContent = "{\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        assertTrue(result is Message.User)
        assertEquals(expectedContent, result.content)
    }
}
