package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.text.numbered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentBuilderWithAttachmentTest {

    @Test
    fun testEmptyBuilder() {
        val builder = MessageContentBuilder()
        val (content, attachments) = builder.buildWithAttachments()

        assertEquals("", content, "Empty builder should produce empty content")
        assertTrue(attachments.isEmpty(), "Empty builder should produce empty attachments list")
    }

    @Test
    fun testTextOnly() {
        val builder = MessageContentBuilder()
        builder.content("Hello")
        builder.content(" ")
        builder.content("World")
        val (content, attachments) = builder.buildWithAttachments()

        assertEquals("Hello World", content, "Content should be correctly built")
        assertTrue(attachments.isEmpty(), "No attachments should be present")
    }

    @Test
    fun testAttachmentsOnly() {
        val builder = MessageContentBuilder()
        builder.attachments {
            image("test.png")
            document("report.pdf")
        }
        val (content, attachments) = builder.buildWithAttachments()

        assertEquals("", content, "Content should be empty")
        assertEquals(2, attachments.size, "Should have two attachments")
        assertTrue(attachments[0] is Attachment.Image, "First attachment should be an Image")
        assertTrue(attachments[1] is Attachment.File, "Second attachment should be a File")
    }

    @Test
    fun testTextWithAttachments() {
        val builder = MessageContentBuilder()
        builder.content("Check out this image:")
        builder.newline()
        builder.attachments {
            image("photo.jpg")
        }
        val (content, attachments) = builder.buildWithAttachments()

        assertEquals("Check out this image:\n", content, "Content should be correctly built with newline")
        assertEquals(1, attachments.size, "Should have one attachment")
        assertTrue(attachments[0] is Attachment.Image, "Attachment should be an Image")
    }

    @Test
    fun testMultipleAttachmentCalls() {
        val builder = MessageContentBuilder()
        builder.attachments {
            image("photo1.jpg")
        }
        // Second call should replace the first attachments
        builder.attachments {
            image("photo2.jpg")
            document("doc.pdf")
        }
        val (_, attachments) = builder.buildWithAttachments()

        assertEquals(2, attachments.size, "Should have two attachments from the second call")
        assertEquals(
            "photo2.jpg",
            (attachments[0] as Attachment.Image).source,
            "Should have the image from the second call"
        )
    }

    @Test
    fun testComplexContent() {
        val builder = MessageContentBuilder()
        builder.content("Here's my analysis:")
        builder.newline()
        builder.content("1. First point")
        builder.newline()
        builder.content("2. Second point")
        builder.newline()
        builder.content("Supporting documents:")
        builder.attachments {
            image("chart.png")
            document("report.pdf")
            document("data.xlsx")
        }
        val (content, attachments) = builder.buildWithAttachments()

        val expectedContent = "Here's my analysis:\n1. First point\n2. Second point\nSupporting documents:"
        assertEquals(expectedContent, content, "Complex content should be correctly built")
        assertEquals(3, attachments.size, "Should have three attachments")
    }

    @Test
    fun testDslSyntax() {
        val (content, attachments) = MessageContentBuilder().apply {
            content("Hello")
            newline()
            content("World")
            attachments {
                image("photo.png")
            }
        }.buildWithAttachments()

        assertEquals("Hello\nWorld", content, "Content should be correctly built with DSL syntax")
        assertEquals(1, attachments.size, "Should have one attachment")
    }

    @Test
    fun testInheritedTextBuilderFunctionality() {
        val (content, attachments) = MessageContentBuilder().apply {
            numbered {
                text("First line")
                newline()
                text("Second line")
            }
        }.buildWithAttachments()

        val expected = "1: First line\n2: Second line"
        assertEquals(expected, content, "Should correctly use inherited numbered functionality")
        assertTrue(attachments.isEmpty(), "No attachments should be present")
    }
}
