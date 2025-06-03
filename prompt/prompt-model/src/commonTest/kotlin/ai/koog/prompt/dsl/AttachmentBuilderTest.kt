package ai.koog.prompt.dsl

import ai.koog.prompt.message.MediaContent
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
        builder.image("test.png")
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertTrue(result[0] is MediaContent.Image, "Attachment should be an Image")
        assertEquals("test.png", (result[0] as MediaContent.Image).source, "Image source should match")
    }

    @Test
    fun testAddSingleAudio() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val builder = AttachmentBuilder()
        builder.audio(audioData, "mp3")
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertTrue(result[0] is MediaContent.Audio, "Attachment should be an Audio")
        assertEquals("mp3", (result[0] as MediaContent.Audio).format, "Audio format should match")
        assertEquals(audioData, (result[0] as MediaContent.Audio).data, "Audio data should match")
    }

    @Test
    fun testAddSingleDocument() {
        val builder = AttachmentBuilder()
        builder.document("report.pdf")
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertTrue(result[0] is MediaContent.File, "Attachment should be a File")
        assertEquals("report.pdf", (result[0] as MediaContent.File).source, "Document source should match")
    }

    @Test
    fun testAddMultipleAttachments() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val builder = AttachmentBuilder()
        builder.image("photo.jpg")
        builder.audio(audioData, "wav")
        builder.document("document.pdf")
        val result = builder.build()

        assertEquals(3, result.size, "Should contain three attachments")
        assertTrue(result[0] is MediaContent.Image, "First attachment should be an Image")
        assertTrue(result[1] is MediaContent.Audio, "Second attachment should be an Audio")
        assertTrue(result[2] is MediaContent.File, "Third attachment should be a File")
    }

    @Test
    fun testDslSyntax() {
        val result = AttachmentBuilder().apply {
            image("photo.png")
            document("report.pdf")
        }.build()

        assertEquals(2, result.size, "Should contain two attachments")
        assertTrue(result[0] is MediaContent.Image, "First attachment should be an Image")
        assertTrue(result[1] is MediaContent.File, "Second attachment should be a File")
    }

    @Test
    fun testImageWithUrl() {
        val result = AttachmentBuilder().apply {
            image("https://example.com/image.jpg")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertTrue(result[0] is MediaContent.Image, "Attachment should be an Image")
        assertEquals(
            "https://example.com/image.jpg",
            (result[0] as MediaContent.Image).source,
            "Image source should match"
        )
        assertTrue((result[0] as MediaContent.Image).isUrl(), "Image should be recognized as URL")
    }
}