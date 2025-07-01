package ai.koog.agents.core.agent.config

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingToolsConversionStrategyTest {

    private val testClock = object : Clock {
        override fun now() = kotlinx.datetime.Instant.fromEpochMilliseconds(1234567890)
    }

    private val testToolDescriptor = ToolDescriptor(
        name = "test-tool",
        description = "Test tool description",
        requiredParameters = emptyList()
    )

    private val anotherToolDescriptor = ToolDescriptor(
        name = "another-tool",
        description = "Another test tool description",
        requiredParameters = emptyList()
    )

    private val testToolCall = Message.Tool.Call(
        id = "test-call-id",
        tool = "test-tool",
        content = """{"param": "value"}""",
        metaInfo = ResponseMetaInfo.create(testClock)
    )

    private val anotherToolCall = Message.Tool.Call(
        id = "another-call-id",
        tool = "another-tool",
        content = """{"param": "another-value"}""",
        metaInfo = ResponseMetaInfo.create(testClock)
    )

    private val testToolResult = Message.Tool.Result(
        id = "test-call-id",
        tool = "test-tool",
        content = "Test result content",
        metaInfo = RequestMetaInfo.create(testClock)
    )

    private val anotherToolResult = Message.Tool.Result(
        id = "another-call-id",
        tool = "another-tool",
        content = "Another test result content",
        metaInfo = RequestMetaInfo.create(testClock)
    )

    private val regularMessage = Message.User(
        content = "Regular message content",
        metaInfo = RequestMetaInfo.create(testClock)
    )

    @Test
    fun testConvertMessageWithToolCall() {
        val strategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val result = strategy.convertMessage(testToolCall)

        // The current implementation returns a JSON string, not a Message.Assistant
        assertTrue(result.content.contains("test-tool"))
        assertTrue(result.content.contains("tool_name"))
    }

    @Test
    fun testConvertMessageWithToolResult() {
        val strategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val result = strategy.convertMessage(testToolResult)

        // The current implementation returns a JSON string, not a Message.User
        assertTrue(result.content.contains("test-tool"))
        assertTrue(result.content.contains("tool_name"))
        assertTrue(result.content.contains("Test result content"))
    }

    @Test
    fun testConvertMessageWithRegularMessage() {
        val strategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val result = strategy.convertMessage(regularMessage)

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

        val strategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val result = strategy.convertPrompt(testPrompt, listOf(testToolDescriptor))

        // All tool calls and results should be converted, regardless of whether they're in the tools list
        val messages = result.messages
        assertEquals(4, messages.size)

        // First two messages should remain unchanged
        assertEquals("User message", messages[0].content)
        assertEquals("Assistant message", messages[1].content)

        // Tool call and result should be converted
        assertTrue(messages[2] is Message.Assistant)
        assertTrue(messages[3] is Message.User)
        assertTrue(messages[2].content.contains("test-tool"))
        assertTrue(messages[3].content.contains("test-tool"))
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

        // Only include one of the tools in the tools list
        val strategy = MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)
        val result = strategy.convertPrompt(testPrompt, listOf(testToolDescriptor))

        val messages = result.messages
        assertEquals(6, messages.size)

        // First two messages should remain unchanged
        assertEquals("User message", messages[0].content)
        assertEquals("Assistant message", messages[1].content)

        // testToolCall and testToolResult should remain as tool messages
        assertTrue(messages[2] is Message.Tool.Call)
        assertTrue(messages[3] is Message.Tool.Result)
        assertEquals("test-tool", (messages[2] as Message.Tool.Call).tool)
        assertEquals("test-tool", (messages[3] as Message.Tool.Result).tool)

        // anotherToolCall and anotherToolResult should be converted to regular messages
        assertTrue(messages[4] is Message.Assistant)
        assertTrue(messages[5] is Message.User)
        assertTrue(messages[4].content.contains("another-tool"))
        assertTrue(messages[5].content.contains("another-tool"))
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

        // Include all tools in the tools list
        val strategy = MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)
        val result = strategy.convertPrompt(testPrompt, listOf(testToolDescriptor, anotherToolDescriptor))

        val messages = result.messages
        assertEquals(6, messages.size)

        // All tool calls and results should remain as tool messages
        assertTrue(messages[2] is Message.Tool.Call)
        assertTrue(messages[3] is Message.Tool.Result)
        assertTrue(messages[4] is Message.Tool.Call)
        assertTrue(messages[5] is Message.Tool.Result)
    }

    @Test
    fun testMissingStrategyConvertPromptWithNoToolsPresent() {
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

        // Include no tools in the tools list
        val strategy = MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)
        val result = strategy.convertPrompt(testPrompt, emptyList())

        val messages = result.messages
        assertEquals(6, messages.size)

        // All tool calls and results should be converted to regular messages
        assertTrue(messages[2] is Message.Assistant)
        assertTrue(messages[3] is Message.User)
        assertTrue(messages[4] is Message.Assistant)
        assertTrue(messages[5] is Message.User)
    }

    @Test
    fun testEdgeCaseEmptyPrompt() {
        val emptyPrompt = prompt("empty-prompt") {}

        val allStrategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val missingStrategy = MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

        val allResult = allStrategy.convertPrompt(emptyPrompt, listOf(testToolDescriptor))
        val missingResult = missingStrategy.convertPrompt(emptyPrompt, listOf(testToolDescriptor))

        assertTrue(allResult.messages.isEmpty())
        assertTrue(missingResult.messages.isEmpty())
    }

    @Test
    fun testEdgeCaseNullToolCallId() {
        val nullIdToolCall = Message.Tool.Call(
            id = null,
            tool = "test-tool",
            content = """{"param": "value"}""",
            metaInfo = ResponseMetaInfo.create(testClock)
        )

        val strategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val result = strategy.convertMessage(nullIdToolCall)

        assertTrue(result is Message.Assistant)
        assertTrue(result.content.contains("test-tool"))
        // Should not throw an exception with null ID
    }

    @Test
    fun testEdgeCaseNullToolResultId() {
        val nullIdToolResult = Message.Tool.Result(
            id = null,
            tool = "test-tool",
            content = "Test result content",
            metaInfo = RequestMetaInfo.create(testClock)
        )

        val strategy = MissingToolsConversionStrategy.All(ToolCallDescriber.JSON)
        val result = strategy.convertMessage(nullIdToolResult)

        assertTrue(result is Message.User)
        assertTrue(result.content.contains("test-tool"))
        assertTrue(result.content.contains("Test result content"))
        // Should not throw an exception with null ID
    }
}