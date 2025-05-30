package ai.koog.prompt.dsl

import ai.koog.prompt.message.MediaContent
import ai.koog.prompt.message.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserContentBuilderTest {

    @Test
    fun testSimpleTextMessage() {
        val builder = UserContentBuilder()
        builder.text("Hello, world!")

        val messages = builder.build()

        assertEquals(1, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("Hello, world!", messages[0].content)
        assertEquals(null, (messages[0] as Message.User).mediaContent)
    }

    @Test
    fun testMultipleTextMessages() {
        val builder = UserContentBuilder()
        builder.text("First message")
        builder.text("Second message")
        builder.text("Third message")

        val messages = builder.build()

        assertEquals(3, messages.size)
        messages.forEach { assertIs<Message.User>(it) }
        assertEquals("First message", messages[0].content)
        assertEquals("Second message", messages[1].content)
        assertEquals("Third message", messages[2].content)
    }

    @Test
    fun testTextWithTextContentBuilder() {
        val builder = UserContentBuilder()
        builder.text {
            text("Line 1")
            newline()
            text("Line 2")
            br()
            text("Line 3")
        }

        val messages = builder.build()

        assertEquals(1, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("Line 1\nLine 2\n\nLine 3", messages[0].content)
    }

    @Test
    fun testImageMessage() {
        val builder = UserContentBuilder()
        builder.image("test.png")

        val messages = builder.build()

        assertEquals(1, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("", messages[0].content)

        val mediaContent = (messages[0] as Message.User).mediaContent
        assertIs<MediaContent.Image>(mediaContent)
        assertEquals("test.png", mediaContent.source)
    }

    @Test
    fun testImageWithUrl() {
        val builder = UserContentBuilder()
        builder.image("https://example.com/image.jpg")

        val messages = builder.build()

        assertEquals(1, messages.size)
        val mediaContent = (messages[0] as Message.User).mediaContent
        assertIs<MediaContent.Image>(mediaContent)
        assertEquals("https://example.com/image.jpg", mediaContent.source)
        assertTrue(mediaContent.isUrl())
    }

    @Test
    fun testAudioMessage() {
        val builder = UserContentBuilder()
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        builder.audio(audioData, "mp3")

        val messages = builder.build()

        assertEquals(1, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("", messages[0].content)

        val mediaContent = (messages[0] as Message.User).mediaContent
        assertIs<MediaContent.Audio>(mediaContent)
        assertEquals(audioData.toList(), mediaContent.data.toList())
        assertEquals("mp3", mediaContent.format)
    }

    @Test
    fun testDocumentMessage() {
        val builder = UserContentBuilder()
        builder.document("document.pdf")

        val messages = builder.build()

        assertEquals(1, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals("", messages[0].content)

        val mediaContent = (messages[0] as Message.User).mediaContent
        assertIs<MediaContent.File>(mediaContent)
        assertEquals("document.pdf", mediaContent.source)
    }

    @Test
    fun testMixedContentTypes() {
        val builder = UserContentBuilder()
        builder.text("Here's some text")
        builder.image("photo.jpg")
        builder.text {
            text("Formatted text with")
            newline()
            text("multiple lines")
        }
        builder.document("report.pdf")
        builder.audio(byteArrayOf(10, 20, 30), "wav")

        val messages = builder.build()

        assertEquals(5, messages.size)

        // Check first message (text)
        assertIs<Message.User>(messages[0])
        assertEquals("Here's some text", messages[0].content)
        assertEquals(null, (messages[0] as Message.User).mediaContent)

        // Check second message (image)
        assertIs<Message.User>(messages[1])
        assertEquals("", messages[1].content)
        assertIs<MediaContent.Image>((messages[1] as Message.User).mediaContent)

        // Check third message (formatted text)
        assertIs<Message.User>(messages[2])
        assertEquals("Formatted text with\nmultiple lines", messages[2].content)
        assertEquals(null, (messages[2] as Message.User).mediaContent)

        // Check fourth message (document)
        assertIs<Message.User>(messages[3])
        assertEquals("", messages[3].content)
        assertIs<MediaContent.File>((messages[3] as Message.User).mediaContent)

        // Check fifth message (audio)
        assertIs<Message.User>(messages[4])
        assertEquals("", messages[4].content)
        assertIs<MediaContent.Audio>((messages[4] as Message.User).mediaContent)
    }

    @Test
    fun testEmptyBuilder() {
        val builder = UserContentBuilder()
        val messages = builder.build()

        assertEquals(0, messages.size)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun testBuilderReusability() {
        val builder = UserContentBuilder()
        builder.text("First build")

        val firstBuild = builder.build()
        assertEquals(1, firstBuild.size)

        builder.text("Second build")
        val secondBuild = builder.build()
        assertEquals(2, secondBuild.size)
        assertEquals("First build", secondBuild[0].content)
        assertEquals("Second build", secondBuild[1].content)
    }

    @Test
    fun testComplexTextContentBuilder() {
        val builder = UserContentBuilder()
        builder.text {
            text("Header")
            br()
            padding("  ") {
                text("Indented line 1")
                newline()
                text("Indented line 2")
            }
            newline()
            text("Footer")
        }

        val messages = builder.build()

        assertEquals(1, messages.size)
        val content = messages[0].content
        val expectedContent = "Header\n\n  Indented line 1\n  Indented line 2\nFooter"
        assertEquals(expectedContent, content)
    }
}