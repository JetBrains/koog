package ai.koog.prompt.dsl

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.text.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {

    @Test
    fun testUserMessageWithAttachments() {
        val prompt = Prompt.build("test") {
            user {
                text("Check this image")
                image(
                    ContentPart.Image(
                        content = AttachmentContent.URL("https://example.com/test.png"),
                        format = "png",
                        mimeType = "image/png",
                        fileName = "test.png"
                    )
                )
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        val userMessage = prompt.messages[0] as Message.User
        assertEquals(2, userMessage.parts.size, "Should have text part and image part")

        val expectedText = ContentPart.Text("Check this image")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/test.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "test.png"
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")
    }

    @Test
    fun testUserMessageWithAttachmentBuilder() {
        val prompt = Prompt.build("test") {
            user {
                text("Check these files")
                image("https://example.com/photo.jpg")
                file("https://example.com/report.pdf", "application/pdf")
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        val userMessage = prompt.messages[0] as Message.User

        assertEquals(3, userMessage.parts.size, "Should have text part, image part, and file part")

        val expectedText = ContentPart.Text("Check these files")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo.jpg"
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")

        val expectedFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/report.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        assertEquals(expectedFile, userMessage.parts[2], "Third part should match expected File")
    }

    @Test
    fun testUserMessageWithContentBuilderWithAttachment() {
        val prompt = Prompt.build("test") {
            user {
                text {
                    text("Here's my question:")
                    newline()
                    text("How do I implement a binary search in Kotlin?")
                }
                image("https://example.com/screenshot.png")
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        val userMessage = prompt.messages[0] as Message.User

        assertEquals(2, userMessage.parts.size, "Should have text part and image part")

        val expectedText = ContentPart.Text("Here's my question:\nHow do I implement a binary search in Kotlin?")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/screenshot.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "screenshot.png"
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")
    }

    @Test
    fun testUserMessageWithMultipleAttachmentsUsingContentBuilder() {
        val prompt = Prompt.build("test") {
            user {
                text("Please analyze these files")
                image("https://example.com/chart.png")
                file("https://example.com/data.pdf", "application/pdf")
                file(
                    "https://example.com/report.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have 1 message")

        val userMessage = prompt.messages.first() as Message.User

        assertEquals(4, userMessage.parts.size, "Should have text part and three attachment parts")

        val expectedText = ContentPart.Text("Please analyze these files")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/chart.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "chart.png"
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")

        val expectedPdfFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/data.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "data.pdf"
        )
        assertEquals(expectedPdfFile, userMessage.parts[2], "Third part should match expected PDF File")

        val expectedDocxFile = ContentPart.File(
            content = AttachmentContent.URL("https://example.com/report.docx"),
            format = "docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            fileName = "report.docx"
        )
        assertEquals(expectedDocxFile, userMessage.parts[3], "Fourth part should match expected DOCX File")
    }

    @Test
    fun testComplexPromptWithAllMessageTypes() {
        val prompt = Prompt.build("test") {
            system {
                text("You are a helpful assistant.")
                text(" Please answer user questions accurately.")
            }

            user {
                text {
                    text("I have a question about programming.")
                    newline()
                    text("How do I implement a binary search in Kotlin?")
                }

                image("https://example.com/code_example.png")
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
        assertEquals(1, systemMessage.parts.size, "Should have only text part")
        val expectedSystemText =
            ContentPart.Text("You are a helpful assistant. Please answer user questions accurately.")
        assertEquals(expectedSystemText, systemMessage.parts[0], "First part should be text")

        // User message should have Parts content (Text + Image)
        val userMessage = prompt.messages[1] as Message.User
        assertEquals(2, userMessage.parts.size, "Should have text part and image part")

        val expectedUserText =
            ContentPart.Text("I have a question about programming.\nHow do I implement a binary search in Kotlin?")
        assertEquals(expectedUserText, userMessage.parts[0], "First part should be text")

        val expectedUserImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/code_example.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "code_example.png"
        )
        assertEquals(expectedUserImage, userMessage.parts[1], "Second part should match expected Image")

        // Assistant message should have Text content
        val assistantMessage = prompt.messages[2] as Message.Assistant
        assertEquals(1, assistantMessage.parts.size, "Should have text part")
        val assistantText = assistantMessage.content
        assertTrue(assistantText.contains("Here's how you can implement binary search in Kotlin:"))
        assertTrue(assistantText.contains("```kotlin"))

        // Tool messages should have Text content
        val toolCallMessage = prompt.messages[3] as Message.Tool.Call
        assertEquals("tool_1", toolCallMessage.id)
        assertEquals("code_analyzer", toolCallMessage.tool)
        assertEquals("Analyzing the code example...", toolCallMessage.content)

        val toolResultMessage = prompt.messages[4] as Message.Tool.Result
        assertEquals("tool_1", toolResultMessage.id)
        assertEquals("code_analyzer", toolResultMessage.tool)
        assertEquals("The code looks correct.", toolResultMessage.content)
    }
}
