package ai.koog.utils.lang

import kotlin.test.Test
import kotlin.test.assertEquals

class StringExtensionsTest {

    @Test
    fun `String masked should hide string contents`() {
        // Test null input
        assertEquals(null, null.masked())

        // Test empty string
        assertEquals(null, "".masked())

        // Test blank string
        assertEquals(null, "   ".masked())

        // Test single character - strict security returns consistent pattern
        assertEquals("***HIDDEN***", "I".masked())

        // Test two characters - strict security returns consistent pattern
        assertEquals("***HIDDEN***", "Hi".masked())

        // Test three characters - strict security returns consistent pattern
        assertEquals("***HIDDEN***", "Hey".masked())

        // Test longer string - strict security returns consistent pattern
        assertEquals("***HIDDEN***", "Hello".masked())

        // Test very long string - strict security returns consistent pattern
        assertEquals("***HIDDEN***", "cccccclulbkucigkbggivvitngjdbfuhkevedrdukvcr".masked())

        // Test custom mask character - should use custom char in pattern
        assertEquals("---HIDDEN---", "Hello".masked(maskChar = '-'))

        // Test string with whitespace - should still return consistent pattern
        assertEquals("***HIDDEN***", "  Hello  ".masked())

        // Test sensitive data - all return same pattern for maximum security
        assertEquals("***HIDDEN***", "password123".masked())
        assertEquals("***HIDDEN***", "api-key-secret".masked())
        assertEquals("***HIDDEN***", "x".masked())
    }
}
