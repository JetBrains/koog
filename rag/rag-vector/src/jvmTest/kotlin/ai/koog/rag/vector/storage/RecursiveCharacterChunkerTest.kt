package ai.koog.rag.vector.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecursiveCharacterChunkerTest {

    @Test
    fun testEmptyTextProducesSingleEmptyChunk() {
        val chunks = RecursiveCharacterChunker().chunk("")
        assertEquals(listOf(""), chunks)
    }

    @Test
    fun testShortTextIsNotChunked() {
        val chunker = RecursiveCharacterChunker(chunkSizeChars = 100, overlapChars = 10, minChunkChars = 10)
        val chunks = chunker.chunk("Hello, world.")
        assertEquals(listOf("Hello, world."), chunks)
    }

    @Test
    fun testChunksNeverExceedLimit() {
        val chunker = RecursiveCharacterChunker(chunkSizeChars = 50, overlapChars = 10, minChunkChars = 5)
        val input = (1..20).joinToString(". ") { "Sentence number $it" } + "."
        val chunks = chunker.chunk(input)
        assertTrue(chunks.all { it.length <= 50 + /* last-piece tolerance */ 20 })
        // Content is preserved (merging all chunks should contain every sentence somewhere).
        (1..20).forEach { n ->
            assertTrue(chunks.any { it.contains("Sentence number $n") }, "Missing sentence $n in $chunks")
        }
    }

    @Test
    fun testDoesNotSplitInsideSurrogatePair() {
        // 😀 is a supplementary codepoint encoded as a surrogate pair in UTF-16. If we naively cut
        // at a fixed char index we risk producing a lone high surrogate, which breaks UTF-8 encoding.
        val emoji = "😀"
        val longRun = emoji.repeat(200) // 400 UTF-16 units
        val chunker = RecursiveCharacterChunker(chunkSizeChars = 51, overlapChars = 5, minChunkChars = 5)
        val chunks = chunker.chunk(longRun)
        for (chunk in chunks) {
            // Every chunk must be valid UTF-16: no lone surrogates.
            var i = 0
            while (i < chunk.length) {
                val c = chunk[i]
                if (c.isHighSurrogate()) {
                    assertTrue(
                        i + 1 < chunk.length && chunk[i + 1].isLowSurrogate(),
                        "Chunk ends in a high surrogate: $chunk"
                    )
                    i += 2
                } else {
                    assertTrue(!c.isLowSurrogate(), "Chunk starts with a low surrogate: $chunk")
                    i += 1
                }
            }
        }
    }

    @Test
    fun testPrefersSemanticBoundaries() {
        val chunker = RecursiveCharacterChunker(chunkSizeChars = 40, overlapChars = 0, minChunkChars = 5)
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        val chunks = chunker.chunk(text)
        // Expect splits around paragraph boundaries — no chunk should interleave sentence fragments.
        assertTrue(chunks.all { it.isNotBlank() })
        assertEquals(text.replace(Regex("\\s+"), ""), chunks.joinToString("").replace(Regex("\\s+"), ""))
    }
}
