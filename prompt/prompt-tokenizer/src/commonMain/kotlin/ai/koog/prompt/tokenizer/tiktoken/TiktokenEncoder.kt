package ai.koog.prompt.tokenizer.tiktoken

import ai.koog.prompt.tokenizer.Tokenizer

/**
 * A tokenizer implementation that uses a token encoding vocabulary and a regex pattern to tokenize
 * text into a series of token IDs. The tokenization process utilizes byte pair encoding (BPE) for
 * segments of text not directly found in the vocabulary.
 *
 * This implementation is based on the reference Rust implementation from
 * https://github.com/openai/tiktoken/blob/main/src/lib.rs.
 *
 * @constructor Initializes the TiktokenEncoder with the specified vocabulary, regex pattern for
 * matching tokens, and a token ID for unknown tokens.
 * @param vocabulary A mapping of ByteArrayKey to token ID that represents the token encoding vocabulary.
 * @param pattern A regular expression used to match text patterns for initial token matching.
 * @param unkTokenId The token ID used for unknown tokens not present in the vocabulary.
 */
public open class TiktokenEncoder(
    private val vocabulary: Map<ByteArrayKey, Int>,
    private val pattern: Regex,
    private val unkTokenId: Int,
) : Tokenizer {

    /**
     * Counts the number of tokens in the given text.
     *
     * The method utilizes the `encodeAsIds` function to tokenize the input string
     * and determines the number of resulting tokens.
     *
     * @param text The input text to be tokenized and counted.
     * @return The total number of tokens in the input text.
     */
    override fun countTokens(text: String): Int = encodeAsIds(text).count()

    /**
     * Encodes the provided text into a list of integer token IDs using a predefined vocabulary
     * and byte pair encoding for unknown tokens.
     *
     * @param text The input string to be encoded into token IDs.
     * @return A list of integer token IDs representing the encoded text.
     */
    protected fun encodeAsIds(text: String): List<Int> {
        val matches = pattern.findAll(text)
        return matches.map { match ->
            val piece = match.value
            vocabulary[piece.toByteArrayKey()]?.let { listOf(it) } ?: bytePairEncode(piece.toByteArrayKey())
        }.flatten().toList()
    }

    /**
     * Encodes a byte sequence into a list of token IDs using a byte-pair encoding strategy.
     *
     * @param bytes The input byte sequence encapsulated in a `ByteArrayKey` that needs to be encoded.
     * @return A list of token IDs representing the encoded form of the input byte sequence.
     */
    private fun bytePairEncode(bytes: ByteArrayKey): List<Int> {
        if (bytes.size == 1) {
            return listOf(vocabulary[bytes] ?: unkTokenId)
        }
        return bytePairMerge(bytes)
    }

    /**
     * Merges adjacent pairs of bytes from the given `ByteArrayKey` based on a ranking mechanism
     * to create an optimized list of integer identifiers.
     *
     * This method is a step in a byte-pair encoding process, iteratively combining adjacent
     * parts of the byte array while minimizing a ranking score.
     *
     * @param bytes The `ByteArrayKey` instance representing the input sequence of bytes to be merged.
     * @return A list of integers representing the merged pairs of bytes encoded as identifiers.
     */
    private fun bytePairMerge(bytes: ByteArrayKey): List<Int> {
        val parts = MutableList(bytes.size + 1) { it toM Int.MAX_VALUE }

        for (i in 0 until parts.size - 2) {
            val rank = getRank(bytes, parts, i, 0) ?: continue
            parts[i].second = rank
        }

        while (parts.size > 1) {
            val minRank = parts.asSequence().take(parts.size - 1).minBy { it.second }
            if (minRank.second != Int.MAX_VALUE) {
                val i = parts.indexOf(minRank)
                parts[i].second = getRank(bytes, parts, i, 1) ?: Int.MAX_VALUE
                if (i > 0) {
                    parts[i - 1].second = getRank(bytes, parts, i - 1, 1) ?: Int.MAX_VALUE
                }
                parts.removeAt(i + 1)
            } else {
                break
            }
        }
        return parts.mapIndexedNotNull { i, part ->
            if (i >= parts.size - 1) {
                null
            } else {
                vocabulary[bytes.range(part.first, parts[i + 1].first)]
            }
        }
    }

    /**
     * Retrieves a rank based on the specified range within parts, derived from a byte array key.
     *
     * @param bytes A `ByteArrayKey` instance representing the underlying byte array for which the rank is being calculated.
     * @param parts A list of `MutablePair` objects, where each pair represents a range and a rank.
     * @param start The starting index in the `parts` list to use for the range calculation.
     * @param skip The number of elements to skip from the starting index when calculating the rank.
     * @return The rank as an `Int` if the calculation is successful, or `null` if the range extends out of bounds.
     */
    private fun getRank(bytes: ByteArrayKey, parts: List<MutablePair<Int, Int>>, start: Int, skip: Int): Int? {
        return if (start + skip + 2 < parts.size) {
            vocabulary[bytes.range(parts[start].first, parts[start + skip + 2].first)]
        } else {
            null
        }
    }
}
