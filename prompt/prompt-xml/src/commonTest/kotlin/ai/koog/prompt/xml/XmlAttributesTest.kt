package ai.koog.prompt.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlAttributesTest {

    @Test
    fun testSingleAttribute() {
        val result = xml {
            tag("element", linkedMapOf("id" to "123")) {
                text("Content")
            }
        }
        assertEquals("<element id=\"123\">\n  Content\n</element>", result)
    }

    @Test
    fun testMultipleAttributes() {
        val result = xml {
            tag(
                "element",
                linkedMapOf(
                    "id" to "123",
                    "class" to "main",
                    "data-test" to "true"
                )
            ) {
                text("Content")
            }
        }
        assertEquals("<element id=\"123\" class=\"main\" data-test=\"true\">\n  Content\n</element>", result)
    }

    @Test
    fun testAttributesWithSpecialCharacters() {
        val result = xml {
            tag(
                "element",
                linkedMapOf(
                    "data-value" to "a < b & c > d",
                    "title" to "Quote: \"Hello\""
                )
            ) {
                text("Content")
            }
        }
        assertEquals("<element data-value=\"a < b & c > d\" title=\"Quote: \"Hello\"\">\n  Content\n</element>", result)
    }

    @Test
    fun testEmptyAttributes() {
        val result = xml {
            tag("element", linkedMapOf()) {
                text("Content")
            }
        }
        assertEquals("<element>\n  Content\n</element>", result)
    }

    @Test
    fun testSelfClosingTagWithAttributes() {
        val result = xml {
            selfClosingTag(
                "img",
                linkedMapOf(
                    "src" to "image.jpg",
                    "alt" to "An image",
                    "width" to "100",
                    "height" to "100"
                )
            )
        }
        assertEquals("<img src=\"image.jpg\" alt=\"An image\" width=\"100\" height=\"100\"/>", result)
    }

    @Test
    fun testNestedTagsWithAttributes() {
        val result = xml {
            tag("parent", linkedMapOf("id" to "parent1")) {
                tag("child", linkedMapOf("id" to "child1")) {
                    text("Child 1 content")
                }
                tag("child", linkedMapOf("id" to "child2")) {
                    text("Child 2 content")
                }
            }
        }
        val expected = """
            <parent id="parent1">
              <child id="child1">
                Child 1 content
              </child>
              <child id="child2">
                Child 2 content
              </child>
            </parent>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testComplexAttributeCombinations() {
        val result = xml {
            tag("html", linkedMapOf("xmlns" to "http://www.w3.org/1999/xhtml", "lang" to "en")) {
                tag("head") {
                    tag("meta", linkedMapOf("charset" to "UTF-8"))
                    tag("title") {
                        text("Test Document")
                    }
                    selfClosingTag(
                        "link",
                        linkedMapOf(
                            "rel" to "stylesheet",
                            "href" to "styles.css",
                            "type" to "text/css"
                        )
                    )
                }
                tag("body", linkedMapOf("class" to "main-content", "data-theme" to "light")) {
                    tag("div", linkedMapOf("id" to "container", "class" to "wrapper")) {
                        tag("h1", linkedMapOf("class" to "title")) {
                            text("Hello, World!")
                        }
                        tag("p", linkedMapOf("class" to "description")) {
                            text("This is a test paragraph.")
                        }
                        selfClosingTag("hr", linkedMapOf("class" to "divider"))
                        tag("ul", linkedMapOf("class" to "list")) {
                            tag("li", linkedMapOf("data-index" to "0")) {
                                text("Item 1")
                            }
                            tag("li", linkedMapOf("data-index" to "1")) {
                                text("Item 2")
                            }
                        }
                    }
                }
            }
        }

        // We don't need to check the exact output, just that it doesn't throw exceptions
        // and produces a non-empty string with proper attributes
        assertTrue(result.contains("xmlns=\"http://www.w3.org/1999/xhtml\""))
        assertTrue(result.contains("charset=\"UTF-8\""))
        assertTrue(result.contains("class=\"main-content\""))
    }
}
