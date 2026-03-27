package ai.koog.agents.features.chathistory.aws

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import aws.sdk.kotlin.services.bedrockagentcore.model.Content
import aws.sdk.kotlin.services.bedrockagentcore.model.Conversational
import aws.sdk.kotlin.services.bedrockagentcore.model.PayloadType
import aws.sdk.kotlin.services.bedrockagentcore.model.Role
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class AgentcoreMessageConverterTest {

    private val randomEventId = UUID.randomUUID().toString()

    // --- messageToPayload tests ---

    @Test
    fun testUserMessageToPayload() {
        val message = Message.User("Hello", RequestMetaInfo.Empty)
        val payload = AgentcoreMessageConverter.messageToPayload(message)

        assertIs<PayloadType.Conversational>(payload)
        assertEquals(Role.User, payload.value.role)
        assertEquals("Hello", (payload.value.content as Content.Text).value)
    }

    @Test
    fun testAssistantMessageToPayload() {
        val message = Message.Assistant("Hi there", ResponseMetaInfo.Empty)
        val payload = AgentcoreMessageConverter.messageToPayload(message)

        assertIs<PayloadType.Conversational>(payload)
        assertEquals(Role.Assistant, payload.value.role)
        assertEquals("Hi there", (payload.value.content as Content.Text).value)
    }

    @Test
    fun testSystemMessageToPayloadReturnsNullWhenIgnored() {
        val message = Message.System("You are a helpful assistant", RequestMetaInfo.Empty)
        assertNull(AgentcoreMessageConverter.messageToPayload(message, ignoreUnsupportedValues = true))
    }

    @Test
    fun testSystemMessageToPayloadThrowsWhenNotIgnored() {
        val message = Message.System("You are a helpful assistant", RequestMetaInfo.Empty)
        assertFailsWith<IllegalStateException> {
            AgentcoreMessageConverter.messageToPayload(message, ignoreUnsupportedValues = false)
        }
    }

    @Test
    fun testToolCallMessageToPayloadReturnsNull() {
        val message = Message.Tool.Call(id = "1", tool = "search", content = "{}", metaInfo = ResponseMetaInfo.Empty)
        assertNull(AgentcoreMessageConverter.messageToPayload(message))
    }

    @Test
    fun testToolResultMessageToPayloadReturnsNull() {
        val message =
            Message.Tool.Result(id = "1", tool = "search", content = "result", metaInfo = RequestMetaInfo.Empty)
        assertNull(AgentcoreMessageConverter.messageToPayload(message))
    }

    @Test
    fun testUserMessageWithAttachmentsExtractsText() {
        val message = Message.User(
            parts = listOf(
                ContentPart.Text("Hello with image"),
                ContentPart.File(
                    content = AttachmentContent.PlainText("file content"),
                    format = "txt",
                    mimeType = "text/plain"
                )
            ),
            metaInfo = RequestMetaInfo.Empty
        )
        val payload = AgentcoreMessageConverter.messageToPayload(message)

        assertIs<PayloadType.Conversational>(payload)
        assertEquals(Role.User, payload.value.role)
        assertEquals("Hello with image", (payload.value.content as Content.Text).value)
    }

    @Test
    fun testAssistantMessageWithMultipleTextPartsAndAttachment() {
        val message = Message.Assistant(
            parts = listOf(
                ContentPart.Text("Part one"),
                ContentPart.File(
                    content = AttachmentContent.PlainText("attachment"),
                    format = "txt",
                    mimeType = "text/plain"
                ),
                ContentPart.Text("Part two")
            ),
            metaInfo = ResponseMetaInfo.Empty
        )
        val payload = AgentcoreMessageConverter.messageToPayload(message)

        assertIs<PayloadType.Conversational>(payload)
        assertEquals(Role.Assistant, payload.value.role)
        assertEquals("Part one\nPart two", (payload.value.content as Content.Text).value)
    }

    // --- conversationalToMessage tests ---

    @Test
    fun testConversationalUserToMessage() {
        val conversational = Conversational.Companion {
            role = Role.User
            content = Content.Text("Hello from user")
        }
        val message = AgentcoreMessageConverter.conversationalToMessage(conversational, randomEventId, Clock.System.now())

        assertIs<Message.User>(message)
        assertEquals("Hello from user", message.content)
    }

    @Test
    fun testConversationalAssistantToMessage() {
        val conversational = Conversational.Companion {
            role = Role.Assistant
            content = Content.Text("Hello from assistant")
        }
        val message = AgentcoreMessageConverter.conversationalToMessage(conversational, randomEventId, Clock.System.now())

        assertIs<Message.Assistant>(message)
        assertEquals("Hello from assistant", message.content)
    }

    @Test
    fun testConversationalToolRoleReturnsNullWhenIgnored() {
        val conversational = Conversational.Companion {
            role = Role.Tool
            content = Content.Text("tool output")
        }
        assertNull(AgentcoreMessageConverter.conversationalToMessage(conversational, randomEventId, Clock.System.now(), ignoreUnsupportedValues = true))
    }

    @Test
    fun testConversationalToolRoleThrowsWhenNotIgnored() {
        val conversational = Conversational.Companion {
            role = Role.Tool
            content = Content.Text("tool output")
        }
        assertFailsWith<IllegalStateException> {
            AgentcoreMessageConverter.conversationalToMessage(conversational, randomEventId, Clock.System.now(), ignoreUnsupportedValues = false)
        }
    }

    @Test
    fun testConversationalOtherRoleReturnsNull() {
        val conversational = Conversational.Companion {
            role = Role.Other
            content = Content.Text("other")
        }
        assertNull(AgentcoreMessageConverter.conversationalToMessage(conversational, randomEventId, Clock.System.now()))
    }

    // --- eventId metadata tests ---

    @Test
    fun testConversationalToMessageWithEventId() {
        val conversational = Conversational.Companion {
            role = Role.User
            content = Content.Text("Hello")
        }
        val message = AgentcoreMessageConverter.conversationalToMessage(conversational, eventId = "evt-123", Clock.System.now())

        assertNotNull(message)
        assertEquals("evt-123", AgentcoreMessageConverter.getEventId(message))
    }

    @Test
    fun testHasEventIdOnPlainMessage() {
        val message = Message.User("Hello", RequestMetaInfo.Empty)
        assertNull(AgentcoreMessageConverter.getEventId(message))
    }

    // --- Round-trip tests ---

    @Test
    fun testUserMessageRoundTrip() {
        val original = Message.User("round trip test", RequestMetaInfo.Empty)
        val payload = AgentcoreMessageConverter.messageToPayload(original)!!
        val restored = AgentcoreMessageConverter.conversationalToMessage(payload.value, randomEventId, Clock.System.now())

        assertIs<Message.User>(restored)
        assertEquals(original.content, restored.content)
    }

    @Test
    fun testAssistantMessageRoundTrip() {
        val original = Message.Assistant("round trip assistant", ResponseMetaInfo.Empty)
        val payload = AgentcoreMessageConverter.messageToPayload(original)!!
        val restored = AgentcoreMessageConverter.conversationalToMessage(payload.value, randomEventId, Clock.System.now())

        assertIs<Message.Assistant>(restored)
        assertEquals(original.content, restored.content)
    }

    @Test
    fun testMixedMessagesRoundTrip() {
        val originals = listOf(
            Message.User("Hello", RequestMetaInfo.Empty),
            Message.System("system prompt", RequestMetaInfo.Empty),
            Message.Assistant("Hi!", ResponseMetaInfo.Empty),
            Message.Tool.Call(id = "1", tool = "t", content = "{}", metaInfo = ResponseMetaInfo.Empty)
        )

        val payloads = originals.mapNotNull { AgentcoreMessageConverter.messageToPayload(it) }
        assertEquals(2, payloads.size)

        val restored = payloads.mapNotNull { AgentcoreMessageConverter.conversationalToMessage(it.value, randomEventId, Clock.System.now()) }
        assertEquals(2, restored.size)
        assertIs<Message.User>(restored[0])
        assertEquals("Hello", restored[0].content)
        assertIs<Message.Assistant>(restored[1])
        assertEquals("Hi!", restored[1].content)
    }
}
