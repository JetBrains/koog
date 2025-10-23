package ai.koog.prompt.dsl

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentPartsBuilderTest {

    @Test
    fun testEmptyBuilder() {
        val builder = ContentPartsBuilder()
        val result = builder.build()

        assertTrue(result.isEmpty(), "Empty builder should produce empty list")
    }

    @Test
    fun testAddSingleImage() {
        val result = ContentPartsBuilder().apply {
            image("https://example.com/test.png")
        }.build()

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
        val result = ContentPartsBuilder().apply {
            audio(
                ContentPart.Audio(
                    content = AttachmentContent.Binary.Bytes(audioData),
                    format = "mp3",
                    fileName = "audio.mp3"
                )
            )
        }.build()

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
        val result = ContentPartsBuilder().apply {
            file(
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(documentData),
                    format = "pdf",
                    mimeType = "application/pdf",
                    fileName = "report.pdf"
                )
            )
        }.build()

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
        val result = ContentPartsBuilder().apply {
            image(
                ContentPart.Image(
                    content = AttachmentContent.Binary.Bytes(imageData),
                    format = "jpg",
                    fileName = "photo.jpg"
                )
            )
            audio(
                ContentPart.Audio(
                    content = AttachmentContent.Binary.Bytes(audioData),
                    format = "wav",
                    fileName = "audio.wav"
                )
            )
            file(
                ContentPart.File(
                    content = AttachmentContent.Binary.Bytes(documentData),
                    format = "pdf",
                    mimeType = "application/pdf",
                    fileName = "document.pdf"
                )
            )
        }.build()

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
        val result = ContentPartsBuilder().apply {
            image(
                ContentPart.Image(
                    content = AttachmentContent.Binary.Base64("simulated_base64_content"),
                    format = "png",
                    fileName = "local_image.png"
                )
            )
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
        val result = ContentPartsBuilder().apply {
            file(
                ContentPart.File(
                    content = AttachmentContent.Binary.Base64("simulated_base64_content"),
                    format = "pdf",
                    mimeType = "application/pdf",
                    fileName = "local_document.pdf"
                )
            )
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
        val resultFile = result[0]
        assertIs<ContentPart.File>(resultFile, "Part should be recognized as File")
        assertTrue(resultFile.content is AttachmentContent.PlainText, "File should be recognized as PlainText content")
        assertEquals("This is a text file content", (resultFile.content).text, "Text content should match")
        assertEquals("text/plain", resultFile.mimeType, "MIME type should match")
        assertEquals("document.txt", resultFile.fileName, "File name should match")
    }

    @Test
    fun testText() {
        val result = ContentPartsBuilder().apply {
            text("This is a text content")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultText = result[0]
        assertIs<ContentPart.Text>(resultText, "Part should be recognized as Text")
        assertEquals("This is a text content", resultText.text)
    }

    @Test
    fun testMultipleText() {
        val result = ContentPartsBuilder().apply {
            text("This is a text content.")
            text(" This is another text content")
        }.build()

        assertEquals(1, result.size, "Should contain two attachments")
        val resultText = result[0]
        assertIs<ContentPart.Text>(resultText, "Part should be recognized as Text")
        assertEquals("This is a text content. This is another text content", resultText.text)
    }

    @Test
    fun testTextWithTextBuilder() {
        val result = ContentPartsBuilder().apply {
            text("This is a text content")
            newline()
            text("This is another text content")
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultText = result[0]
        assertIs<ContentPart.Text>(resultText, "Part should be recognized as Text")
        assertEquals("This is a text content\nThis is another text content", resultText.text)
    }

    @Test
    fun testTextWithMarkdownBuilder() {
        val result = ContentPartsBuilder().apply {
            markdown {
                numbered {
                    item("This is a markdown content")
                    item("This is another markdown content")
                }
            }
        }.build()

        assertEquals(1, result.size, "Should contain one attachment")
        val resultText = result[0]
        assertIs<ContentPart.Text>(resultText, "Part should be recognized as Text")
        assertEquals("1. This is a markdown content\n2. This is another markdown content", resultText.text)
    }

    @Test
    fun testMultipleTextWithAttachment() {
        val result = ContentPartsBuilder().apply {
            text("This is the first image")
            image("https://example.com/first.png")
            text("This is the second image")
            image("https://example.com/second.png")
        }.build()

        assertEquals(4, result.size, "Should contain two attachments")
        val resultFirstText = result[0]
        assertIs<ContentPart.Text>(resultFirstText, "Part should be recognized as Text")
        assertEquals("This is the first image", resultFirstText.text)
        val resultFirstImage = result[1]
        assertIs<ContentPart.Image>(resultFirstImage, "Part should be recognized as Image")
        assertEquals(AttachmentContent.URL("https://example.com/first.png"), resultFirstImage.content)
        val resultSecondText = result[2]
        assertIs<ContentPart.Text>(resultSecondText, "Part should be recognized as Text")
        assertEquals("This is the second image", resultSecondText.text)
        val resultSecondImage = result[3]
        assertIs<ContentPart.Image>(resultSecondImage, "Part should be recognized as Image")
        assertEquals(AttachmentContent.URL("https://example.com/second.png"), resultSecondImage.content)
    }
}
