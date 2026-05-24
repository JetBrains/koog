package ai.koog.agents.core.agent.config

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.time.KoogClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant.Companion.fromEpochMilliseconds

class MissingToolsConversionStrategyTest {
    private companion object {
        private val testClock = object : KoogClock {
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

        private val testToolCall = MessagePart.Tool.Call(
            id = "test-call-id",
            tool = "test-tool",
            args = """{"param": "value"}""",
        )

        private val anotherToolCall = MessagePart.Tool.Call(
            id = "another-call-id",
            tool = "another-tool",
            args = """{"param": "another-value"}""",
        )

        private val testToolResult = MessagePart.Tool.Result(
            id = "test-call-id",
            tool = "test-tool",
            output = "Test result content",
        )

        private val anotherToolResult = MessagePart.Tool.Result(
            id = "another-call-id",
            tool = "another-tool",
            output = "Another test result content",
        )

        private val regularUserMessage = Message.User(
            content = "Regular message content",
            metaInfo = RequestMetaInfo.create(testClock),
        )
    }

    @Test
    fun testConvertMessageWithToolCall() {
        val testPrompt = prompt("test-prompt") {
            assistant {
                toolCall(testToolCall)
            }
        }

        val result = allStrategy.convertPrompt(testPrompt, emptyList())
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        val message = assertIs<Message.Assistant>(result.messages.single())
        val textMessage = assertIs<MessagePart.Text>(message.parts.single())

        assertEquals(expectedContent, textMessage.text)
    }

    @Test
    fun testConvertMessageWithToolResult() {
        val testPrompt = prompt("test-prompt") {
            user {
                toolResult(testToolResult)
            }
        }

        val result = allStrategy.convertPrompt(testPrompt, emptyList())
        val expectedContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        val message = assertIs<Message.User>(result.messages.single())
        val textPart = assertIs<MessagePart.Text>(message.parts.single())

        assertEquals(expectedContent, textPart.text)
    }

    @Test
    fun testConvertMessageWithRegularMessage() {
        val result = allStrategy.convertMessage(regularUserMessage, emptyList())

        assertEquals(regularUserMessage, result)
    }

    @Test
    fun testAllStrategyConvertPrompt() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            assistant {
                toolCall(testToolCall)
            }
            user {
                toolResult(testToolResult)
            }
        }

        val result = allStrategy.convertPrompt(testPrompt, listOf(testToolDescriptor))

        val messages = result.messages
        val expectedToolCallContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"
        val expectedToolResultContent =
            "{\"tool_call_id\":\"test-call-id\",\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        val userMsg = assertIs<Message.User>(messages[0])
        assertEquals("User message", assertIs<MessagePart.Text>(userMsg.parts.single()).text)

        val assistantMsg = assertIs<Message.Assistant>(messages[1])
        assertEquals("Assistant message", assertIs<MessagePart.Text>(assistantMsg.parts.single()).text)

        val toolCallMsg = assertIs<Message.Assistant>(messages[2])
        assertEquals(expectedToolCallContent, assertIs<MessagePart.Text>(toolCallMsg.parts.single()).text)

        val toolResultMsg = assertIs<Message.User>(messages[3])
        assertEquals(expectedToolResultContent, assertIs<MessagePart.Text>(toolResultMsg.parts.single()).text)
    }

    @Test
    fun testMissingStrategyConvertPromptWithMissingTool() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            assistant {
                toolCall(testToolCall)
            }
            user {
                toolResult(testToolResult)
            }
            assistant {
                toolCall(anotherToolCall)
            }
            user {
                toolResult(anotherToolResult)
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
        val userMsg = assertIs<Message.User>(messages[0])
        assertEquals("User message", assertIs<MessagePart.Text>(userMsg.parts.single()).text)

        val assistantMsg = assertIs<Message.Assistant>(messages[1])
        assertEquals("Assistant message", assertIs<MessagePart.Text>(assistantMsg.parts.single()).text)

        // testToolCall and testToolResult should remain as tool messages
        val testCallMsg = assertIs<Message.Assistant>(messages[2])
        val testCallPart = assertIs<MessagePart.Tool.Call>(testCallMsg.parts.single())
        assertEquals("test-tool", testCallPart.tool)

        val testResultMsg = assertIs<Message.User>(messages[3])
        val testResultPart = assertIs<MessagePart.Tool.Result>(testResultMsg.parts.single())
        assertEquals("test-tool", testResultPart.tool)

        // anotherToolCall and anotherToolResult should be converted to text
        val anotherCallMsg = assertIs<Message.Assistant>(messages[4])
        assertEquals(expectedAnotherToolCallContent, assertIs<MessagePart.Text>(anotherCallMsg.parts.single()).text)

        val anotherResultMsg = assertIs<Message.User>(messages[5])
        assertEquals(expectedAnotherToolResultContent, assertIs<MessagePart.Text>(anotherResultMsg.parts.single()).text)
    }

    @Test
    fun testMissingStrategyConvertPromptWithAllToolsPresent() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            assistant {
                toolCall(testToolCall)
            }
            user {
                toolResult(testToolResult)
            }
            assistant {
                toolCall(anotherToolCall)
            }
            user {
                toolResult(anotherToolResult)
            }
        }

        val result = missingStrategy.convertPrompt(testPrompt, listOf(testToolDescriptor, anotherToolDescriptor))

        val messages = result.messages
        assertEquals(6, messages.size)

        val callMsg1 = assertIs<Message.Assistant>(messages[2])
        assertIs<MessagePart.Tool.Call>(callMsg1.parts.single())

        val resultMsg1 = assertIs<Message.User>(messages[3])
        assertIs<MessagePart.Tool.Result>(resultMsg1.parts.single())

        val callMsg2 = assertIs<Message.Assistant>(messages[4])
        assertIs<MessagePart.Tool.Call>(callMsg2.parts.single())

        val resultMsg2 = assertIs<Message.User>(messages[5])
        assertIs<MessagePart.Tool.Result>(resultMsg2.parts.single())
    }

    @Test
    fun testMissingStrategyConvertPromptWithEmptyTools() {
        val testPrompt = prompt("test-prompt") {
            user("User message")
            assistant("Assistant message")
            assistant {
                toolCall(testToolCall)
            }
            user {
                toolResult(testToolResult)
            }
            assistant {
                toolCall(anotherToolCall)
            }
            user {
                toolResult(anotherToolResult)
            }
        }

        // empty tools
        val result = missingStrategy.convertPrompt(testPrompt, emptyList())
        val messages = result.messages

        val callMsg1 = assertIs<Message.Assistant>(messages[2])
        assertIs<MessagePart.Text>(callMsg1.parts.single())

        val resultMsg1 = assertIs<Message.User>(messages[3])
        assertIs<MessagePart.Text>(resultMsg1.parts.single())

        val callMsg2 = assertIs<Message.Assistant>(messages[4])
        assertIs<MessagePart.Text>(callMsg2.parts.single())

        val resultMsg2 = assertIs<Message.User>(messages[5])
        assertIs<MessagePart.Text>(resultMsg2.parts.single())
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
        val nullIdToolCall = MessagePart.Tool.Call(
            id = null,
            tool = "test-tool",
            args = """{"param": "value"}""",
        )
        val testPrompt = prompt("test-prompt") {
            assistant {
                toolCall(nullIdToolCall)
            }
        }

        val result = allStrategy.convertPrompt(testPrompt, emptyList())
        val expectedContent = "{\"tool_name\":\"test-tool\",\"tool_args\":{\"param\":\"value\"}}"

        val message = assertIs<Message.Assistant>(result.messages.single())
        assertEquals(expectedContent, assertIs<MessagePart.Text>(message.parts.single()).text)
    }

    @Test
    fun testNullIdToolResult() {
        val nullIdToolResult = MessagePart.Tool.Result(
            id = null,
            tool = "test-tool",
            output = "Test result content",
        )
        val testPrompt = prompt("test-prompt") {
            user {
                toolResult(nullIdToolResult)
            }
        }

        val result = allStrategy.convertPrompt(testPrompt, emptyList())
        val expectedContent = "{\"tool_name\":\"test-tool\",\"tool_result\":\"Test result content\"}"

        val message = assertIs<Message.User>(result.messages.single())
        assertEquals(expectedContent, assertIs<MessagePart.Text>(message.parts.single()).text)
    }
}
