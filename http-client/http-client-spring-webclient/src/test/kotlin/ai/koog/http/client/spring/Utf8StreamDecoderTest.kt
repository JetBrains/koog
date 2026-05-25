package ai.koog.http.client.spring

import org.springframework.core.io.buffer.DefaultDataBufferFactory
import java.nio.charset.MalformedInputException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Utf8StreamDecoderTest {

    private val factory = DefaultDataBufferFactory()

    private fun dataBuffer(bytes: ByteArray) = factory.wrap(bytes)

    @Test
    fun testNdjsonStreamWithEmojiTokenSplitAcrossBuffers() {
        val ndjson = """
            {"model":"llama3.2","message":{"role":"assistant","content":"Hello"},"done":false}
            {"model":"llama3.2","message":{"role":"assistant","content":" 😀"},"done":false}
            {"model":"llama3.2","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
        """.trimIndent()
        val bytes = ndjson.toByteArray(Charsets.UTF_8)
        val split = ndjson.substringBefore("😀").toByteArray(Charsets.UTF_8).size + 2 // split inside 😀 (F0 9F | 98 80)

        val decoder = Utf8StreamDecoder()
        val chunk1 = decoder.decode(dataBuffer(bytes.copyOfRange(0, split)))
        val chunk2 = decoder.decode(dataBuffer(bytes.copyOfRange(split, bytes.size)))
        assertEquals(ndjson, chunk1 + chunk2 + decoder.finish())
    }

    @Test
    fun testDecodeAscii() {
        val decoder = Utf8StreamDecoder()
        assertEquals("hello", decoder.decode(dataBuffer("hello".toByteArray())))
        assertEquals("", decoder.finish())
    }

    @Test
    fun testDecodeMultiByteCharSplitAfterFirstByte() {
        val decoder = Utf8StreamDecoder()
        val euro = "€".toByteArray(Charsets.UTF_8) // E2 82 AC
        assertEquals("", decoder.decode(dataBuffer(euro.copyOfRange(0, 1))))
        assertEquals("€", decoder.decode(dataBuffer(euro.copyOfRange(1, 3))))
        assertEquals("", decoder.finish())
    }

    @Test
    fun testDecodeAfterFinishThrows() {
        val decoder = Utf8StreamDecoder()
        decoder.finish()
        assertFailsWith<IllegalStateException> {
            decoder.decode(dataBuffer("x".toByteArray()))
        }
    }

    @Test
    fun testMalformedByteThrows() {
        val decoder = Utf8StreamDecoder()
        assertFailsWith<MalformedInputException> {
            decoder.decode(dataBuffer(byteArrayOf(0xFF.toByte())))
        }
    }

    @Test
    fun testMixedAsciiAndMultiByteCharSplit() {
        val decoder = Utf8StreamDecoder()
        val bytes = "zażółć".toByteArray(Charsets.UTF_8)
        val split = "za".toByteArray(Charsets.UTF_8).size + 1 // "za" + first byte of 'ż' (C5 BC)
        val chunk1 = decoder.decode(dataBuffer(bytes.copyOfRange(0, split)))
        val chunk2 = decoder.decode(dataBuffer(bytes.copyOfRange(split, bytes.size)))
        assertEquals("zażółć", chunk1 + chunk2 + decoder.finish())
    }

    @Test
    fun testDecodeFourByteCharSplitAfterFirstByte() {
        val decoder = Utf8StreamDecoder()
        val emoji = "😀".toByteArray(Charsets.UTF_8) // F0 9F 98 80
        assertEquals("", decoder.decode(dataBuffer(emoji.copyOfRange(0, 1))))
        assertEquals("😀", decoder.decode(dataBuffer(emoji.copyOfRange(1, 4))))
        assertEquals("", decoder.finish())
    }

    @Test
    fun testDecodeFourByteCharSplitAfterThirdByte() {
        val decoder = Utf8StreamDecoder()
        val emoji = "😀".toByteArray(Charsets.UTF_8) // F0 9F 98 80
        assertEquals("", decoder.decode(dataBuffer(emoji.copyOfRange(0, 3))))
        assertEquals("😀", decoder.decode(dataBuffer(emoji.copyOfRange(3, 4))))
        assertEquals("", decoder.finish())
    }

    @Test
    fun testFinishWithIncompleteSequenceThrows() {
        val decoder = Utf8StreamDecoder()
        val pound = "£".toByteArray(Charsets.UTF_8) // C2 A3
        decoder.decode(dataBuffer(pound.copyOfRange(0, 1))) // only C2 — incomplete
        assertFailsWith<MalformedInputException> { decoder.finish() }
    }

    @Test
    fun testDecodeEmptyDataBuffer() {
        val decoder = Utf8StreamDecoder()
        assertEquals("", decoder.decode(dataBuffer(ByteArray(0))))
        assertEquals("", decoder.finish())
    }

    @Test
    fun testDecodeBufferLargerThanOutputWindow() {
        val decoder = Utf8StreamDecoder()
        val input = "a".repeat(9000)
        assertEquals(input, decoder.decode(dataBuffer(input.toByteArray())))
        assertEquals("", decoder.finish())
    }
}
