package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachmentBuilderTest {

    @Test
    fun testEmptyBuilder() {
        val builder = AttachmentBuilder()
        val result = builder.build()

        assertTrue(result.isEmpty(), "Empty builder should produce empty list")
    }

    @Test
    fun testAddSingleImage() {
        val builder = AttachmentBuilder()
        builder.image("https://example.com/test.png")
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            Attachment.Image(
                content = AttachmentContent.URL("https://example.com/test.png"),
                format = "png",
                fileName = "test.png"
            ),
            result[0]
        )
    }

    @Test
    fun testAddSingleAudio() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val builder = AttachmentBuilder()
        builder.audio(Attachment.Audio(content = AttachmentContent.Binary.Bytes(audioData), format = "mp3", fileName = "audio.mp3"))
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            Attachment.Audio(
                content = AttachmentContent.Binary.Bytes(audioData),
                format = "mp3",
                fileName = "audio.mp3"
            ),
            result[0]
        )
    }

    @Test
    fun testAddSingleDocument() {
        val documentData = byteArrayOf(1, 2, 3, 4, 5)
        val builder = AttachmentBuilder()
        builder.file(Attachment.File(
            content = AttachmentContent.Binary.Bytes(documentData),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        ))
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            Attachment.File(
                content = AttachmentContent.Binary.Bytes(documentData),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "report.pdf"
            ),
            result[0]
        )
    }

    @Test
    fun testAddMultipleAttachments() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val imageData = byteArrayOf(10, 20, 30, 40, 50)
        val documentData = byteArrayOf(60, 70, 80, 90, 100)
        val builder = AttachmentBuilder()
        builder.image(Attachment.Image(
            content = AttachmentContent.Binary.Bytes(imageData),
            format = "jpg",
            fileName = "photo.jpg"
        ))
        builder.audio(Attachment.Audio(
            content = AttachmentContent.Binary.Bytes(audioData),
            format = "wav",
            fileName = "audio.wav"
        ))
        builder.file(Attachment.File(
            content = AttachmentContent.Binary.Bytes(documentData),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "document.pdf"
        ))
        val result = builder.build()

        assertEquals(3, result.size, "Should contain three attachments")
        assertEquals(
            Attachment.Image(
                content = AttachmentContent.Binary.Bytes(imageData),
                format = "jpg",
                fileName = "photo.jpg"
            ),
            result[0]
        )
        assertEquals(
            Attachment.Audio(
                content = AttachmentContent.Binary.Bytes(audioData),
                format = "wav",
                fileName = "audio.wav"
            ),
            result[1]
        )
        assertEquals(
            Attachment.File(
                content = AttachmentContent.Binary.Bytes(documentData),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "document.pdf"
            ),
            result[2]
        )
    }

    @Test
    fun testDslSyntax() {
        val imageData = byteArrayOf(11, 22, 33, 44, 55)
        val pdfData = byteArrayOf(66, 77, 88, 99, 111)
        val result = AttachmentBuilder().apply {
            image(Attachment.Image(
                content = AttachmentContent.Binary.Bytes(imageData),
                format = "png",
                fileName = "photo.png"
            ))
            file(Attachment.File(
                content = AttachmentContent.Binary.Bytes(pdfData),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "report.pdf"
            ))
        }.build()

        assertEquals(2, result.size, "Should contain two attachments")
        assertEquals(
            Attachment.Image(
                content = AttachmentContent.Binary.Bytes(imageData),
                format = "png",
                fileName = "photo.png"
            ),
            result[0]
        )
        assertEquals(
            Attachment.File(
                content = AttachmentContent.Binary.Bytes(pdfData),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "report.pdf"
            ),
            result[1]
        )
    }

    @Test
    fun testImageWithUrl() {
        val result = AttachmentBuilder().apply {
            image("https://example.com/image.jpg")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            Attachment.Image(
                content = AttachmentContent.URL("https://example.com/image.jpg"),
                format = "jpg",
                fileName = "image.jpg"
            ),
            result[0]
        )
    }

    @Test
    fun testAudioWithUrl() {
        val result = AttachmentBuilder().apply {
            audio("https://example.com/music.mp3")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            Attachment.Audio(
                content = AttachmentContent.URL("https://example.com/music.mp3"),
                format = "mp3",
                fileName = "music.mp3"
            ),
            result[0]
        )
    }
}
