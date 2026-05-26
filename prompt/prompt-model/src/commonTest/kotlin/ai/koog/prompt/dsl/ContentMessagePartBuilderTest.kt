package ai.koog.prompt.dsl

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.CacheControl
import ai.koog.prompt.message.MessagePart
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentMessagePartBuilderTest {

    @Serializable
    sealed interface TestCacheControl : CacheControl {
        /** Cache with the default TTL. */
        @Serializable
        data object Default : TestCacheControl

        /** Cache for 1 hour. */
        @Serializable
        data object OneHour : TestCacheControl
    }

    @Test
    fun testEmptyBuilder() {
        val builder = ContentMessagePartsBuilder()
        val result = builder.build()

        assertTrue(result.isEmpty(), "Empty builder should produce empty list")
    }

    @Test
    fun testSinglePngImageWithUrl() {
        val result = ContentMessagePartsBuilder().apply {
            image("https://example.com/test.png")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.Image(
                    content = AttachmentContent.URL("https://example.com/test.png"),
                    format = "png",
                    fileName = "test.png"
                )
            ),
            result[0],
            "Image source should match"
        )
    }

    @Test
    fun testSingleJPGImageWithUrl() {
        val result = ContentMessagePartsBuilder().apply {
            image("https://example.com/image.jpg")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.Image(
                    content = AttachmentContent.URL("https://example.com/image.jpg"),
                    format = "jpg",
                    fileName = "image.jpg"
                ),
            ),
            result[0],
            "Image source should match"
        )
    }

    @Test
    fun testSingleAudioWithUrl() {
        val result = ContentMessagePartsBuilder().apply {
            audio("https://example.com/music.mp3")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.Audio(
                    content = AttachmentContent.URL("https://example.com/music.mp3"),
                    format = "mp3",
                    fileName = "music.mp3"
                ),
            ),
            result[0],
            "Audio source should match"
        )
    }

    @Test
    fun testSingleDocumentWithUrl() {
        val result = ContentMessagePartsBuilder().apply {
            file("https://example.com/document.pdf", "application/pdf")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.File(
                    content = AttachmentContent.URL("https://example.com/document.pdf"),
                    mimeType = "application/pdf",
                    format = "pdf",
                    fileName = "document.pdf"
                ),
            ),
            result[0],
            "File source should match"
        )
    }

    @Test
    fun testSingleVideoWithUrl() {
        val result = ContentMessagePartsBuilder().apply {
            video("https://example.com/video.mp4")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.Video(
                    content = AttachmentContent.URL("https://example.com/video.mp4"),
                    format = "mp4",
                    fileName = "video.mp4"
                ),
            ),
            result[0],
            "Video source should match"
        )
    }

    @Test
    fun testSingleAudio() {
        val audio = AttachmentSource.Audio(
            content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3, 4, 5)),
            format = "mp3",
            fileName = "audio.mp3"
        )
        val result = ContentMessagePartsBuilder().apply {
            audio(audio)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = audio), result[0], "Audio source should match")
    }

    @Test
    fun testSingleDocument() {
        val file = AttachmentSource.File(
            content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3, 4, 5)),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        val result = ContentMessagePartsBuilder().apply {
            file(file)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = file), result[0], "File source should match")
    }

    @Test
    fun testImageBase64() {
        val image = AttachmentSource.Image(
            content = AttachmentContent.Binary.Base64("simulated_base64_content"),
            format = "png",
            fileName = "local_image.png"
        )
        val result = ContentMessagePartsBuilder().apply {
            image(image)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = image), result[0], "Image source should match")
    }

    @Test
    fun testDocumentBase64() {
        val file = AttachmentSource.File(
            content = AttachmentContent.Binary.Base64("simulated_base64_content"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "local_document.pdf"
        )
        val result = ContentMessagePartsBuilder().apply {
            file(file)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = file), result[0], "File source should match")
    }

    @Test
    fun testAudioBase64() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val audio = AttachmentSource.Audio(
            content = AttachmentContent.Binary.Bytes(audioData),
            format = "mp3"
        )
        val result = ContentMessagePartsBuilder().apply {
            audio(audio)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = audio), result[0], "Audio source should match")
    }

    @Test
    fun testBinaryFile() {
        val fileData = byteArrayOf(1, 2, 3, 4, 5)
        val file = AttachmentSource.File(
            content = AttachmentContent.Binary.Bytes(fileData),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "document.pdf"
        )
        val result = ContentMessagePartsBuilder().apply {
            file(file)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = file), result[0], "File source should match")
    }

    @Test
    fun testTextFile() {
        val file = AttachmentSource.File(
            content = AttachmentContent.PlainText("This is a text file content"),
            format = "txt",
            mimeType = "text/plain",
            fileName = "document.txt"
        )
        val result = ContentMessagePartsBuilder().apply {
            file(file)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Attachment(source = file), result[0], "File source should match")
    }

    @Test
    fun testAddMultipleAttachments() {
        val image = AttachmentSource.Image(
            content = AttachmentContent.Binary.Bytes(byteArrayOf(11, 22, 33, 44, 55)),
            format = "png",
            fileName = "photo.png"
        )
        val file = AttachmentSource.File(
            content = AttachmentContent.Binary.Bytes(byteArrayOf(66, 77, 88, 99, 111)),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        val audio = AttachmentSource.Audio(
            content = AttachmentContent.Binary.Bytes(byteArrayOf(1, 2, 3, 4, 5)),
            format = "wav",
            fileName = "audio.wav"
        )

        val result = ContentMessagePartsBuilder().apply {
            image(image)
            file(file)
            audio(audio)
        }.build()

        assertEquals(3, result.size, "Should contain two attachments")
        assertEquals(MessagePart.Attachment(source = image), result[0])
        assertEquals(MessagePart.Attachment(source = file), result[1])
        assertEquals(MessagePart.Attachment(source = audio), result[2])
    }

    @Test
    fun testText() {
        val result = ContentMessagePartsBuilder().apply {
            text("This is a text content")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(MessagePart.Text(text = "This is a text content"), result[0], "Text content should match")
    }

    @Test
    fun testMultipleText() {
        val result = ContentMessagePartsBuilder().apply {
            text("This is a text content.")
            text(" This is another text content")
        }.build()

        assertEquals(1, result.size, "Should contain two attachments")
        assertEquals(
            MessagePart.Text(text = "This is a text content. This is another text content"),
            result[0],
            "Text content should match"
        )
    }

    @Test
    fun testTextWithTextBuilder() {
        val result = ContentMessagePartsBuilder().apply {
            text("This is a text content")
            newline()
            text("This is another text content")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Text(text = "This is a text content\nThis is another text content"),
            result[0],
            "Text content should match"
        )
    }

    @Test
    fun testTextWithMarkdownBuilder() {
        val result = ContentMessagePartsBuilder().apply {
            markdown {
                numbered {
                    item("This is a markdown content")
                    item("This is another markdown content")
                }
            }
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            MessagePart.Text(text = "1. This is a markdown content\n2. This is another markdown content"),
            result[0],
            "Text content should match"
        )
    }

    @Test
    fun testMultipleTextWithAttachment() {
        val result = ContentMessagePartsBuilder().apply {
            text("This is the first image")
            image("https://example.com/first.png")
            +"This is the second image"
            image("https://example.com/second.png")
        }.build()

        assertEquals(4, result.size, "Should contain two attachments")
        assertEquals(
            MessagePart.Text(text = "This is the first image"),
            result[0],
            "Fist text content should match"
        )
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.Image(
                    content = AttachmentContent.URL("https://example.com/first.png"),
                    format = "png",
                    fileName = "first.png"
                )
            ),
            result[1],
            "First image source should match"
        )
        assertEquals(
            MessagePart.Text(text = "This is the second image"),
            result[2],
            "Second text content should match"
        )
        assertEquals(
            MessagePart.Attachment(
                source = AttachmentSource.Image(
                    content = AttachmentContent.URL("https://example.com/second.png"),
                    format = "png",
                    fileName = "second.png"
                )
            ),
            result[3],
            "Second image source should match"
        )
    }
}
