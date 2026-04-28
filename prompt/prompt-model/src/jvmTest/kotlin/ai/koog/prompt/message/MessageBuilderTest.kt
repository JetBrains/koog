package ai.koog.prompt.message

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.time.Instant as JavaInstant

class MessageBuilderTest {

    private val javaInstant: JavaInstant = JavaInstant.parse("2024-01-15T10:30:00Z")

    @Test
    fun testUserMessageWithContent() {
        val message = MessageBuilder.user()
            .addText("Hello!")
            .build()

        assertEquals("Hello!", (message.parts[0] as MessagePart.Text).text)
        assertEquals(Message.Role.User, message.role)
    }

    @Test
    fun testUserMessageWithJavaInstant() {
        val message = MessageBuilder.user()
            .addText("Hello!")
            .metaInfo(RequestMetaInfo.fromJavaInstant(javaInstant))
            .build()

        assertEquals("Hello!", (message.parts[0] as MessagePart.Text).text)
        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
    }

    @Test
    fun testUserMessageWithMultipleParts() {
        val message = MessageBuilder.user()
            .addText("Part 1")
            .addText("Part 2")
            .build()

        assertEquals(2, message.parts.size)
        assertEquals("Part 1", (message.parts[0] as MessagePart.Text).text)
        assertEquals("Part 2", (message.parts[1] as MessagePart.Text).text)
    }

    @Test
    fun testUserMessageAddTextAccumulates() {
        val message = MessageBuilder.user()
            .addText("First")
            .addText("Second")
            .build()

        assertEquals(2, message.parts.size)
        assertEquals("First", (message.parts[0] as MessagePart.Text).text)
        assertEquals("Second", (message.parts[1] as MessagePart.Text).text)
    }

    @Test
    fun testUserMessageEmptyFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.user().build()
        }
    }

    @Test
    fun testAssistantMessageWithContent() {
        val message = MessageBuilder.assistant()
            .addText("Hi there!")
            .finishReason("stop")
            .build()

        assertEquals("Hi there!", (message.parts[0] as MessagePart.Text).text)
        assertEquals(Message.Role.Assistant, message.role)
        assertEquals("stop", message.finishReason)
    }

    @Test
    fun testAssistantMessageWithJavaInstant() {
        val message = MessageBuilder.assistant()
            .addText("Response")
            .metaInfo(
                ResponseMetaInfo.builder()
                    .timestamp(javaInstant)
                    .totalTokensCount(100)
                    .inputTokensCount(40)
                    .outputTokensCount(60)
                    .build()
            )
            .build()

        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
        assertEquals(100, message.metaInfo.totalTokensCount)
        assertEquals(40, message.metaInfo.inputTokensCount)
        assertEquals(60, message.metaInfo.outputTokensCount)
    }

    @Test
    fun testAssistantMessageEmptyFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.assistant().build()
        }
    }

    @Test
    fun testSystemMessage() {
        val message = MessageBuilder.system()
            .addText("You are a helpful assistant.")
            .build()

        assertEquals("You are a helpful assistant.", (message.parts[0] as MessagePart.Text).text)
        assertEquals(Message.Role.System, message.role)
    }

    @Test
    fun testSystemMessageWithJavaInstant() {
        val message = MessageBuilder.system()
            .addText("System prompt")
            .metaInfo(RequestMetaInfo.fromJavaInstant(javaInstant))
            .build()

        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
    }

    @Test
    fun testSystemMessageEmptyFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.system().build()
        }
    }

    @Test
    fun testToolCallPart() {
        val toolCall = ToolCallBuilder()
            .id("call_123")
            .tool("search")
            .args(JsonObject(mapOf("query" to JsonPrimitive("hello"))))
            .build()

        assertEquals("call_123", toolCall.id)
        assertEquals("search", toolCall.tool)
        assertEquals(JsonObject(mapOf("query" to JsonPrimitive("hello"))), toolCall.argsJson)
    }

    @Test
    fun testToolCallMissingToolFails() {
        assertFailsWith<IllegalStateException> {
            ToolCallBuilder()
                .args(JsonObject(mapOf()))
                .build()
        }
    }

    @Test
    fun testToolCallMissingArgsFails() {
        assertFailsWith<IllegalStateException> {
            ToolCallBuilder()
                .tool("search")
                .build()
        }
    }

    @Test
    fun testToolResultPart() {
        val toolResult = ToolResultBuilder()
            .id("call_123")
            .tool("search")
            .output("Found 5 results")
            .build()

        assertEquals("call_123", toolResult.id)
        assertEquals("search", toolResult.tool)
        assertEquals("Found 5 results", toolResult.output)
    }

    @Test
    fun testToolResultWithError() {
        val toolResult = ToolResultBuilder()
            .id("call_789")
            .tool("fetch")
            .output("Error occurred")
            .isError(true)
            .build()

        assertEquals(true, toolResult.isError)
    }

    @Test
    fun testToolResultMissingToolFails() {
        assertFailsWith<IllegalStateException> {
            ToolResultBuilder()
                .output("result")
                .build()
        }
    }

    @Test
    fun testReasoningPart() {
        val reasoning = ReasoningBuilder()
            .content(listOf("Let me think about this..."))
            .summary(listOf("Thinking about the problem"))
            .build()

        assertEquals("Let me think about this...", reasoning.content[0])
        assertNotNull(reasoning.summary)
        assertEquals("Thinking about the problem", reasoning.summary.first())
    }

    @Test
    fun testReasoningPartWithAllFields() {
        val reasoning = ReasoningBuilder()
            .id("reasoning_1")
            .encrypted("encrypted_content")
            .content(listOf("Reasoning content"))
            .summary(listOf("Summary"))
            .build()

        assertEquals("reasoning_1", reasoning.id)
        assertEquals("encrypted_content", reasoning.encrypted)
        assertEquals("Reasoning content", reasoning.content[0])
        assertEquals("Summary", reasoning.summary?.first())
    }

    @Test
    fun testReasoningEmptyFails() {
        assertFailsWith<IllegalStateException> {
            ReasoningBuilder().build()
        }
    }

    @Test
    fun testToolCallWithNullId() {
        val toolCall = ToolCallBuilder()
            .tool("search")
            .args(JsonObject(mapOf()))
            .build()

        assertNull(toolCall.id)
    }

    @Test
    fun testRequestMetaInfoBuilder() {
        val metaInfo = RequestMetaInfo.builder()
            .timestamp(javaInstant)
            .build()

        assertEquals(javaInstant.epochSecond, metaInfo.timestamp.epochSeconds)
    }

    @Test
    fun testRequestMetaInfoFromJavaInstant() {
        val metaInfo = RequestMetaInfo.fromJavaInstant(javaInstant)

        assertEquals(javaInstant.epochSecond, metaInfo.timestamp.epochSeconds)
        assertNull(metaInfo.metadata)
    }

    @Test
    fun testResponseMetaInfoBuilder() {
        val metaInfo = ResponseMetaInfo.builder()
            .timestamp(javaInstant)
            .totalTokensCount(100)
            .inputTokensCount(40)
            .outputTokensCount(60)
            .build()

        assertEquals(javaInstant.epochSecond, metaInfo.timestamp.epochSeconds)
        assertEquals(100, metaInfo.totalTokensCount)
        assertEquals(40, metaInfo.inputTokensCount)
        assertEquals(60, metaInfo.outputTokensCount)
    }

    @Test
    fun testResponseMetaInfoFromJavaInstant() {
        val metaInfo = ResponseMetaInfo.fromJavaInstant(
            timestamp = javaInstant,
            totalTokensCount = 50,
            inputTokensCount = 20,
            outputTokensCount = 30
        )

        assertEquals(javaInstant.epochSecond, metaInfo.timestamp.epochSeconds)
        assertEquals(50, metaInfo.totalTokensCount)
    }

    @Test
    fun testRequestMetaInfoBuilderDefaultTimestamp() {
        val metaInfo = RequestMetaInfo.builder().build()
        assertNotNull(metaInfo.timestamp)
    }

    @Test
    fun testResponseMetaInfoBuilderDefaultTimestamp() {
        val metaInfo = ResponseMetaInfo.builder().build()
        assertNotNull(metaInfo.timestamp)
    }
}
