package ai.koog.prompt.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextContentBuilderTest {

    @Test
    fun testLineNumberingWithDefaultStart() {
        val result = text {
            numbered {
                text("First line")
                newline()
                text("Second line")
                newline()
                text("Third line")
            }
        }

        val expected = "1: First line\n2: Second line\n3: Third line"
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingWithCustomStart() {
        val result = text {
            numbered(startLineNumber = 10) {
                text("First line")
                newline()
                text("Second line")
                newline()
                text("Third line")
            }
        }

        val expected = "10: First line\n11: Second line\n12: Third line"
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingWithEmptyContent() {
        val result = text {
            numbered {
                // Empty content
            }
        }

        val expected = "1: "
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingWithSingleLine() {
        val result = text {
            numbered {
                text("Single line")
            }
        }

        val expected = "1: Single line"
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingWithMultipleEmptyLines() {
        val result = text {
            numbered {
                newline()
                newline()
                text("Line after empty lines")
            }
        }

        val expected = "1: \n2: \n3: Line after empty lines"
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingWithTrailingNewline() {
        val result = text {
            numbered {
                text("Line with trailing newline")
                newline()
            }
        }

        val expected = "1: Line with trailing newline\n2: "
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingWithLargeStartNumber() {
        val result = text {
            numbered(startLineNumber = 1000) {
                text("Line 1")
                newline()
                text("Line 2")
            }
        }

        val expected = "1000: Line 1\n1001: Line 2"
        assertEquals(expected, result)
    }

    @Test
    fun testLineNumberingAlignment() {
        val result = text {
            numbered(startLineNumber = 9) {
                text("Line 1")
                newline()
                text("Line 2")
                newline()
                text("Line 3")
            }
        }

        // Line numbers should be aligned (single digit)
        val expected = " 9: Line 1\n10: Line 2\n11: Line 3"
        assertEquals(expected, result)
    }
}
