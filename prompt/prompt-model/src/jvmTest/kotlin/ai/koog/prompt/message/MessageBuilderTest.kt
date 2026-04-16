package ai.koog.prompt.message

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
            .content("Hello!")
            .build()

        assertEquals("Hello!", message.content)
        assertEquals(Message.Role.User, message.role)
    }

    @Test
    fun testUserMessageWithJavaInstant() {
        val message = MessageBuilder.user()
            .content("Hello!")
            .timestamp(javaInstant)
            .build()

        assertEquals("Hello!", message.content)
        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
    }

    @Test
    fun testUserMessageWithMultipleParts() {
        val message = MessageBuilder.user()
            .addPart(ContentPart.Text("Part 1"))
            .addPart(ContentPart.Text("Part 2"))
            .build()

        assertEquals(2, message.parts.size)
        assertEquals("Part 1\nPart 2", message.content)
    }

    @Test
    fun testUserMessageContentReplacesParts() {
        val message = MessageBuilder.user()
            .addPart(ContentPart.Text("Old"))
            .content("New")
            .build()

        assertEquals(1, message.parts.size)
        assertEquals("New", message.content)
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
            .content("Hi there!")
            .finishReason("stop")
            .build()

        assertEquals("Hi there!", message.content)
        assertEquals(Message.Role.Assistant, message.role)
        assertEquals("stop", message.finishReason)
    }

    @Test
    fun testAssistantMessageWithJavaInstant() {
        val message = MessageBuilder.assistant()
            .content("Response")
            .timestamp(javaInstant)
            .totalTokensCount(100)
            .inputTokensCount(40)
            .outputTokensCount(60)
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
            .content("You are a helpful assistant.")
            .build()

        assertEquals("You are a helpful assistant.", message.content)
        assertEquals(Message.Role.System, message.role)
    }

    @Test
    fun testSystemMessageWithJavaInstant() {
        val message = MessageBuilder.system()
            .content("System prompt")
            .timestamp(javaInstant)
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
    fun testToolCallMessage() {
        val message = MessageBuilder.toolCall()
            .id("call_123")
            .tool("search")
            .content("{\"query\": \"hello\"}")
            .build()

        assertEquals("call_123", message.id)
        assertEquals("search", message.tool)
        assertEquals("{\"query\": \"hello\"}", message.content)
        assertEquals(Message.Role.Tool, message.role)
    }

    @Test
    fun testToolCallMessageWithJavaInstant() {
        val message = MessageBuilder.toolCall()
            .id("call_456")
            .tool("calculate")
            .content("{\"expression\": \"2+2\"}")
            .timestamp(javaInstant)
            .build()

        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
    }

    @Test
    fun testToolCallMessageMissingToolFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.toolCall()
                .content("content")
                .build()
        }
    }

    @Test
    fun testToolCallMessageEmptyContentFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.toolCall()
                .tool("search")
                .build()
        }
    }

    @Test
    fun testToolResultMessage() {
        val message = MessageBuilder.toolResult()
            .id("call_123")
            .tool("search")
            .content("Found 5 results")
            .build()

        assertEquals("call_123", message.id)
        assertEquals("search", message.tool)
        assertEquals("Found 5 results", message.content)
        assertEquals(Message.Role.Tool, message.role)
    }

    @Test
    fun testToolResultMessageWithJavaInstant() {
        val message = MessageBuilder.toolResult()
            .id("call_789")
            .tool("fetch")
            .content("Data retrieved")
            .timestamp(javaInstant)
            .build()

        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
    }

    @Test
    fun testToolResultMessageMissingToolFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.toolResult()
                .content("result")
                .build()
        }
    }

    @Test
    fun testReasoningMessage() {
        val message = MessageBuilder.reasoning()
            .content("Let me think about this...")
            .summary("Thinking about the problem")
            .build()

        assertEquals("Let me think about this...", message.content)
        assertEquals(Message.Role.Reasoning, message.role)
        assertNotNull(message.summary)
        assertEquals("Thinking about the problem", message.summary?.first()?.text)
    }

    @Test
    fun testReasoningMessageWithAllFields() {
        val message = MessageBuilder.reasoning()
            .id("reasoning_1")
            .encrypted("encrypted_content")
            .content("Reasoning content")
            .summary("Summary")
            .timestamp(javaInstant)
            .totalTokensCount(50)
            .build()

        assertEquals("reasoning_1", message.id)
        assertEquals("encrypted_content", message.encrypted)
        assertEquals("Reasoning content", message.content)
        assertEquals(javaInstant.epochSecond, message.metaInfo.timestamp.epochSeconds)
        assertEquals(50, message.metaInfo.totalTokensCount)
    }

    @Test
    fun testReasoningMessageEmptyFails() {
        assertFailsWith<IllegalStateException> {
            MessageBuilder.reasoning().build()
        }
    }

    @Test
    fun testToolCallWithNullId() {
        val message = MessageBuilder.toolCall()
            .tool("search")
            .content("query")
            .build()

        assertNull(message.id)
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
