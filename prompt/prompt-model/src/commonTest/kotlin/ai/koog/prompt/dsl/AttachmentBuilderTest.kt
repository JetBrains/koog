package ai.koog.prompt.dsl

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentBuilderTest {

    @Test
    fun testEmptyBuilder() {
        val builder = ContentPartsBuilder()
        val result = builder.build()

        assertTrue(result.isEmpty(), "Empty builder should produce empty list")
    }

    @Test
    fun testAddSingleImage() {
        val builder = ContentPartsBuilder()
        builder.image("https://example.com/test.png")
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            ContentPart.Image(
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
        val builder = ContentPartsBuilder()
        builder.audio(
            ContentPart.Audio(
                content = AttachmentContent.Binary.Bytes(audioData),
                format = "mp3",
                fileName = "audio.mp3"
            )
        )
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            ContentPart.Audio(
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
        val builder = ContentPartsBuilder()
        builder.file(
            ContentPart.File(
                content = AttachmentContent.Binary.Bytes(documentData),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "report.pdf"
            )
        )
        val result = builder.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            ContentPart.File(
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
        val builder = ContentPartsBuilder()
        builder.image(
            ContentPart.Image(
                content = AttachmentContent.Binary.Bytes(imageData),
                format = "jpg",
                fileName = "photo.jpg"
            )
        )
        builder.audio(
            ContentPart.Audio(
                content = AttachmentContent.Binary.Bytes(audioData),
                format = "wav",
                fileName = "audio.wav"
            )
        )
        builder.file(
            ContentPart.File(
                content = AttachmentContent.Binary.Bytes(documentData),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "document.pdf"
            )
        )
        val result = builder.build()

        assertEquals(3, result.size, "Should contain three attachments")
        assertEquals(
            ContentPart.Image(
                content = AttachmentContent.Binary.Bytes(imageData),
                format = "jpg",
                fileName = "photo.jpg"
            ),
            result[0]
        )
        assertEquals(
            ContentPart.Audio(
                content = AttachmentContent.Binary.Bytes(audioData),
                format = "wav",
                fileName = "audio.wav"
            ),
            result[1]
        )
        assertEquals(
            ContentPart.File(
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
        val result = ContentPartsBuilder().apply {
            image(
                ContentPart.Image(
                    content = AttachmentContent.Binary.Bytes(imageData),
                    format = "png",
                    fileName = "photo.png"
                )
            )
            file(
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(pdfData),
                    format = "pdf",
                    mimeType = "application/pdf",
                    fileName = "report.pdf"
                )
            )
        }.build()

        assertEquals(2, result.size, "Should contain two attachments")
        assertEquals(
            ContentPart.Image(
                content = AttachmentContent.Binary.Bytes(imageData),
                format = "png",
                fileName = "photo.png"
            ),
            result[0]
        )
        assertEquals(
            ContentPart.File(
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
        val result = ContentPartsBuilder().apply {
            image("https://example.com/image.jpg")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            ContentPart.Image(
                content = AttachmentContent.URL("https://example.com/image.jpg"),
                format = "jpg",
                fileName = "image.jpg"
            ),
            result[0]
        )
    }

    @Test
    fun testAudioWithUrl() {
        val result = ContentPartsBuilder().apply {
            audio("https://example.com/music.mp3")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            ContentPart.Audio(
                content = AttachmentContent.URL("https://example.com/music.mp3"),
                format = "mp3",
                fileName = "music.mp3"
            ),
            result[0]
        )
    }

    @Test
    fun testDocumentWithUrl() {
        val result = ContentPartsBuilder().apply {
            file("https://example.com/document.pdf", "application/pdf")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertTrue(result[0] is ContentPart.File, "Attachment should be a File")
        assertEquals(
            AttachmentContent.URL("https://example.com/document.pdf"),
            (result[0] as ContentPart.File).content,
            "Document source should match"
        )
        assertTrue(
            (result[0] as ContentPart.File).content is AttachmentContent.URL,
            "Document should be recognized as URL"
        )
    }

    @Test
    fun testImageBase64Behavior() {
        val image = ContentPart.Image(
            content = AttachmentContent.Binary.Base64("simulated_base64_content"),
            format = "png",
            fileName = "local_image.png"
        )
        val result = ContentPartsBuilder().apply {
            image(image)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultImage = result[0] as ContentPart.Image
        assertFalse(resultImage.content is AttachmentContent.URL, "Local image should not be recognized as URL")
        assertTrue(
            resultImage.content is AttachmentContent.Binary,
            "Local image should be recognized as Binary content"
        )

        val base64String = resultImage.content.asBase64()
        assertEquals("simulated_base64_content", base64String, "Base64 content should match")
    }

    @Test
    fun testDocumentBase64Behavior() {
        val document = ContentPart.File(
            content = AttachmentContent.Binary.Base64("simulated_base64_content"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "local_document.pdf"
        )
        val result = ContentPartsBuilder().apply {
            file(document)
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultDocument = result[0] as ContentPart.File
        assertFalse(resultDocument.content is AttachmentContent.URL, "Local document should not be recognized as URL")
        assertTrue(
            resultDocument.content is AttachmentContent.Binary,
            "Local document should be recognized as Binary content"
        )

        val base64String = resultDocument.content.asBase64()
        assertEquals("simulated_base64_content", base64String, "Base64 content should match")
    }

    @Test
    fun testAudioBase64Encoding() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val result = ContentPartsBuilder().apply {
            audio(
                ContentPart.Audio(
                    content = AttachmentContent.Binary.Bytes(audioData),
                    format = "mp3"
                )
            )
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultAudio = result[0] as ContentPart.Audio
        assertFalse(resultAudio.content is AttachmentContent.URL, "Local audio should not be recognized as URL")
        assertTrue(
            resultAudio.content is AttachmentContent.Binary,
            "Local audio should be recognized as Binary content"
        )
    }

    @Test
    fun testVideoWithUrl() {
        val result = ContentPartsBuilder().apply {
            video("https://example.com/video.mp4")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        assertEquals(
            ContentPart.Video(
                content = AttachmentContent.URL("https://example.com/video.mp4"),
                format = "mp4",
                fileName = "video.mp4"
            ),
            result[0]
        )
    }

    @Test
    fun testBinaryFile() {
        val fileData = byteArrayOf(1, 2, 3, 4, 5)
        val result = ContentPartsBuilder().apply {
            file(
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(fileData),
                    format = "pdf",
                    mimeType = "application/pdf",
                    fileName = "document.pdf"
                )
            )
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultFile = result[0] as ContentPart.File
        assertTrue(resultFile.content is AttachmentContent.Binary, "File should be recognized as Binary content")
        assertEquals("application/pdf", resultFile.mimeType, "MIME type should match")
        assertEquals("document.pdf", resultFile.fileName, "File name should match")
    }

    @Test
    fun testTextFile() {
        val result = ContentPartsBuilder().apply {
            file(
                ContentPart.File(
                    content = AttachmentContent.PlainText("This is a text file content"),
                    format = "txt",
                    mimeType = "text/plain",
                    fileName = "document.txt"
                )
            )
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultFile = result[0] as ContentPart.File
        assertTrue(resultFile.content is AttachmentContent.PlainText, "File should be recognized as PlainText content")
        assertEquals("This is a text file content", (resultFile.content).text, "Text content should match")
        assertEquals("text/plain", resultFile.mimeType, "MIME type should match")
        assertEquals("document.txt", resultFile.fileName, "File name should match")
    }
}
