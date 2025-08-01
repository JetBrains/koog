package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.text.numbered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageContentBuilderTest {

    @Test
    fun testEmptyBuilder() {
        val builder = MessageContentBuilder()
        val result = builder.build()

        assertEquals("", result.content, "Empty builder should produce empty content")
        assertTrue(result.attachments.isEmpty(), "Empty builder should produce empty attachments list")
    }

    @Test
    fun testTextOnly() {
        val builder = MessageContentBuilder().apply {
            text("Hello")
            text(" ")
            text("World")
        }
        val result = builder.build()

        assertEquals("Hello World", result.content, "Content should be correctly built")
        assertTrue(result.attachments.isEmpty(), "No attachments should be present")
    }

    @Test
    fun testAttachmentsOnly() {
        val builder = MessageContentBuilder()
        builder.attachments {
            image("https://example.com/test.png")
            file("https://example.com/report.pdf", "application/pdf")
        }
        val result = builder.build()

        assertEquals("", result.content, "Content should be empty")
        assertEquals(2, result.attachments.size, "Should have two attachments")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/test.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "test.png"
        )
        assertEquals(expectedImage, result.attachments[0], "First attachment should match expected Image")

        val expectedFile = Attachment.File(
            content = AttachmentContent.URL("https://example.com/report.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        assertEquals(expectedFile, result.attachments[1], "Second attachment should match expected File")
    }

    @Test
    fun testTextWithAttachments() {
        val builder = MessageContentBuilder().apply {
            text("Check out this image:")
            newline()
        }
        builder.attachments {
            image("https://example.com/photo.jpg")
        }
        val result = builder.build()

        assertEquals("Check out this image:\n", result.content, "Content should be correctly built with newline")
        assertEquals(1, result.attachments.size, "Should have one attachment")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/photo.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo.jpg"
        )
        assertEquals(expectedImage, result.attachments[0], "Attachment should match expected Image")
    }

    @Test
    fun testMultipleAttachmentCalls() {
        val builder = MessageContentBuilder()
        builder.attachments {
            image("https://example.com/photo1.jpg")
        }
        // Second call should replace the first attachments
        builder.attachments {
            image("https://example.com/photo2.jpg")
            file("https://example.com/doc.pdf", "application/pdf")
        }
        val result = builder.build()

        assertEquals(2, result.attachments.size, "Should have two attachments from the second call")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/photo2.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo2.jpg"
        )
        assertEquals(expectedImage, result.attachments[0], "First attachment should match expected Image")

        val expectedFile = Attachment.File(
            content = AttachmentContent.URL("https://example.com/doc.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "doc.pdf"
        )
        assertEquals(expectedFile, result.attachments[1], "Second attachment should match expected File")
    }

    @Test
    fun testComplexContent() {
        val builder = MessageContentBuilder().apply {
            text("Here's my analysis:")
            newline()
            text("1. First point")
            newline()
            text("2. Second point")
            newline()
            text("Supporting documents:")

            attachments {
                image("https://example.com/chart.png")
                file("https://example.com/report.pdf", "application/pdf")
                file(
                    "https://example.com/data.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            }
        }
        val result = builder.build()

        val expectedContent = "Here's my analysis:\n1. First point\n2. Second point\nSupporting documents:"
        assertEquals(expectedContent, result.content, "Complex content should be correctly built")
        assertEquals(3, result.attachments.size, "Should have three attachments")
    }

    @Test
    fun testDslSyntax() {
        val result = MessageContentBuilder().apply {
            text("Hello")
            newline()
            text("World")

            attachments {
                image("https://example.com/photo.png")
            }
        }.build()

        assertEquals("Hello\nWorld", result.content, "Content should be correctly built with DSL syntax")
        assertEquals(1, result.attachments.size, "Should have one attachment")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/photo.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "photo.png"
        )
        assertEquals(expectedImage, result.attachments[0], "Attachment should match expected Image")
    }

    @Test
    fun testInheritedTextBuilderFunctionality() {
        val result = MessageContentBuilder().apply {
            numbered {
                text("First line")
                newline()
                text("Second line")
            }
        }.build()

        val expected = "1: First line\n2: Second line"
        assertEquals(expected, result.content, "Should correctly use inherited numbered functionality")
        assertTrue(result.attachments.isEmpty(), "No attachments should be present")
    }
}
