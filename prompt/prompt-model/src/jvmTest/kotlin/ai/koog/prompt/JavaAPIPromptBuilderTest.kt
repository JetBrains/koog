package ai.koog.prompt

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ToolCallBuilder
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * Tests for @JavaAPI methods in PromptBuilder class.
 * These tests verify that Java-facing API methods work correctly.
 */
class JavaAPIPromptBuilderTest {
    companion object {
        val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

        val testClock: KoogClock = KoogClock { ts }

        const val promptId = "test-id"
        const val systemMessageText = "You are a helpful assistant"
        const val userMessageText = "Hello, how are you?"
        const val assistantMessageText = "I'm doing well, thank you!"
    }

    @Test
    fun testSystemStringMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .system(systemMessageText)
            .build()

        assertEquals(1, prompt.messages.size)
        val systemMessage = assertIs<Message.System>(prompt.messages[0])
        assertEquals(1, systemMessage.parts.size)
        val systemTextPart = assertIs<MessagePart.Text>(systemMessage.parts[0])
        assertEquals(systemMessageText, systemTextPart.text)
    }

    @Test
    fun testSystemWithTextContentBuilderMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .system {
                text("First part")
                text("Second part")
            }
            .build()

        assertEquals(1, prompt.messages.size)
        val systemMessage = assertIs<Message.System>(prompt.messages[0])
        assertEquals(1, systemMessage.parts.size)
        val systemTextPart = assertIs<MessagePart.Text>(systemMessage.parts[0])
        assertEquals("First partSecond part", systemTextPart.text)
    }

    @Test
    fun testUserListMethod() {
        val parts = listOf(MessagePart.Text("Hello"))
        val prompt = Prompt.builder(promptId, testClock)
            .user(parts)
            .build()

        assertEquals(1, prompt.messages.size)
        val userMessage = assertIs<Message.User>(prompt.messages[0])
        val userTextPart = assertIs<MessagePart.Text>(userMessage.parts[0])
        assertEquals("Hello", userTextPart.text)
    }

    @Test
    fun testUserStringMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .user(userMessageText)
            .build()

        assertEquals(1, prompt.messages.size)
        val userMessage = assertIs<Message.User>(prompt.messages[0])
        val userTextPart = assertIs<MessagePart.Text>(userMessage.parts[0])
        assertEquals(userMessageText, userTextPart.text)
    }

    @Test
    fun testUserWithContentPartsBuilderMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .user {
                text("Hello")
                text(" World")
            }
            .build()

        assertEquals(1, prompt.messages.size)
        val userMessage = assertIs<Message.User>(prompt.messages[0])
        val userTextPart = assertIs<MessagePart.Text>(userMessage.parts[0])
        assertEquals("Hello World", userTextPart.text)
    }

    @Test
    fun testAssistantStringMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .assistant(assistantMessageText)
            .build()

        assertEquals(1, prompt.messages.size)
        val assistantMessage = assertIs<Message.Assistant>(prompt.messages[0])
        val assistantTextPart = assertIs<MessagePart.Text>(assistantMessage.parts[0])
        assertEquals(assistantMessageText, assistantTextPart.text)
    }

    @Test
    fun testAssistantWithTextContentBuilderMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .assistant {
                text("Part 1")
                text("Part 2")
            }
            .build()

        assertEquals(1, prompt.messages.size)
        val assistantMessage = assertIs<Message.Assistant>(prompt.messages[0])
        val assistantTextPart = assertIs<MessagePart.Text>(assistantMessage.parts[0])
        assertEquals("Part 1Part 2", assistantTextPart.text)
    }

    @Test
    fun testMessageMethod() {
        val systemMsg = Message.System(systemMessageText, RequestMetaInfo.create(testClock))
        val prompt = Prompt.builder(promptId, testClock)
            .message(systemMsg)
            .build()

        assertEquals(1, prompt.messages.size)
        val systemMessage = assertIs<Message.System>(prompt.messages[0])
        val systemTextPart = assertIs<MessagePart.Text>(systemMessage.parts[0])
        assertEquals(systemMessageText, systemTextPart.text)
    }

    @Test
    fun testMessagesMethod() {
        val messages = listOf(
            Message.System(systemMessageText, RequestMetaInfo.create(testClock)),
            Message.User(userMessageText, RequestMetaInfo.create(testClock))
        )
        val prompt = Prompt.builder(promptId, testClock)
            .messages(messages)
            .build()

        assertEquals(2, prompt.messages.size)

        val systemMessage = assertIs<Message.System>(prompt.messages[0])
        assertEquals(1, systemMessage.parts.size)
        val systemTextPart = assertIs<MessagePart.Text>(systemMessage.parts[0])
        assertEquals(systemMessageText, systemTextPart.text)

        val userMessage = assertIs<Message.User>(prompt.messages[1])
        assertEquals(1, userMessage.parts.size)
        val userMessagePart = assertIs<MessagePart.Text>(userMessage.parts[0])
        assertEquals(userMessageText, userMessagePart.text)
    }

    @Test
    fun testToolCallMethod() {
        val toolCallId = "call-123"
        val toolName = "calculator"
        val toolArgs = JsonObject(
            mapOf("op" to JsonPrimitive("add"), "a" to JsonPrimitive(1), "b" to JsonPrimitive(2))
        )

        val builder = PromptBuilder(promptId, clock = testClock)
        val toolCall = ToolCallBuilder().id(toolCallId).tool(toolName).args(toolArgs).build()

        val prompt = builder.assistant { toolCall(toolCall) }.build()

        assertEquals(1, prompt.messages.size)
        val assistantMessage = assertIs<Message.Assistant>(prompt.messages[0])
        assertEquals(1, assistantMessage.parts.size)
        val toolCallPart = assertIs<MessagePart.Tool.Call>(assistantMessage.parts[0])
        assertEquals(toolCall, toolCallPart)
    }

    @Test
    fun testToolResultMethod() {
        val toolCallId = "call-123"
        val toolName = "calculator"
        val toolArgs = JsonObject(
            mapOf("op" to JsonPrimitive("add"), "a" to JsonPrimitive(1), "b" to JsonPrimitive(2))
        )
        val toolOutput = "3"

        val builder = PromptBuilder(promptId, clock = testClock)
        builder.assistant { toolCall(id = toolCallId, tool = toolName, args = toolArgs) }
        builder.user { toolResult(id = toolCallId, tool = toolName, output = toolOutput) }

        val prompt = builder.build()

        assertEquals(2, prompt.messages.size)
        val callMsg = assertIs<Message.Assistant>(prompt.messages[0])
        assertEquals(1, callMsg.parts.size)
        val callPart = assertIs<MessagePart.Tool.Call>(callMsg.parts[0])
        assertEquals(toolCallId, callPart.id)
        assertEquals(toolName, callPart.tool)

        val resultMsg = assertIs<Message.User>(prompt.messages[1])
        assertEquals(1, resultMsg.parts.size)
        val result = assertIs<MessagePart.Tool.Result>(resultMsg.parts[0])
        assertEquals(toolCallId, result.id)
        assertEquals(toolName, result.tool)
        assertEquals(toolOutput, result.output)
    }

    @Test
    fun testChainedJavaAPIMethodCalls() {
        // Test that all @JavaAPI methods can be chained together
        val prompt = Prompt.builder(promptId, testClock)
            .system(systemMessageText)
            .user(userMessageText)
            .assistant(assistantMessageText)
            .user(listOf(MessagePart.Text("Another message")))
            .message(Message.User("Yet another", RequestMetaInfo.create(testClock)))
            .build()

        assertEquals(5, prompt.messages.size)
        assertIs<Message.System>(prompt.messages[0])
        assertIs<Message.User>(prompt.messages[1])
        assertIs<Message.Assistant>(prompt.messages[2])
        assertIs<Message.User>(prompt.messages[3])
        assertIs<Message.User>(prompt.messages[4])
    }

    @Test
    fun testPromptBuilderUsesProvidedClock() {
        val builder = PromptBuilder(promptId, clock = testClock)
        val prompt = builder.system(systemMessageText).build()
        assertEquals(ts, prompt.messages[0].metaInfo.timestamp)
    }

    @Test
    fun testJavaStyleComplexPromptBuilding() {
        // Test the exact pattern used in the Java example project
        // This matches the pattern from KoogAgentService.java line 156-166
        val prompt = Prompt.builder("id")
            .system("system")
            .user("user")
            .assistant("assistant")
            .user("user")
            .assistant("assistant")
            .assistant {
                toolCall(id = "id-1", tool = "tool-1", args = JsonObject(mapOf("arg" to JsonPrimitive("value1"))))
            }
            .user {
                toolResult(id = "id-1", tool = "tool-1", output = "123")
            }
            .assistant {
                toolCall(id = "id-2", tool = "tool-2", args = JsonObject(mapOf("arg" to JsonPrimitive("value2"))))
            }
            .user {
                toolResult(id = "id-2", tool = "tool-2", output = "1234")
            }
            .build()

        // Verify the structure: 5 regular messages + 4 tool messages = 9 total
        assertEquals(9, prompt.messages.size)

        // Verify message types and order
        val msg0 = assertIs<Message.System>(prompt.messages[0])
        assertEquals("system", assertIs<MessagePart.Text>(msg0.parts[0]).text)

        val msg1 = assertIs<Message.User>(prompt.messages[1])
        assertEquals("user", assertIs<MessagePart.Text>(msg1.parts[0]).text)

        val msg2 = assertIs<Message.Assistant>(prompt.messages[2])
        assertEquals("assistant", assertIs<MessagePart.Text>(msg2.parts[0]).text)

        val msg3 = assertIs<Message.User>(prompt.messages[3])
        assertEquals("user", assertIs<MessagePart.Text>(msg3.parts[0]).text)

        val msg4 = assertIs<Message.Assistant>(prompt.messages[4])
        assertEquals("assistant", assertIs<MessagePart.Text>(msg4.parts[0]).text)

        // First tool call and result
        val callMsg1 = assertIs<Message.Assistant>(prompt.messages[5])
        assertEquals(1, callMsg1.parts.size)
        val toolCall1 = assertIs<MessagePart.Tool.Call>(callMsg1.parts[0])
        assertEquals("id-1", toolCall1.id)
        assertEquals("tool-1", toolCall1.tool)

        val resultMsg1 = assertIs<Message.User>(prompt.messages[6])
        assertEquals(1, resultMsg1.parts.size)
        val toolResult1 = assertIs<MessagePart.Tool.Result>(resultMsg1.parts[0])
        assertEquals("id-1", toolResult1.id)
        assertEquals("tool-1", toolResult1.tool)
        assertEquals("123", toolResult1.output)

        // Second tool call and result
        val callMsg2 = assertIs<Message.Assistant>(prompt.messages[7])
        assertEquals(1, callMsg2.parts.size)
        val toolCall2 = assertIs<MessagePart.Tool.Call>(callMsg2.parts[0])
        assertEquals("id-2", toolCall2.id)
        assertEquals("tool-2", toolCall2.tool)

        val resultMsg2 = assertIs<Message.User>(prompt.messages[8])
        assertEquals(1, resultMsg2.parts.size)
        val toolResult2 = assertIs<MessagePart.Tool.Result>(resultMsg2.parts[0])
        assertEquals("id-2", toolResult2.id)
        assertEquals("tool-2", toolResult2.tool)
        assertEquals("1234", toolResult2.output)
    }
}
