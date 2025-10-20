package ai.koog.prompt.dsl

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Content
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.text.numbered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageContentBuilderTest {

    @Test
    fun testEmptyBuilder() {
        val builder = MessageContentBuilder()
        val result = builder.build()

        assertIs<Content.Text>(result, "Empty builder should produce empty text content")
        assertEquals("", result.value, "Empty builder should produce empty content")
    }

    @Test
    fun testTextOnly() {
        val builder = MessageContentBuilder().apply {
            text("Hello")
            text(" ")
            text("World")
        }
        val result = builder.build()

        assertIs<Content.Text>(result, "Builder with only text should produce text content")
        assertEquals("Hello World", result.value, "Content should be correctly built")
    }

    @Test
    fun testAttachmentsOnly() {
        val builder = MessageContentBuilder()
        builder.attachments {
            image("https://example.com/test.png")
            file("https://example.com/report.pdf", "application/pdf")
        }
        val result = builder.build()

        assertIs<Content.Parts>(result, "Builder with only attachments should produce parts content")

        assertEquals(2, result.value.size)

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/test.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "test.png"
        )
        assertEquals(expectedImage, result.value[0], "First attachment should match expected Image")

        val expectedFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/report.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        assertEquals(expectedFile, result.value[1], "Second attachment should match expected File")
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

        assertIs<Content.Parts>(result, "Builder with text and attachments should produce parts content")
        assertEquals(2, result.value.size, "Should have text part and one attachment part")

        val expectedText = ContentPart.Text("Check out this image:\n")
        assertEquals(expectedText, result.value[0], "First part should be text content")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo.jpg"
        )
        assertEquals(expectedImage, result.value[1], "Second part should match expected Image")
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

        assertIs<Content.Parts>(result, "Builder with only attachments should produce parts content")
        assertEquals(2, result.value.size, "Should have two attachments from the second call")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo2.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo2.jpg"
        )
        assertEquals(expectedImage, result.value[0], "First attachment should match expected Image")

        val expectedFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/doc.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "doc.pdf"
        )
        assertEquals(expectedFile, result.value[1], "Second attachment should match expected File")
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

        assertIs<Content.Parts>(result, "Builder with text and attachments should produce parts content")
        assertEquals(4, result.value.size, "Should have text part and three attachment parts")

        val expectedText = ContentPart.Text("Here's my analysis:\n1. First point\n2. Second point\nSupporting documents:")
        assertEquals(expectedText, result.value[0], "First part should be text content")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/chart.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "chart.png"
        )
        assertEquals(expectedImage, result.value[1], "Second part should match expected Image")

        val expectedPdf = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/report.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        assertEquals(expectedPdf, result.value[2], "Third part should match expected PDF file")

        val expectedExcel = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/data.xlsx"),
            format = "xlsx",
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            fileName = "data.xlsx"
        )
        assertEquals(expectedExcel, result.value[3], "Fourth part should match expected Excel file")
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

        assertIs<Content.Parts>(result, "Builder with text and attachments should produce parts content")
        assertEquals(2, result.value.size, "Should have text part and one attachment part")

        val expectedText = ContentPart.Text("Hello\nWorld")
        assertEquals(expectedText, result.value[0], "First part should be text content")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "photo.png"
        )
        assertEquals(expectedImage, result.value[1], "Second part should match expected Image")
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

        assertIs<Content.Text>(result, "Builder with only text should produce text content")
        val expected = "1: First line\n2: Second line"
        assertEquals(expected, result.value, "Should correctly use inherited numbered functionality")
    }
}
