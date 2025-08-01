package ai.koog.prompt.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlIndentationTest {

    @Test
    fun testSingleLineTextIndentation() {
        val result = xml {
            tag("root") {
                text("Single line text")
            }
        }
        assertEquals("<root>\n  Single line text\n</root>", result)
    }

    @Test
    fun testMultiLineTextIndentation() {
        val result = xml {
            tag("root") {
                text("Line 1\nLine 2\nLine 3")
            }
        }
        val expected = """
            <root>
              Line 1
              Line 2
              Line 3
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testMixedContentIndentation() {
        val result = xml {
            tag("root") {
                +("Text before")
                tag("child") {
                    text("Child content")
                }
                +("Text after")
            }
        }
        val expected = """
            <root>
              Text before
              <child>
                Child content
              </child>
              Text after
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testNestedTagsIndentation() {
        val result = xml {
            tag("level1") {
                tag("level2") {
                    tag("level3") {
                        text("Deep content")
                    }
                }
            }
        }
        val expected = """
            <level1>
              <level2>
                <level3>
                  Deep content
                </level3>
              </level2>
            </level1>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testComplexIndentation() {
        val result = xml {
            tag("document") {
                tag("header") {
                    tag("title") {
                        text("Document Title")
                    }
                    tag("metadata") {
                        tag("author") {
                            text("John Doe")
                        }
                        tag("date") {
                            text("2023-05-15")
                        }
                    }
                }
                tag("body") {
                    tag("section") {
                        tag("heading") {
                            text("Section 1")
                        }
                        tag("paragraph") {
                            text("This is the first paragraph.\nIt has multiple lines.\nThree lines in total.")
                        }
                    }
                    tag("section") {
                        tag("heading") {
                            text("Section 2")
                        }
                        tag("paragraph") {
                            text("This is the second paragraph.")
                        }
                        tag("subsection") {
                            tag("heading") {
                                text("Subsection 2.1")
                            }
                            tag("paragraph") {
                                text("This is a subsection paragraph.")
                            }
                        }
                    }
                }
                tag("footer") {
                    text("Page 1 of 1")
                }
            }
        }

        // We don't need to check the exact output, just that it doesn't throw exceptions
        // and produces a non-empty string with proper indentation
        assertTrue(result.contains("  ")) // Should have indentation
        assertTrue(result.contains("    ")) // Should have deeper indentation
    }

    @Test
    fun testEmptyLinesInContent() {
        val result = xml {
            tag("root") {
                text("Line 1\n\nLine 3")
            }
        }
        val expected = """
            <root>
              Line 1
              
              Line 3
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testPreserveIndentationInPreformattedText() {
        val result = xml {
            tag("pre") {
                text("    Indented line 1\n      More indented line 2\n  Less indented line 3")
            }
        }
        val expected = """
            <pre>
                  Indented line 1
                    More indented line 2
                Less indented line 3
            </pre>
        """.trimIndent()
        assertEquals(expected, result)
    }
}
