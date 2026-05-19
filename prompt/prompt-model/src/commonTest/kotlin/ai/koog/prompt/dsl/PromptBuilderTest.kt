package ai.koog.prompt.dsl

import ai.koog.prompt.Prompt
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.text.text
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptBuilderTest {

    @Test
    fun testUserMessageWithString() {
        val prompt = Prompt.build("test") {
            user("Hello, how are you?")
            user { +"Hello, how are you?" }
        }
        val expectedText = MessagePart.Text("Hello, how are you?")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithText() {
        val prompt = Prompt.build("test") {
            user(text { +"Hello, how are you?" })
            user { text { +"Hello, how are you?" } }
        }
        val expectedText = MessagePart.Text("Hello, how are you?")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdown() {
        val prompt = Prompt.build("test") {
            user(markdown { +"Hello, how are you?" })
            user { markdown { +"Hello, how are you?" } }
        }
        val expectedText = MessagePart.Text("Hello, how are you?")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownH1() {
        val prompt = Prompt.build("test") {
            user(markdown { h1("Test Header") })
            user { markdown { h1("Test Header") } }
        }
        val expectedText = MessagePart.Text("# Test Header")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownH2() {
        val prompt = Prompt.build("test") {
            user(markdown { h2("Subtitle") })
            user { markdown { h2("Subtitle") } }
        }
        val expectedText = MessagePart.Text("## Subtitle")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownBold() {
        val prompt = Prompt.build("test") {
            user(markdown { bold("important") })
            user { markdown { bold("important") } }
        }
        val expectedText = MessagePart.Text("**important**")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownItalic() {
        val prompt = Prompt.build("test") {
            user(markdown { italic("emphasized") })
            user { markdown { italic("emphasized") } }
        }
        val expectedText = MessagePart.Text("*emphasized*")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownStrikethrough() {
        val prompt = Prompt.build("test") {
            user(markdown { strikethrough("deleted") })
            user { markdown { strikethrough("deleted") } }
        }
        val expectedText = MessagePart.Text("~~deleted~~")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownCode() {
        val prompt = Prompt.build("test") {
            user(markdown { code("println()") })
            user { markdown { code("println()") } }
        }
        val expectedText = MessagePart.Text("`println()`")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownCodeblock() {
        val prompt = Prompt.build("test") {
            user(markdown { codeblock("fun test() = 42", "kotlin") })
            user { markdown { codeblock("fun test() = 42", "kotlin") } }
        }
        val expectedText = MessagePart.Text("```kotlin\nfun test() = 42\n```")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownLink() {
        val prompt = Prompt.build("test") {
            user(markdown { link("GitHub", "https://github.com") })
            user { markdown { link("GitHub", "https://github.com") } }
        }
        val expectedText = MessagePart.Text("[GitHub](https://github.com)")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownImage() {
        val prompt = Prompt.build("test") {
            user(markdown { image("Alt text", "https://example.com/image.png") })
            user { markdown { image("Alt text", "https://example.com/image.png") } }
        }
        val expectedText = MessagePart.Text("![Alt text](https://example.com/image.png)")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownHorizontalRule() {
        val prompt = Prompt.build("test") {
            user(markdown { horizontalRule() })
            user { markdown { horizontalRule() } }
        }
        val expectedText = MessagePart.Text("---")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownBlockquote() {
        val prompt = Prompt.build("test") {
            user(markdown { blockquote("This is a quote") })
            user { markdown { blockquote("This is a quote") } }
        }
        val expectedText = MessagePart.Text("> This is a quote")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    @Ignore // ToDo KG-504 Prompt ending with the markdown br() block is built into empty content parts
    fun testUserMessageWithMarkdownLineBreaks() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    +"Text"
                    br()
                }
            )
            user {
                markdown {
                    +"Text"
                    br()
                }
            }
        }
        val expectedText = MessagePart.Text("Text\n\n")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have one text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have one text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownBulletedList() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    bulleted {
                        item("First item")
                        item("Second item")
                    }
                }
            )
            user {
                markdown {
                    bulleted {
                        item("First item")
                        item("Second item")
                    }
                }
            }
        }
        val expectedText = MessagePart.Text("- First item\n- Second item")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownNumberedList() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    numbered {
                        item("Step 1")
                        item("Step 2")
                    }
                }
            )
            user {
                markdown {
                    numbered {
                        item("Step 1")
                        item("Step 2")
                    }
                }
            }
        }
        val expectedText = MessagePart.Text("1. Step 1\n2. Step 2")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownTable() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    table(
                        listOf("Name", "Age"),
                        listOf(
                            listOf("John", "25"),
                            listOf("Jane", "30")
                        )
                    )
                }
            )
            user {
                markdown {
                    table(
                        listOf("Name", "Age"),
                        listOf(
                            listOf("John", "25"),
                            listOf("Jane", "30")
                        )
                    )
                }
            }
        }
        val expectedText =
            MessagePart.Text("| Name | Age |\n| :--- | :--- |\n| John | 25 |\n| Jane | 30 |")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithTextBuilder() {
        val prompt = Prompt.build("test") {
            user {
                +"Hello, how are you?"
                +"Good, and you?"
                text(" Let's go to the beach!")
            }
        }

        val expectedText = MessagePart.Text("Hello, how are you?\nGood, and you? Let's go to the beach!")
        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        val userMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")

        assertEquals(1, userMessage.parts.size, "Should have only text part")
        assertEquals(
            expectedText,
            userMessage.parts[0],
            "Should have same text"
        )
    }

    @Test
    fun testUserMessageMultipleTextWithMultipleAttachment() {
        val prompt = Prompt.build("test") {
            user {
                +"Hello, how are you?"
                +"Here is my photo"
                image("https://example.com/photo1.jpg")
                +"I'm good!"
                +"And here is mine"
                image("https://example.com/photo2.jpg")
            }
        }

        val expectedText = MessagePart.Text("Hello, how are you?\nHere is my photo")
        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        val userMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")

        assertEquals(4, userMessage.parts.size, "Should have 4 parts")
        assertEquals(
            expectedText,
            userMessage.parts[0],
            "Should have same text"
        )

        val expectedFirstImage = MessagePart.Attachment(
            AttachmentSource.Image(
                content = AttachmentContent.URL("https://example.com/photo1.jpg"),
                format = "jpg",
                mimeType = "image/jpg",
                fileName = "photo1.jpg"
            )
        )

        assertEquals(
            expectedFirstImage,
            userMessage.parts[1],
            "Should have same image url"
        )
        assertEquals(
            MessagePart.Text("I'm good!\nAnd here is mine"),
            userMessage.parts[2],
            "Should have same text"
        )

        val expectedSecondImage = MessagePart.Attachment(
            AttachmentSource.Image(
                content = AttachmentContent.URL("https://example.com/photo2.jpg"),
                format = "jpg",
                mimeType = "image/jpg",
                fileName = "photo2.jpg"
            )
        )
        assertEquals(expectedSecondImage, userMessage.parts[3], "Should have same image url")
    }

    @Test
    fun testUserMessageWithAttachments() {
        val prompt = Prompt.build("test") {
            user {
                text("Check this image")
                image(
                    AttachmentSource.Image(
                        content = AttachmentContent.URL("https://example.com/test.png"),
                        format = "png",
                        mimeType = "image/png",
                        fileName = "test.png"
                    )
                )
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        val userMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(2, userMessage.parts.size, "Should have text part and image part")

        val expectedText = MessagePart.Text("Check this image")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = MessagePart.Attachment(
            AttachmentSource.Image(
                content = AttachmentContent.URL("https://example.com/test.png"),
                format = "png",
                mimeType = "image/png",
                fileName = "test.png"
            )
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")
    }

    @Test
    fun testUserMessageWithContentPartsBuilder() {
        val prompt = Prompt.build("test") {
            user {
                text("Check these files")
                image("https://example.com/photo.jpg")
                file("https://example.com/report.pdf", "application/pdf")
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        val userMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")

        assertEquals(3, userMessage.parts.size, "Should have text part, image part, and file part")

        val expectedText = MessagePart.Text("Check these files")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = MessagePart.Attachment(
            AttachmentSource.Image(
                content = AttachmentContent.URL("https://example.com/photo.jpg"),
                format = "jpg",
                mimeType = "image/jpg",
                fileName = "photo.jpg"
            )
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")

        val expectedFile = MessagePart.Attachment(
            AttachmentSource.File(
                content = AttachmentContent.URL("https://example.com/report.pdf"),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "report.pdf"
            )
        )
        assertEquals(expectedFile, userMessage.parts[2], "Third part should match expected File")
    }

    @Test
    fun testUserMessageWithContentBuilderWithAttachment() {
        val prompt = Prompt.build("test") {
            user {
                text("Here's my question:")
                newline()
                text("How do I implement a binary search in Kotlin?")
                image("https://example.com/screenshot.png")
            }
        }

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        val userMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")

        assertEquals(2, userMessage.parts.size, "Should have text part and image part")

        val expectedText = MessagePart.Text("Here's my question:\nHow do I implement a binary search in Kotlin?")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = MessagePart.Attachment(
            AttachmentSource.Image(
                content = AttachmentContent.URL("https://example.com/screenshot.png"),
                format = "png",
                mimeType = "image/png",
                fileName = "screenshot.png"
            )
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

        val userMessage = assertIs<Message.User>(prompt.messages.first(), "Message should be a User message")

        assertEquals(4, userMessage.parts.size, "Should have text part and three attachment parts")

        val expectedText = MessagePart.Text("Please analyze these files")
        assertEquals(expectedText, userMessage.parts[0], "First part should be text")

        val expectedImage = MessagePart.Attachment(
            AttachmentSource.Image(
                content = AttachmentContent.URL("https://example.com/chart.png"),
                format = "png",
                mimeType = "image/png",
                fileName = "chart.png"
            )
        )
        assertEquals(expectedImage, userMessage.parts[1], "Second part should match expected Image")

        val expectedPdfFile = MessagePart.Attachment(
            AttachmentSource.File(
                content = AttachmentContent.URL("https://example.com/data.pdf"),
                format = "pdf",
                mimeType = "application/pdf",
                fileName = "data.pdf"
            )
        )
        assertEquals(expectedPdfFile, userMessage.parts[2], "Third part should match expected PDF File")

        val expectedDocxFile = MessagePart.Attachment(
            AttachmentSource.File(
                content = AttachmentContent.URL("https://example.com/report.docx"),
                format = "docx",
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                fileName = "report.docx"
            )
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
                text("I have a question about programming.")
                newline()
                text("How do I implement a binary search in Kotlin?")
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

            assistant {
                toolCall(
                    id = "tool_1",
                    tool = "code_analyzer",
                    args = JsonObject(mapOf("language" to JsonPrimitive("kotlin")))
                )
            }

            user {
                toolResult("tool_1", "code_analyzer", "0 errors found")
            }
        }

        assertEquals(5, prompt.messages.size, "Prompt should have 5 messages")

        // System message should have Text content
        val systemMessage = assertIs<Message.System>(prompt.messages[0], "First message should be a System message")
        assertEquals(1, systemMessage.parts.size, "Should have only text part")
        val expectedSystemText =
            MessagePart.Text("You are a helpful assistant. Please answer user questions accurately.")
        assertEquals(expectedSystemText, systemMessage.parts[0], "First part should be text")

        // User message should have Parts content (Text + Image)
        val userMessage = assertIs<Message.User>(prompt.messages[1], "Second message should be a User message")
        assertEquals(2, userMessage.parts.size, "Should have text part and image part")

        val expectedUserText =
            MessagePart.Text("I have a question about programming.\nHow do I implement a binary search in Kotlin?")
        assertEquals(expectedUserText, userMessage.parts[0], "First part should be text")

        val expectedUserImage = MessagePart.Attachment(
            source = AttachmentSource.Image(
                content = AttachmentContent.URL(
                    "https://example.com/code_example.png"
                ),
                format = "png",
                mimeType = "image/png",
                fileName = "code_example.png"
            )
        )
        assertEquals(expectedUserImage, userMessage.parts[1], "Second part should match expected Image")

        // Assistant message should have Text content
        val assistantMessage = assertIs<Message.Assistant>(prompt.messages[2], "Third message should be an Assistant message")
        assertEquals(1, assistantMessage.parts.size, "Should have text part")
        val textPart = assertIs<MessagePart.Text>(assistantMessage.parts[0])
        assertTrue(textPart.text.contains("Here's how you can implement binary search in Kotlin:"))
        assertTrue(textPart.text.contains("```kotlin"))

        // Assistant messages should have Tool.Call part
        val toolCallMessage = assertIs<Message.Assistant>(prompt.messages[3], "Fourth message should be an Assistant message")
        assertEquals(1, toolCallMessage.parts.size, "Should have only Tool.Call part")
        val toolCallPart = assertIs<MessagePart.Tool.Call>(toolCallMessage.parts[0])
        assertEquals("tool_1", toolCallPart.id)
        assertEquals("code_analyzer", toolCallPart.tool)
        assertEquals(buildJsonObject { put("language", JsonPrimitive("kotlin")) }, toolCallPart.argsJson)

        val toolResultMessage = assertIs<Message.User>(prompt.messages[4], "Fifth message should be a User message")
        assertEquals(1, toolResultMessage.parts.size, "Should have only Tool.Result part")
        val toolResultPart = assertIs<MessagePart.Tool.Result>(toolResultMessage.parts[0])
        assertEquals("tool_1", toolResultPart.id)
        assertEquals("code_analyzer", toolResultPart.tool)
        assertEquals("0 errors found", toolResultPart.output)
        assertFalse(toolResultPart.isError)
    }

    @Test
    fun testUserMessageWithMarkdownPlainText() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    +"text"
                    text(" followed by plain text")
                }
            )
            user {
                markdown {
                    +"text"
                    text(" followed by plain text")
                }
            }
        }

        val expectedText = MessagePart.Text("text followed by plain text")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownTextWithNewLine() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    +"text"
                    textWithNewLine(" followed by textWithNewLine")
                }
            )
            user {
                markdown {
                    +"text"
                    textWithNewLine(" followed by textWithNewLine")
                }
            }
        }
        val expectedText = MessagePart.Text("text\n followed by textWithNewLine")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownPadding() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    +"text"
                    padding("  ") {
                        +"followed by padding"
                    }
                }
            )
            user {
                markdown {
                    +"text"
                    padding("  ") {
                        +"followed by padding"
                    }
                }
            }
        }
        val expectedText = MessagePart.Text("text\n  followed by padding")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    @Ignore // ToDo KG-504 Prompt ending with the markdown line break block is built into empty content parts
    fun testUserMessageWithMarkdownNewline() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    +"text"
                    newline()
                }
            )
            user {
                markdown {
                    +"text"
                    newline()
                }
            }
        }
        val expectedText = MessagePart.Text("text\n")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    @Ignore // ToDo KG-504 Prompt ending with the markdown br() block is built into empty content parts
    fun testUserMessageWithMarkdownMixedTextAndMarkdown() {
        val prompt = Prompt.build("test") {
            user(
                markdown {
                    +"text"
                    h2("Header with h2")
                    newline()
                    +"text followed by "
                    bold("bold")
                    br()
                    +"text followed by "
                    italic("italic")
                    br()
                }
            )
            user {
                markdown {
                    +"text"
                    h2("Header with h2")
                    newline()
                    +"text followed by "
                    bold("bold")
                    br()
                    +"text followed by "
                    italic("italic")
                    br()
                }
            }
        }
        val expectedText =
            MessagePart.Text("text\n## Header with h2\ntext followed by \n**bold**\n\ntext followed by \n*italic*")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        val firstUserMessage = assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, firstUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, firstUserMessage.parts[0], "Should have same text")

        val secondUserMessage = assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, secondUserMessage.parts.size, "Should have only text part")
        assertEquals(expectedText, secondUserMessage.parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithTrailingNewline() {
        val prompt = Prompt.build("test") {
            user {
                +"Text\n"
            }
        }

        prompt.messages[0].parts shouldBeEqual listOf(MessagePart.Text("Text\n"))
    }
}
