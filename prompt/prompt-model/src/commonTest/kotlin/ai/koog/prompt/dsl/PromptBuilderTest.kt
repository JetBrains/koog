package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {

    @Test
    fun testUserMessageWithAttachments() {
        val prompt = Prompt.build("test") {
            user("Check this image", listOf(Attachment.Image(
                content = AttachmentContent.URL("https://example.com/test.png"),
                format = "png",
                mimeType = "image/png",
                fileName = "test.png"
            )))
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")
        assertEquals("Check this image", prompt.messages[0].content, "Message content should match")

        val userMessage = prompt.messages[0] as Message.User
        val singleContent = userMessage.attachments.single()

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/test.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "test.png"
        )
        assertEquals(expectedImage, singleContent, "Attachment should match expected Image")
    }

    @Test
    fun testUserMessageWithAttachmentBuilder() {
        // Note: There's a bug in PromptBuilder.user() method where it uses attachments.lastIndex instead of attachments.size
        // in the subList call, which causes the last attachment to be skipped. This test is adjusted to match the current behavior.
        val prompt = Prompt.build("test") {
            user("Check these files") {
                image("https://example.com/photo.jpg")
                file("https://example.com/report.pdf", "application/pdf")
            }
        }

        // Due to the bug, only the first attachment is included, so there's only one message
        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertTrue(prompt.messages[0] is Message.User, "Message should be a User message")

        assertEquals("Check these files", prompt.messages[0].content, "Message content should match")

        val userMessage = prompt.messages[0] as Message.User
        assertEquals(2, userMessage.attachments.size, "Should have two attachments")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/photo.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo.jpg"
        )
        assertEquals(expectedImage, userMessage.attachments[0], "First attachment should match expected Image")

        val expectedFile = Attachment.File(
            content = AttachmentContent.URL("https://example.com/report.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "report.pdf"
        )
        assertEquals(expectedFile, userMessage.attachments[1], "Second attachment should match expected File")
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

        val expectedContent = "Here's my question:\nHow do I implement a binary search in Kotlin?"
        assertEquals(expectedContent, prompt.messages[0].content, "Message content should match")

        val userMessage = prompt.messages[0] as Message.User
        val singleContent = userMessage.attachments.single()

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/screenshot.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "screenshot.png"
        )
        assertEquals(expectedImage, singleContent, "Attachment should match expected Image")
    }

    @Test
    fun testUserMessageWithMultipleAttachmentsUsingContentBuilder() {
        val prompt = Prompt.build("test") {
            user {
                text("Please analyze these files")

                attachments {
                    image("https://example.com/chart.png")
                    file("https://example.com/data.pdf", "application/pdf")
                    file("https://example.com/report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                }
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have 1 message")

        // First message should have the text and all attachments
        val userMessage = prompt.messages.first() as Message.User
        assertEquals("Please analyze these files", userMessage.content, "First message content should match")

        assertEquals(3, userMessage.attachments.size, "Should have three attachments")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/chart.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "chart.png"
        )
        assertEquals(expectedImage, userMessage.attachments[0], "First attachment should match expected Image")

        val expectedPdfFile = Attachment.File(
            content = AttachmentContent.URL("https://example.com/data.pdf"),
            format = "pdf",
            mimeType = "application/pdf",
            fileName = "data.pdf"
        )
        assertEquals(expectedPdfFile, userMessage.attachments[1], "Second attachment should match expected PDF File")

        val expectedDocxFile = Attachment.File(
            content = AttachmentContent.URL("https://example.com/report.docx"),
            format = "docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            fileName = "report.docx"
        )
        assertEquals(expectedDocxFile, userMessage.attachments[2], "Third attachment should match expected DOCX File")
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

        assertEquals(5, prompt.messages.size, "Prompt should have 4 messages")

        assertTrue(prompt.messages[0] is Message.System, "First message should be a System message")
        assertTrue(prompt.messages[1] is Message.User, "Second message should be a User message")
        assertTrue(prompt.messages[2] is Message.Assistant, "Third message should be an Assistant message")
        assertTrue(prompt.messages[3] is Message.Tool.Call, "Fourth message should be a Tool Call message")
        assertTrue(prompt.messages[4] is Message.Tool.Result, "Fifth message should be a Tool Result message")

        assertEquals(
            "You are a helpful assistant. Please answer user questions accurately.",
            prompt.messages[0].content
        )

        val userMessage = prompt.messages[1] as Message.User
        assertEquals(
            "I have a question about programming.\nHow do I implement a binary search in Kotlin?",
            userMessage.content
        )

        assertEquals(1, userMessage.attachments.size, "User message should have an image attachment")

        val expectedImage = Attachment.Image(
            content = AttachmentContent.URL("https://example.com/code_example.png"),
            format = "png",
            mimeType = "image/png",
            fileName = "code_example.png"
        )
        assertEquals(expectedImage, userMessage.attachments.single(), "Attachment should match expected Image")

        val assistantMessage = prompt.messages[2] as Message.Assistant
        assertTrue(assistantMessage.content.contains("Here's how you can implement binary search in Kotlin:"))
        assertTrue(assistantMessage.content.contains("```kotlin"))

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
