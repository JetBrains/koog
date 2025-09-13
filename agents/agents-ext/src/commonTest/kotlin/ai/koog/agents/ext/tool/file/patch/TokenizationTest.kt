package ai.koog.agents.ext.tool.file.patch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizationTest {
    @Test
    fun test_input_with_spaces_tokenizes_into_expected_tokens() {
        val input = "hello world test"
        val tokens = tokenize(input)

        assertEquals(5, tokens.size)
        assertEquals(Token("hello", 0..4), tokens[0])
        assertEquals(Token(" ", 5..5), tokens[1])
        assertEquals(Token("world", 6..10), tokens[2])
        assertEquals(Token(" ", 11..11), tokens[3])
        assertEquals(Token("test", 12..15), tokens[4])
    }

    @Test
    fun test_input_with_multiple_spaces_tokenizes_correctly() {
        val input = "test   multiple    spaces"
        val tokens = tokenize(input)

        assertEquals(5, tokens.size)
        assertEquals(Token("test", 0..3), tokens[0])
        assertEquals(Token("   ", 4..6), tokens[1])
        assertEquals(Token("multiple", 7..14), tokens[2])
        assertEquals(Token("    ", 15..18), tokens[3])
        assertEquals(Token("spaces", 19..24), tokens[4])
    }

    @Test
    fun test_single_word_input_produces_single_token() {
        val input = "singleword"
        val tokens = tokenize(input)

        assertEquals(1, tokens.size)
        assertEquals(Token("singleword", 0..9), tokens[0])
    }

    @Test
    fun test_empty_string_produces_no_tokens() {
        val input = ""
        val tokens = tokenize(input)

        assertTrue(tokens.isEmpty())
    }
}
