package ai.koog.prompt.dsl

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Content
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptBuilderTest {

    @Test
    fun testUserMessageWithAttachments() {
        val prompt = Prompt.build("test") {
            user(
                "Check this image",
                listOf(
                    ContentPart.Image(
                        content = AttachmentContent.URL("https://example.com/test.png"),
                        format = "png",
                        mimeType = "image/png",
                        fileName = "test.png"
                    )
                )
            )
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        val userMessage = prompt.messages[0] as Message.User
        assertIs<Content.Parts>(userMessage.content, "User message with attachments should have Parts content")

        val parts = userMessage.content.value
        assertEquals(2, parts.size, "Should have text part and image part")

        val expectedText = ContentPart.Text("Check this image")
        assertEquals(expectedText, parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/test.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "test.png"
        )
        assertEquals(expectedImage, parts[1], "Second part should match expected Image")
    }

    @Test
    fun testUserMessageWithAttachmentBuilder() {
        val prompt = Prompt.build("test") {
            user("Check these files") {
                image("https://example.com/photo.jpg")
                file("https://example.com/report.pdf", "application/pdf")
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        val userMessage = prompt.messages[0] as Message.User
        assertIs<Content.Parts>(userMessage.content, "User message with attachments should have Parts content")

        val parts = userMessage.content.value
        assertEquals(3, parts.size, "Should have text part, image part, and file part")

        val expectedText = ContentPart.Text("Check these files")
        assertEquals(expectedText, parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo.jpg"
        )
        assertEquals(expectedImage, parts[1], "Second part should match expected Image")

        val expectedFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/report.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        assertEquals(expectedFile, parts[2], "Third part should match expected File")
    }

    @Test
    fun testUserMessageWithContentBuilderWithAttachment() {
        val prompt = Prompt.build("test") {
            user {
                text("Here's my question:")
                newline()
                text("How do I implement a binary search in Kotlin?")

                attachments {
                    image("https://example.com/screenshot.png")
                }
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        val userMessage = prompt.messages[0] as Message.User
        assertIs<Content.Parts>(userMessage.content, "User message with attachments should have Parts content")

        val parts = userMessage.content.value
        assertEquals(2, parts.size, "Should have text part and image part")

        val expectedText = ContentPart.Text("Here's my question:\nHow do I implement a binary search in Kotlin?")
        assertEquals(expectedText, parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/screenshot.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "screenshot.png"
        )
        assertEquals(expectedImage, parts[1], "Second part should match expected Image")
    }

    @Test
    fun testUserMessageWithMultipleAttachmentsUsingContentBuilder() {
        val prompt = Prompt.build("test") {
            user {
                text("Please analyze these files")

                attachments {
                    image("https://example.com/chart.png")
                    file("https://example.com/data.pdf", "application/pdf")
                    file(
                        "https://example.com/report.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                }
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have 1 message")

        val userMessage = prompt.messages.first() as Message.User
        assertIs<Content.Parts>(userMessage.content, "User message with attachments should have Parts content")

        val parts = userMessage.content.value
        assertEquals(4, parts.size, "Should have text part and three attachment parts")

        val expectedText = ContentPart.Text("Please analyze these files")
        assertEquals(expectedText, parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/chart.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "chart.png"
        )
        assertEquals(expectedImage, parts[1], "Second part should match expected Image")

        val expectedPdfFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/data.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "data.pdf"
        )
        assertEquals(expectedPdfFile, parts[2], "Third part should match expected PDF File")

        val expectedDocxFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/report.docx"),
            format = "docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            fileName = "report.docx"
        )
        assertEquals(expectedDocxFile, parts[3], "Fourth part should match expected DOCX File")
    }

    @Test
    fun testComplexPromptWithAllMessageTypes() {
        val prompt = Prompt.build("test") {
            system {
                text("You are a helpful assistant.")
                text(" Please answer user questions accurately.")
            }

            user {
                text("I have a question about programming.")
                newline()
                text("How do I implement a binary search in Kotlin?")

                attachments {
                    image("https://example.com/code_example.png")
                }
            }

            assistant {
                text("Here's how you can implement binary search in Kotlin:")
                newline()
                text("```kotlin")
                newline()
                text("fun binarySearch(array: IntArray, target: Int): Int {")
                newline()
                text("    // Implementation details")
                newline()
                text("}")
                newline()
                text("```")
            }

            tool {
                call("tool_1", "code_analyzer", "Analyzing the code example...")
                result("tool_1", "code_analyzer", "The code looks correct.")
            }
        }

        assertEquals(5, prompt.messages.size, "Prompt should have 5 messages")

        assertTrue(prompt.messages[0] is Message.System, "First message should be a System message")
        assertTrue(prompt.messages[1] is Message.User, "Second message should be a User message")
        assertTrue(prompt.messages[2] is Message.Assistant, "Third message should be an Assistant message")
        assertTrue(prompt.messages[3] is Message.Tool.Call, "Fourth message should be a Tool Call message")
        assertTrue(prompt.messages[4] is Message.Tool.Result, "Fifth message should be a Tool Result message")

        // System message should have Text content
        val systemMessage = prompt.messages[0] as Message.System
        assertIs<Content.Text>(systemMessage.content, "System message should have Text content")
        assertEquals(
            "You are a helpful assistant. Please answer user questions accurately.",
            systemMessage.content.text()
        )

        // User message should have Parts content (text + attachment)
        val userMessage = prompt.messages[1] as Message.User
        assertIs<Content.Parts>(userMessage.content, "User message with attachments should have Parts content")

        val userParts = userMessage.content.value
        assertEquals(2, userParts.size, "Should have text part and image part")

        val expectedUserText = ContentPart.Text("I have a question about programming.\nHow do I implement a binary search in Kotlin?")
        assertEquals(expectedUserText, userParts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/code_example.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "code_example.png"
        )
        assertEquals(expectedImage, userParts[1], "Second part should match expected Image")

        // Assistant message should have Text content
        val assistantMessage = prompt.messages[2] as Message.Assistant
        assertIs<Content.Text>(assistantMessage.content, "Assistant message should have Text content")
        val assistantText = assistantMessage.content.text()
        assertTrue(assistantText.contains("Here's how you can implement binary search in Kotlin:"))
        assertTrue(assistantText.contains("```kotlin"))

        // Tool messages should have Text content
        val toolCallMessage = prompt.messages[3] as Message.Tool.Call
        assertEquals("tool_1", toolCallMessage.id)
        assertEquals("code_analyzer", toolCallMessage.tool)
        assertIs<Content.Text>(toolCallMessage.content, "Tool call message should have Text content")
        assertEquals("Analyzing the code example...", toolCallMessage.content.text())

        val toolResultMessage = prompt.messages[4] as Message.Tool.Result
        assertEquals("tool_1", toolResultMessage.id)
        assertEquals("code_analyzer", toolResultMessage.tool)
        assertIs<Content.Text>(toolResultMessage.content, "Tool result message should have Text content")
        assertEquals("The code looks correct.", toolResultMessage.content.text())
    }
}
