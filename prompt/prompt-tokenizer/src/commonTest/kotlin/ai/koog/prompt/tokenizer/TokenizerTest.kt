package ai.koog.prompt.tokenizer

import ai.koog.prompt.tokenizer.tiktoken.TiktokenEncoder
import ai.koog.prompt.tokenizer.tiktoken.toByteArrayKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the Tokenizer implementations.
 */
class TokenizerTest {

    /**
     * Tests that NoTokenizer always returns 0 regardless of input.
     */
    @Test
    fun testNoTokenizer() {
        val tokenizer = NoTokenizer()

        // Test with empty string
        assertEquals(0, tokenizer.countTokens(""))

        // Test with simple text
        assertEquals(0, tokenizer.countTokens("Hello, world!"))

        // Test with longer text
        assertEquals(
            0,
            tokenizer.countTokens("This is a longer text with multiple sentences. It should still return 0 tokens.")
        )
    }

    /**
     * Tests that RegexBasedTokenizer correctly counts tokens in various text inputs.
     */
    @Test
    fun testRegexBasedTokenizer() {
        val tokenizer = SimpleRegexBasedTokenizer()

        // Test with empty string
        assertEquals(0, tokenizer.countTokens(""))

        // Test with simple text
        val simpleText = "Hello, world!"
        // Expected: "Hello" and "world" = 2 tokens, with 1.1 overhead = 2.2, rounded to 2
        assertEquals(2, tokenizer.countTokens(simpleText))

        // Test with more complex text
        val complexText = "This is a test. It has multiple sentences, punctuation, and numbers like 123."
        // Count tokens manually: "This", "is", "a", "test", "It", "has", "multiple", "sentences",
        // "punctuation", "and", "numbers", "like", "123" = 13 tokens
        // With 1.1 overhead = 14.3, rounded to 14
        assertEquals(14, tokenizer.countTokens(complexText))

        // Test with text containing special characters
        val specialCharsText = "Text with [brackets], {braces}, and (parentheses)."
        // Count tokens: "Text", "with", "brackets", "braces", "and", "parentheses" = 6 tokens
        // With 1.1 overhead = 6.6, rounded to 6 (since we're using Long.toLong() which truncates)
        assertEquals(6, tokenizer.countTokens(specialCharsText))
    }

    /**
     * Tests that RegexBasedTokenizer handles edge cases correctly.
     */
    @Test
    fun testRegexBasedTokenizerEdgeCases() {
        val tokenizer = SimpleRegexBasedTokenizer()

        // Test with only whitespace
        assertEquals(0, tokenizer.countTokens("   "))

        // Test with only punctuation
        assertEquals(0, tokenizer.countTokens(".,;:!?"))

        // Test with repeated whitespace and punctuation
        assertEquals(0, tokenizer.countTokens("  ,  .  ;  :  !  ?  "))

        // Test with a very long text
        val longText = "a ".repeat(1000)
        // This should be 1000 "a" tokens, with 1.1 overhead = 1100
        assertEquals(1100, tokenizer.countTokens(longText))
    }

    /**
     * Tests that TiktokenEncoder correctly counts tokens using its vocabulary and pattern.
     */
    @Test
    fun testTiktokenEncoder() {
        // Create a test vocabulary with both word-level and character-level tokens
        val vocabulary = mapOf(
            "Hello".toByteArrayKey() to 1,
            "World".toByteArrayKey() to 2,
            "!".toByteArrayKey() to 3,
            "This".toByteArrayKey() to 4,
            "is".toByteArrayKey() to 5,
            "a".toByteArrayKey() to 6,
            "test".toByteArrayKey() to 7,
            ".".toByteArrayKey() to 8,
            // Add single-character tokens for testing unknown token handling
            "U".toByteArrayKey() to 9,
            "n".toByteArrayKey() to 10,
            "k".toByteArrayKey() to 11,
            "o".toByteArrayKey() to 12,
            "w".toByteArrayKey() to 13
        )

        // Create a pattern that matches words and punctuation
        val pattern = Regex("\\w+|[^\\w\\s]")

        // Define an unknown token ID
        val unkTokenId = 0

        // Create the TiktokenEncoder instance
        val tokenizer = TiktokenEncoder(vocabulary, pattern, unkTokenId)

        // Test with empty string
        assertEquals(0, tokenizer.countTokens(""))

        // Test with known tokens
        assertEquals(3, tokenizer.countTokens("Hello World!"))

        // Test with known and unknown tokens
        assertEquals(5, tokenizer.countTokens("This is a test."))

        // Test with partially known tokens (some characters in the words are in the vocabulary)
        // "Unknown" has "U", "n", "k", "o", "w", "n" in vocabulary
        // Each character is treated as a separate token, with unknown characters using unkTokenId
        assertEquals(7, tokenizer.countTokens("Unknown"))

        // Test with a mix of known words and characters
        // The tokenizer counts "Hello" and "test" as 2 tokens total
        assertEquals(2, tokenizer.countTokens("Hello test"))
    }
}
