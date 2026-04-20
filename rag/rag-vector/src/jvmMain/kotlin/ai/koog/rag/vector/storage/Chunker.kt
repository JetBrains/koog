package ai.koog.rag.vector.storage

/**
 * Splits a document's text content into chunks suitable for embedding and retrieval.
 *
 * Implementations MUST:
 * - Be deterministic: the same input must always produce the same chunks.
 * - Preserve content: the concatenation of chunks (ignoring inter-chunk overlap) must cover the
 *   input without losing characters.
 * - Be Unicode-safe: never split in the middle of a Unicode codepoint or surrogate pair.
 *
 * Implementations SHOULD prefer breaking at semantic boundaries (paragraph → sentence → word →
 * character) so that embeddings receive coherent spans.
 */
public fun interface Chunker {
    /**
     * Splits [text] into one or more chunks. For empty text, implementations MUST return
     * `listOf("")` so that a single (empty) chunk is emitted and the document remains addressable.
     */
    public fun chunk(text: String): List<String>
}

/**
 * Default production-grade chunker used by [PGVectorStorage] when none is provided.
 *
 * Splits text recursively at a hierarchy of separators (paragraph break → line break → sentence
 * terminator → whitespace → codepoint), falling back to smaller granularities only when a span
 * exceeds [chunkSizeChars]. Produces overlapping windows of approximately [chunkSizeChars]
 * characters with [overlapChars] overlap, never breaking inside a Unicode surrogate pair.
 *
 * Sizes are measured in Java `String` length (UTF-16 code units) because that is what pgvector's
 * `content TEXT` column counts and what matches [PGVectorStorage]'s character-addressable layer.
 * Pair this with a tokenizer-aware chunker (e.g., from your embedder SDK) if you need strict
 * token budgets.
 *
 * @property chunkSizeChars target chunk size in UTF-16 code units (default ~2000, ≈500 tokens).
 * @property overlapChars overlap between consecutive chunks (default 300).
 * @property minChunkChars minimum size for a trailing chunk to be kept on its own. Smaller tails
 *   are merged into the previous chunk.
 * @property separators ordered list of separator patterns tried from coarsest to finest. The
 *   defaults cover English and most whitespace-delimited languages.
 */
public class RecursiveCharacterChunker(
    private val chunkSizeChars: Int = 2000,
    private val overlapChars: Int = 300,
    private val minChunkChars: Int = 200,
    private val separators: List<String> = DEFAULT_SEPARATORS,
) : Chunker {

    init {
        require(chunkSizeChars > 0) { "chunkSizeChars must be > 0" }
        require(overlapChars in 0 until chunkSizeChars) { "overlapChars must be in [0, chunkSizeChars)" }
        require(minChunkChars in 1..chunkSizeChars) { "minChunkChars must be in [1, chunkSizeChars]" }
        require(separators.isNotEmpty()) { "separators must not be empty" }
    }

    override fun chunk(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        if (text.length <= chunkSizeChars) return listOf(text)

        // Step 1: split the text into atomic pieces at the finest separator that fits.
        val pieces = splitRecursively(text, separators)

        // Step 2: greedily pack pieces into windows ~chunkSizeChars with overlap.
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (piece in pieces) {
            if (buffer.isEmpty()) {
                buffer.append(piece)
                continue
            }
            if (buffer.length + piece.length <= chunkSizeChars) {
                buffer.append(piece)
            } else {
                chunks.add(buffer.toString())
                // Build overlap from the tail of the previous chunk, respecting codepoint boundaries.
                val tail = safeTail(buffer, overlapChars)
                buffer.setLength(0)
                buffer.append(tail)
                buffer.append(piece)
            }
        }
        if (buffer.isNotEmpty()) {
            val tail = buffer.toString()
            if (chunks.isNotEmpty() && tail.length < minChunkChars) {
                // Merge the small trailing chunk into the previous one instead of emitting a stub.
                chunks[chunks.lastIndex] =
                    chunks.last() + tail.removePrefix(safeTail(StringBuilder(chunks.last()), overlapChars))
            } else {
                chunks.add(tail)
            }
        }
        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    /**
     * Recursively splits [text] into atomic pieces (each ≤ [chunkSizeChars] when possible),
     * trying each separator in order and falling through to codepoint-safe hard splits when no
     * separator helps. Pieces retain their trailing separator characters so that concatenation
     * is lossless.
     */
    private fun splitRecursively(text: String, separators: List<String>): List<String> {
        if (text.length <= chunkSizeChars) return listOf(text)
        for ((index, sep) in separators.withIndex()) {
            if (sep.isEmpty()) continue
            if (!text.contains(sep)) continue
            val parts = splitKeepingSeparator(text, sep)
            val remaining = separators.drop(index + 1)
            val result = mutableListOf<String>()
            for (part in parts) {
                if (part.length <= chunkSizeChars) {
                    result.add(part)
                } else {
                    result.addAll(splitRecursively(part, remaining))
                }
            }
            return result
        }
        // No separator worked: hard-split at codepoint boundaries.
        return hardSplitByCodepoints(text, chunkSizeChars)
    }

    private fun splitKeepingSeparator(text: String, sep: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        while (true) {
            val idx = text.indexOf(sep, start)
            if (idx < 0) {
                if (start < text.length) out.add(text.substring(start))
                break
            }
            val end = idx + sep.length
            out.add(text.substring(start, end))
            start = end
        }
        return out
    }

    private fun hardSplitByCodepoints(text: String, size: Int): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + size).coerceAtMost(text.length)
            // Don't split a surrogate pair.
            if (end < text.length && text[end - 1].isHighSurrogate()) end -= 1
            if (end <= start) end = (start + size).coerceAtMost(text.length) // safety
            out.add(text.substring(start, end))
            start = end
        }
        return out
    }

    private fun safeTail(buffer: StringBuilder, overlap: Int): String {
        if (overlap <= 0 || buffer.isEmpty()) return ""
        var from = (buffer.length - overlap).coerceAtLeast(0)
        if (from > 0 && buffer[from].isLowSurrogate()) from -= 1
        return buffer.substring(from)
    }

    public companion object {
        /**
         * Default separator hierarchy: paragraph break → line break → sentence terminator
         * (English / CJK) → clause boundary → whitespace → empty (codepoint-level fallback).
         */
        public val DEFAULT_SEPARATORS: List<String> = listOf(
            "\n\n",
            "\n",
            ". ", "? ", "! ",
            "。", "？", "！", // CJK sentence terminators
            "; ", ", ",
            " ",
            "",
        )
    }
}
