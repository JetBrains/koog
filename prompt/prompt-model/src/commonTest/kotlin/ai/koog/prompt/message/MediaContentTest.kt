package ai.koog.prompt.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaContentTest {

    @Test
    fun testImageWithLocalPath() {
        val image = MediaContent.Image("test.png")

        assertEquals("png", image.format)
        assertEquals("image/png", image.getMimeType())
        assertFalse(image.isUrl())
    }

    @Test
    fun testImageWithUrl() {
        val image = MediaContent.Image("https://example.com/image.jpg")

        assertEquals("jpg", image.format)
        assertEquals("image/jpeg", image.getMimeType())
        assertTrue(image.isUrl())
    }

    @Test
    fun testImageWithoutExtension() {
        val image = MediaContent.Image("image")

        assertEquals(null, image.format)
        assertFailsWith<IllegalStateException> {
            image.getMimeType()
        }
    }

    @Test
    fun testImageUnsupportedFormat() {
        val image = MediaContent.Image("test.bmp")
        assertFailsWith<IllegalStateException> {
            image.getMimeType()
        }
    }

    @Test
    fun testImageToBase64WithUrl() {
        val image = MediaContent.Image("https://example.com/test.png")
        assertFailsWith<IllegalStateException> {
            image.toBase64()
        }
    }

    @Test
    fun testVideoWithByteArray() {
        val videoData = byteArrayOf(1, 2, 3, 4, 5)
        val video = MediaContent.Video(videoData, "mp4")

        assertEquals("mp4", video.format)
        assertTrue(video.toBase64().isNotEmpty())
    }

    @Test
    fun testAudioWithByteArray() {
        val audioData = byteArrayOf(10, 20, 30, 40, 50)
        val audio = MediaContent.Audio(audioData, "mp3")

        assertEquals("mp3", audio.format)
        assertTrue(audio.toBase64().isNotEmpty())
    }

    @Test
    fun testFileWithLocalPath() {
        val file = MediaContent.File("document.pdf")

        assertEquals("pdf", file.format)
        assertEquals("application/pdf", file.getMimeType())
        assertEquals("document.pdf", file.fileName())
    }

    @Test
    fun testFileUnsupportedFormat() {
        val file = MediaContent.File("archive.zip")
        assertFailsWith<IllegalStateException> {
            file.getMimeType()
        }
    }
}