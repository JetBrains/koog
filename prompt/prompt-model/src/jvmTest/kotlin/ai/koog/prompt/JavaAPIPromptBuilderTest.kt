package ai.koog.prompt

import ai.koog.prompt.dsl.ContentPartsBuilder
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for @JavaAPI methods in PromptBuilder class.
 * These tests verify that Java-facing API methods work correctly.
 */
class JavaAPIPromptBuilderTest {
    companion object {
        val ts: Instant = Instant.parse("2023-01-01T00:00:00Z")

        val testClock: Clock = object : Clock {
            override fun now(): Instant = ts
        }

        const val promptId = "test-id"
        const val systemMessage = "You are a helpful assistant"
        const val userMessage = "Hello, how are you?"
        const val assistantMessage = "I'm doing well, thank you!"
    }

    @Test
    fun testSystemStringMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .system(systemMessage)
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertEquals(systemMessage, prompt.messages[0].content)
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
        assertTrue(prompt.messages[0] is Message.System)
        assertEquals("First partSecond part", prompt.messages[0].content)
    }

    @Test
    fun testUserListMethod() {
        val parts = listOf(ContentPart.Text("Hello"))
        val prompt = Prompt.builder(promptId, testClock)
            .user(parts)
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.User)
        assertEquals("Hello", prompt.messages[0].content)
    }

    @Test
    fun testUserStringMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .user(userMessage)
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.User)
        assertEquals(userMessage, prompt.messages[0].content)
    }

    @Test
    fun testUserWithContentAndAttachmentsMethod() {
        @Suppress("DEPRECATION")
        val prompt = Prompt.builder(promptId, testClock)
            .user(userMessage, emptyList())
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.User)
        assertEquals(userMessage, prompt.messages[0].content)
    }

    @Test
    fun testUserWithContentAndBlockMethod() {
        @Suppress("DEPRECATION")
        val prompt = Prompt.builder(promptId, testClock)
            .user("Base message") {
                // Lambda for building parts
            }
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.User)
        assertTrue(prompt.messages[0].content.contains("Base message"))
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
        assertTrue(prompt.messages[0] is Message.User)
        assertEquals("Hello World", prompt.messages[0].content)
    }

    @Test
    fun testAssistantStringMethod() {
        val prompt = Prompt.builder(promptId, testClock)
            .assistant(assistantMessage)
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Assistant)
        assertEquals(assistantMessage, prompt.messages[0].content)
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
        assertTrue(prompt.messages[0] is Message.Assistant)
        assertEquals("Part 1Part 2", prompt.messages[0].content)
    }

    @Test
    fun testMessageMethod() {
        val systemMsg = Message.System(systemMessage, ai.koog.prompt.message.RequestMetaInfo.create(testClock))
        val prompt = Prompt.builder(promptId, testClock)
            .message(systemMsg)
            .build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertEquals(systemMessage, prompt.messages[0].content)
    }

    @Test
    fun testMessagesMethod() {
        val messages = listOf(
            Message.System(systemMessage, ai.koog.prompt.message.RequestMetaInfo.create(testClock)),
            Message.User(userMessage, ai.koog.prompt.message.RequestMetaInfo.create(testClock))
        )
        val prompt = Prompt.builder(promptId, testClock)
            .messages(messages)
            .build()

        assertEquals(2, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertTrue(prompt.messages[1] is Message.User)
        assertEquals(systemMessage, prompt.messages[0].content)
        assertEquals(userMessage, prompt.messages[1].content)
    }

    @Test
    fun testToolCallMethod() {
        val toolCallId = "call-123"
        val toolName = "calculator"
        val toolContent = """{"op":"add","a":1,"b":2}"""

        val builder = PromptBuilder(promptId, clock = testClock)
        val toolBuilder = builder.ToolMessageBuilder(testClock)
        toolBuilder.call(toolCallId, toolName, toolContent)

        val prompt = builder.build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Tool.Call)
        val call = prompt.messages[0] as Message.Tool.Call
        assertEquals(toolCallId, call.id)
        assertEquals(toolName, call.tool)
        assertEquals(toolContent, call.content)
    }

    @Test
    fun testToolCallWithMessageMethod() {
        val toolCallId = "call-123"
        val toolName = "calculator"
        val toolContent = """{"op":"add","a":1,"b":2}"""
        val toolCallMessage = Message.Tool.Call(
            toolCallId,
            toolName,
            toolContent,
            ai.koog.prompt.message.ResponseMetaInfo.create(testClock)
        )

        val builder = PromptBuilder(promptId, clock = testClock)
        val toolBuilder = builder.ToolMessageBuilder(testClock)
        toolBuilder.call(toolCallMessage)

        val prompt = builder.build()

        assertEquals(1, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Tool.Call)
        val call = prompt.messages[0] as Message.Tool.Call
        assertEquals(toolCallId, call.id)
        assertEquals(toolName, call.tool)
        assertEquals(toolContent, call.content)
    }

    @Test
    fun testToolResultMethod() {
        val toolCallId = "call-123"
        val toolName = "calculator"
        val toolContent = """{"op":"add","a":1,"b":2}"""
        val toolResultContent = "3"

        val builder = PromptBuilder(promptId, clock = testClock)
        val toolBuilder = builder.ToolMessageBuilder(testClock)
        toolBuilder.call(toolCallId, toolName, toolContent)
        toolBuilder.result(toolCallId, toolName, toolResultContent)

        val prompt = builder.build()

        assertEquals(2, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Tool.Call)
        assertTrue(prompt.messages[1] is Message.Tool.Result)
        val result = prompt.messages[1] as Message.Tool.Result
        assertEquals(toolCallId, result.id)
        assertEquals(toolName, result.tool)
        assertEquals(toolResultContent, result.content)
    }

    @Test
    fun testToolResultWithMessageMethod() {
        val toolCallId = "call-123"
        val toolName = "calculator"
        val toolContent = """{"op":"add","a":1,"b":2}"""
        val toolResultContent = "3"

        val toolCallMessage = Message.Tool.Call(
            toolCallId,
            toolName,
            toolContent,
            ai.koog.prompt.message.ResponseMetaInfo.create(testClock)
        )
        val toolResultMessage = Message.Tool.Result(
            toolCallId,
            toolName,
            toolResultContent,
            ai.koog.prompt.message.RequestMetaInfo.create(testClock)
        )

        val builder = PromptBuilder(promptId, clock = testClock)
        val toolBuilder = builder.ToolMessageBuilder(testClock)
        toolBuilder.call(toolCallMessage)
        toolBuilder.result(toolResultMessage)

        val prompt = builder.build()

        assertEquals(2, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.Tool.Call)
        assertTrue(prompt.messages[1] is Message.Tool.Result)
        val result = prompt.messages[1] as Message.Tool.Result
        assertEquals(toolCallId, result.id)
        assertEquals(toolName, result.tool)
        assertEquals(toolResultContent, result.content)
    }

    @Test
    fun testChainedJavaAPIMethodCalls() {
        // Test that all @JavaAPI methods can be chained together
        val prompt = Prompt.builder(promptId, testClock)
            .system(systemMessage)
            .user(userMessage)
            .assistant(assistantMessage)
            .user(listOf(ContentPart.Text("Another message")))
            .message(Message.User("Yet another", ai.koog.prompt.message.RequestMetaInfo.create(testClock)))
            .build()

        assertEquals(5, prompt.messages.size)
        assertTrue(prompt.messages[0] is Message.System)
        assertTrue(prompt.messages[1] is Message.User)
        assertTrue(prompt.messages[2] is Message.Assistant)
        assertTrue(prompt.messages[3] is Message.User)
        assertTrue(prompt.messages[4] is Message.User)
    }

    @Test
    fun testToolMessageBuilderClass() {
        // Test that ToolMessageBuilder class is accessible and properly annotated
        val builder = PromptBuilder(promptId, clock = testClock)
        val toolBuilder = builder.ToolMessageBuilder(testClock)

        // Verify that the ToolMessageBuilder can be created
        assertEquals(testClock, toolBuilder.clock)
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
            .toolCall("id-1", "tool-1", "args-1")
            .toolResult("id-1", "tool-1", "result-1")
            .toolCall("id-2", "tool-2", "args-2")
            .toolResult("id-2", "tool-2", "result-2")
            .build()

        // Verify the structure: 5 regular messages + 4 tool messages = 9 total
        assertEquals(9, prompt.messages.size)

        // Verify message types and order
        assertTrue(prompt.messages[0] is Message.System)
        assertEquals("system", prompt.messages[0].content)

        assertTrue(prompt.messages[1] is Message.User)
        assertEquals("user", prompt.messages[1].content)

        assertTrue(prompt.messages[2] is Message.Assistant)
        assertEquals("assistant", prompt.messages[2].content)

        assertTrue(prompt.messages[3] is Message.User)
        assertEquals("user", prompt.messages[3].content)

        assertTrue(prompt.messages[4] is Message.Assistant)
        assertEquals("assistant", prompt.messages[4].content)

        // First tool call and result
        assertTrue(prompt.messages[5] is Message.Tool.Call)
        val toolCall1 = prompt.messages[5] as Message.Tool.Call
        assertEquals("id-1", toolCall1.id)
        assertEquals("tool-1", toolCall1.tool)
        assertEquals("args-1", toolCall1.content)

        assertTrue(prompt.messages[6] is Message.Tool.Result)
        val toolResult1 = prompt.messages[6] as Message.Tool.Result
        assertEquals("id-1", toolResult1.id)
        assertEquals("tool-1", toolResult1.tool)
        assertEquals("result-1", toolResult1.content)

        // Second tool call and result
        assertTrue(prompt.messages[7] is Message.Tool.Call)
        val toolCall2 = prompt.messages[7] as Message.Tool.Call
        assertEquals("id-2", toolCall2.id)
        assertEquals("tool-2", toolCall2.tool)
        assertEquals("args-2", toolCall2.content)

        assertTrue(prompt.messages[8] is Message.Tool.Result)
        val toolResult2 = prompt.messages[8] as Message.Tool.Result
        assertEquals("id-2", toolResult2.id)
        assertEquals("tool-2", toolResult2.tool)
        assertEquals("result-2", toolResult2.content)
    }
}
