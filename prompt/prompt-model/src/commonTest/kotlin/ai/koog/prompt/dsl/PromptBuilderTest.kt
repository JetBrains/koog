package ai.koog.prompt.dsl

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.text.text
import io.kotest.matchers.equals.shouldBeEqual
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptBuilderTest {

    @Test
    fun testUserMessageWithString() {
        val prompt = Prompt.build("test") {
            user("Hello, how are you?")
            user { +"Hello, how are you?" }
        }
        val expectedText = ContentPart.Text("Hello, how are you?")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithText() {
        val prompt = Prompt.build("test") {
            user(text { +"Hello, how are you?" })
            user { text { +"Hello, how are you?" } }
        }
        val expectedText = ContentPart.Text("Hello, how are you?")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdown() {
        val prompt = Prompt.build("test") {
            user(markdown { +"Hello, how are you?" })
            user { markdown { +"Hello, how are you?" } }
        }
        val expectedText = ContentPart.Text("Hello, how are you?")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownH1() {
        val prompt = Prompt.build("test") {
            user(markdown { h1("Test Header") })
            user { markdown { h1("Test Header") } }
        }
        val expectedText = ContentPart.Text("# Test Header")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownH2() {
        val prompt = Prompt.build("test") {
            user(markdown { h2("Subtitle") })
            user { markdown { h2("Subtitle") } }
        }
        val expectedText = ContentPart.Text("## Subtitle")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownBold() {
        val prompt = Prompt.build("test") {
            user(markdown { bold("important") })
            user { markdown { bold("important") } }
        }
        val expectedText = ContentPart.Text("**important**")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownItalic() {
        val prompt = Prompt.build("test") {
            user(markdown { italic("emphasized") })
            user { markdown { italic("emphasized") } }
        }
        val expectedText = ContentPart.Text("*emphasized*")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownStrikethrough() {
        val prompt = Prompt.build("test") {
            user(markdown { strikethrough("deleted") })
            user { markdown { strikethrough("deleted") } }
        }
        val expectedText = ContentPart.Text("~~deleted~~")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownCode() {
        val prompt = Prompt.build("test") {
            user(markdown { code("println()") })
            user { markdown { code("println()") } }
        }
        val expectedText = ContentPart.Text("`println()`")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownCodeblock() {
        val prompt = Prompt.build("test") {
            user(markdown { codeblock("fun test() = 42", "kotlin") })
            user { markdown { codeblock("fun test() = 42", "kotlin") } }
        }
        val expectedText = ContentPart.Text("```kotlin\nfun test() = 42\n```")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownLink() {
        val prompt = Prompt.build("test") {
            user(markdown { link("GitHub", "https://github.com") })
            user { markdown { link("GitHub", "https://github.com") } }
        }
        val expectedText = ContentPart.Text("[GitHub](https://github.com)")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownImage() {
        val prompt = Prompt.build("test") {
            user(markdown { image("Alt text", "https://example.com/image.png") })
            user { markdown { image("Alt text", "https://example.com/image.png") } }
        }
        val expectedText = ContentPart.Text("![Alt text](https://example.com/image.png)")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownHorizontalRule() {
        val prompt = Prompt.build("test") {
            user(markdown { horizontalRule() })
            user { markdown { horizontalRule() } }
        }
        val expectedText = ContentPart.Text("---")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithMarkdownBlockquote() {
        val prompt = Prompt.build("test") {
            user(markdown { blockquote("This is a quote") })
            user { markdown { blockquote("This is a quote") } }
        }
        val expectedText = ContentPart.Text("> This is a quote")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("Text\n\n")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have one text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have one text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("- First item\n- Second item")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("1. Step 1\n2. Step 2")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("| Name | Age |\n| :--- | :--- |\n| John | 25 |\n| Jane | 30 |")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")

        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(
            ContentPart.Text("Hello, how are you?\nGood, and you? Let's go to the beach!"),
            prompt.messages[0].parts[0],
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

        assertEquals(1, prompt.messages.size, "Prompt should have one message")
        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")

        assertEquals(4, prompt.messages[0].parts.size, "Should have 4 parts")
        assertEquals(
            ContentPart.Text("Hello, how are you?\nHere is my photo"),
            prompt.messages[0].parts[0],
            "Should have same text"
        )

        val expectedFirstImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo1.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo1.jpg"
        )
        assertEquals(expectedFirstImage, prompt.messages[0].parts[1], "Should have same image url")
        assertEquals(
            ContentPart.Text("I'm good!\nAnd here is mine"),
            prompt.messages[0].parts[2],
            "Should have same text"
        )

        val expectedSecondImage = ContentPart.Image(
            content = AttachmentContent.URL("https://example.com/photo2.jpg"),
            format = "jpg",
            mimeType = "image/jpg",
            fileName = "photo2.jpg"
        )
        assertEquals(expectedSecondImage, prompt.messages[0].parts[3], "Should have same image url")
    }

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
    fun testUserMessageWithAttachmentsOldAPI() {
        val prompt = Prompt.build("test") {
            user {
                text("Check this image")
                attachments {
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
    fun testUserMessageWithContentPartsBuilder() {
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
                text("Here's my question:")
                newline()
                text("How do I implement a binary search in Kotlin?")
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
            content = AttachmentContent.URL(
                "https://example.com/code_example.png"
            ),
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

        val expectedText = ContentPart.Text("text followed by plain text")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("text\n followed by textWithNewLine")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("text\n  followed by padding")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
        val expectedText = ContentPart.Text("text\n")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
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
            ContentPart.Text("text\n## Header with h2\ntext followed by \n**bold**\n\ntext followed by \n*italic*")

        assertEquals(2, prompt.messages.size, "Prompt should have two messages")

        assertIs<Message.User>(prompt.messages[0], "Message should be a User message")
        assertEquals(1, prompt.messages[0].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[0].parts[0], "Should have same text")

        assertIs<Message.User>(prompt.messages[1], "Message should be a User message")
        assertEquals(1, prompt.messages[1].parts.size, "Should have only text part")
        assertEquals(expectedText, prompt.messages[1].parts[0], "Should have same text")
    }

    @Test
    fun testUserMessageWithTrailingNewline() {
        val prompt = Prompt.build("test") {
            user {
                +"Text\n"
            }
        }

        prompt.messages[0].parts shouldBeEqual listOf(ContentPart.Text("Text\n"))
    }
}
