package ai.koog.agents.a2a.core

import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FilePart
import ai.koog.a2a.model.FileWithBytes
import ai.koog.a2a.model.FileWithUri
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConvertersTest {

    private val fixedInstant: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val fixedClock: Clock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    private val prettyJson = Json { prettyPrint = true }

    @Test
    fun testA2AtoKoog_User_withTextDataAndFiles_fullObjectEquality() {
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

        val expectedContent = buildString {
            appendLine("Context ID: ctx-123")
            appendLine("Task ID: task-1")
            appendLine("Reference Task ID: ref-1")
            appendLine("Reference Task ID: ref-2")
            appendLine("Hello")
            appendLine(prettyJson.encodeToString(json))
        }
        val expectedAttachments = listOf(
            Attachment.File(
                format = "",
                mimeType = "application/octet-stream",
                fileName = "file.bin",
                content = AttachmentContent.Binary.Base64(bytesBase64)
            ),
            Attachment.File(
                format = "",
                mimeType = "text/plain",
                fileName = "doc.txt",
                content = AttachmentContent.URL("https://example.com/doc.txt")
            )
        )
        val expected: Message = Message.User(
            content = expectedContent,
            metaInfo = RequestMetaInfo(timestamp = fixedInstant),
            attachments = expectedAttachments
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testA2AtoKoog_Agent_fullObjectEquality() {
        val a2a = A2AMessage(
            messageId = "m2",
            role = Role.Agent,
            parts = listOf(TextPart("Agent says hi")),
        )

        val actual = a2a.toKoogMessage(clock = fixedClock)

        val expected = Message.Assistant(
            content = buildString { appendLine("Agent says hi") },
            metaInfo = ResponseMetaInfo(timestamp = fixedInstant),
            attachments = emptyList()
        )

        assertEquals(expected, actual)
    }

    @Test
    fun testKoogToA2A_User_withPlainTextBinaryAndUrlAttachments_fullObjectEquality() {
        val plain = Attachment.File(
            content = AttachmentContent.PlainText("abc"),
            format = "txt",
            mimeType = "text/plain",
            fileName = "note.txt",
        )
        val bytes = byteArrayOf(1, 2, 3)
        val bin = Attachment.File(
            content = AttachmentContent.Binary.Bytes(bytes),
            format = "bin",
            mimeType = "application/octet-stream",
            fileName = "bytes.bin",
        )
        val url = Attachment.File(
            content = AttachmentContent.URL("https://example.com/a.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "a.png",
        )

        val koog: Message = Message.User(
            content = "Hi",
            metaInfo = RequestMetaInfo(timestamp = fixedInstant),
            attachments = listOf(plain, bin, url)
        )

        val actual = koog.toA2AMessage(
            messageId = "mid",
            contextId = "ctx",
            taskId = "task",
            referenceTaskIds = listOf("r1"),
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
    fun testKoogToA2A_Assistant_fullObjectEquality() {
        val koog: Message = Message.Assistant(
            content = "Answer",
            metaInfo = ResponseMetaInfo(timestamp = fixedInstant),
        )
        val actual = koog.toA2AMessage(messageId = "m3")
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
            sys.toA2AMessage(messageId = "m4")
        }
    }
}
