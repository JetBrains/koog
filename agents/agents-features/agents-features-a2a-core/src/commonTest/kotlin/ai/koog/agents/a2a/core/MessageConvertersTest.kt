package ai.koog.agents.a2a.core

import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FilePart
import ai.koog.a2a.model.FileWithBytes
import ai.koog.a2a.model.FileWithUri
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageConvertersTest {
    private val fixedInstant: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val fixedClock: Clock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    private val prettyJson = Json { prettyPrint = true }

    @Test
    fun testA2AtoKoog_User_withTextDataAndFiles() {
        val json = buildJsonObject { put("k", "v") }
        val bytesBase64 = "YmFzZTY0" // arbitrary base64 string

        val a2a = A2AMessage(
            messageId = "m1",
            role = Role.User,
            parts = listOf(
                TextPart("Hello"),
                DataPart(json),
                FilePart(FileWithBytes(bytes = bytesBase64, name = "file.bin", mimeType = null)),
                FilePart(FileWithUri(uri = "https://example.com/doc.txt", name = "doc.txt", mimeType = "text/plain")),
            ),
            contextId = "ctx-123",
            taskId = "task-1",
            referenceTaskIds = listOf("ref-1", "ref-2"),
            extensions = listOf("ext:a"),
        )

        val actual: Message = a2a.toKoogMessage(clock = fixedClock)

        val expectedParts = listOf(
            ContentPart.Text("Hello"),
            ContentPart.Text(prettyJson.encodeToString(json)),
            ContentPart.File(
                format = "",
                mimeType = "application/octet-stream",
                fileName = "file.bin",
                content = AttachmentContent.Binary.Base64(bytesBase64)
            ),
            ContentPart.File(
                format = "",
                mimeType = "text/plain",
                fileName = "doc.txt",
                content = AttachmentContent.URL("https://example.com/doc.txt")
            )
        )
        val expectedMetadata = JsonObject(
            mapOf(
                MESSAGE_A2A_METADATA_KEY to Json.encodeToJsonElement(
                    MessageA2AMetadata(
                        messageId = "m1",
                        contextId = "ctx-123",
                        taskId = "task-1",
                        referenceTaskIds = listOf("ref-1", "ref-2"),
                        metadata = null,
                        extensions = listOf("ext:a"),
                    )
                )
            )
        )
        val expected: Message = Message.User(
            parts = expectedParts,
            metaInfo = RequestMetaInfo(timestamp = fixedInstant, metadata = expectedMetadata),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testA2AtoKoog_Agent() {
        val a2a = A2AMessage(
            messageId = "m2",
            role = Role.Agent,
            parts = listOf(TextPart("Agent says hi")),
        )

        val actual = a2a.toKoogMessage(clock = fixedClock)

        val expectedMetadata = JsonObject(
            mapOf(
                MESSAGE_A2A_METADATA_KEY to Json.encodeToJsonElement(
                    MessageA2AMetadata(
                        messageId = "m2",
                        contextId = null,
                        taskId = null,
                        referenceTaskIds = null,
                        metadata = null,
                        extensions = null,
                    )
                )
            )
        )
        val expected = Message.Assistant(
            content = "Agent says hi",
            metaInfo = ResponseMetaInfo(timestamp = fixedInstant, metadata = expectedMetadata),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testKoogToA2A_User_withPlainTextBinaryAndUrlAttachments() {
        val text = ContentPart.Text("Hi")
        val plain = ContentPart.File(
            content = AttachmentContent.PlainText("abc"),
            format = "txt",
            mimeType = "text/plain",
            fileName = "note.txt",
        )
        val bytes = byteArrayOf(1, 2, 3)
        val bin = ContentPart.File(
            content = AttachmentContent.Binary.Bytes(bytes),
            format = "bin",
            mimeType = "application/octet-stream",
            fileName = "bytes.bin",
        )
        val url = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/a.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "a.png",
        )

        val koog: Message = Message.User(
            parts = listOf(text, plain, bin, url),
            metaInfo = RequestMetaInfo(timestamp = fixedInstant),
        )

        val actual = koog.toA2AMessage(
            a2aMetadata = MessageA2AMetadata(
                messageId = "mid",
                contextId = "ctx",
                taskId = "task",
                referenceTaskIds = listOf("r1"),
                metadata = null,
                extensions = null,
            )
        )

        val expectedPlainBase64 = AttachmentContent.Binary.Bytes("abc".encodeToByteArray()).asBase64()
        val expectedBinBase64 = AttachmentContent.Binary.Bytes(bytes).asBase64()
        val expected = A2AMessage(
            messageId = "mid",
            role = Role.User,
            parts = listOf(
                TextPart("Hi"),
                FilePart(FileWithBytes(bytes = expectedPlainBase64, name = "note.txt", mimeType = "text/plain")),
                FilePart(
                    FileWithBytes(
                        bytes = expectedBinBase64,
                        name = "bytes.bin",
                        mimeType = "application/octet-stream"
                    )
                ),
                FilePart(FileWithUri(uri = "https://example.com/a.png", name = "a.png", mimeType = "image/png")),
            ),
            extensions = null,
            taskId = "task",
            referenceTaskIds = listOf("r1"),
            contextId = "ctx",
            metadata = null,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testKoogToA2A_Assistant() {
        val koog: Message = Message.Assistant(
            content = "Answer",
            metaInfo = ResponseMetaInfo(timestamp = fixedInstant),
        )
        val actual = koog.toA2AMessage(
            a2aMetadata = MessageA2AMetadata(
                messageId = "m3",
                contextId = null,
                taskId = null,
                referenceTaskIds = null,
                metadata = null,
                extensions = null,
            )
        )
        val expected = A2AMessage(
            messageId = "m3",
            role = Role.Agent,
            parts = listOf(TextPart("Answer")),
            extensions = null,
            taskId = null,
            referenceTaskIds = null,
            contextId = null,
            metadata = null,
        )
        assertEquals(expected, actual)
    }

    @Test
    fun testKoogToA2A_unsupportedKoogMessageThrows() {
        val sys: Message = Message.System(
            content = "system",
            metaInfo = RequestMetaInfo(timestamp = fixedInstant)
        )
        assertFailsWith<IllegalArgumentException> {
            sys.toA2AMessage()
        }
    }
}
