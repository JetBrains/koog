package ai.koog.http.client.spring

import org.springframework.core.io.buffer.DataBuffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Stateful UTF-8 decoder for byte streams arriving in arbitrary chunks, buffering incomplete
 * multi-byte sequences between calls until the full character is available.
 *
 * Not thread-safe; intended for use within a single coroutine.
 */
internal class Utf8StreamDecoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    private var pendingBytes: ByteArray = ByteArray(0)
    private var finished = false

    /** Decodes the next chunk, returning all complete characters decoded so far. */
    fun decode(dataBuffer: DataBuffer): String {
        check(!finished) { "decode() called after finish()" }
        val bytes = ByteArray(dataBuffer.readableByteCount())
        dataBuffer.read(bytes)
        return decode(ByteBuffer.wrap(pendingBytes + bytes), endOfInput = false)
    }

    /** Flushes any remaining decoder state and returns the final characters; throws [java.nio.charset.MalformedInputException] if a partial sequence is pending. */
    fun finish(): String {
        finished = true
        return decode(ByteBuffer.wrap(pendingBytes), endOfInput = true) + flush()
    }

    private fun decode(input: ByteBuffer, endOfInput: Boolean): String {
        val decoded = StringBuilder()
        val output = CharBuffer.allocate(8 * 1024)

        while (true) {
            val result = decoder.decode(input, output, endOfInput)
            output.flip()
            decoded.append(output)
            output.clear()

            when {
                result.isOverflow -> continue
                result.isUnderflow -> {
                    pendingBytes = if (endOfInput) {
                        ByteArray(0)
                    } else {
                        ByteArray(input.remaining()).also { input.get(it) }
                    }
                    return decoded.toString()
                }
                else -> result.throwException()
            }
        }
    }

    private fun flush(): String {
        val decoded = StringBuilder()
        val output = CharBuffer.allocate(8 * 1024)

        while (true) {
            val result = decoder.flush(output)
            output.flip()
            decoded.append(output)
            output.clear()

            when {
                result.isUnderflow -> return decoded.toString()
                result.isOverflow -> continue
                else -> result.throwException()
            }
        }
    }
}
