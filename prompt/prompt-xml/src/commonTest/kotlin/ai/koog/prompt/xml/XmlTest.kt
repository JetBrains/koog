package ai.koog.prompt.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlTest {

    @Test
    fun testBasicTag() {
        val result = xml {
            tag("root") {
                text("Hello, World!")
            }
        }
        assertEquals("<root>\n  Hello, World!\n</root>", result)
    }

    @Test
    fun testEmptyTag() {
        val result = xml {
            tag("empty")
        }
        assertEquals("<empty/>", result)
    }

    @Test
    fun testSelfClosingTag() {
        val result = xml {
            selfClosingTag("br")
        }
        assertEquals("<br/>", result)
    }

    @Test
    fun testTagWithAttributes() {
        val result = xml {
            tag("person", linkedMapOf("id" to "1", "name" to "John")) {
                text("This is John")
            }
        }
        assertEquals("<person id=\"1\" name=\"John\">\n  This is John\n</person>", result)
    }

    @Test
    fun testNestedTags() {
        val result = xml {
            tag("root") {
                tag("child1") {
                    text("Child 1 content")
                }
                tag("child2") {
                    text("Child 2 content")
                }
            }
        }
        val expected = """
            <root>
              <child1>
                Child 1 content
              </child1>
              <child2>
                Child 2 content
              </child2>
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testDeeplyNestedTags() {
        val result = xml {
            tag("level1") {
                tag("level2") {
                    tag("level3") {
                        tag("level4") {
                            text("Deep content")
                        }
                    }
                }
            }
        }
        val expected = """
            <level1>
              <level2>
                <level3>
                  <level4>
                    Deep content
                  </level4>
                </level3>
              </level2>
            </level1>
        """.trimIndent()

        assertEquals(expected, result)
    }

    @Test
    fun testMixedContent() {
        val result = xml {
            tag("mixed") {
                +("Text before")
                tag("child") {
                    text("Child content")
                }
                +("Text after")
            }
        }
        val expected = """
            <mixed>
              Text before
              <child>
                Child content
              </child>
              Text after
            </mixed>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testXmlDeclaration() {
        val result = xml {
            xmlDeclaration()
            tag("root") {
                text("Content")
            }
        }
        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
              Content
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testXmlDeclarationWithCustomValues() {
        val result = xml {
            xmlDeclaration(version = "1.1", encoding = "ISO-8859-1", standalone = true)
            tag("root") {
                text("Content")
            }
        }
        val expected = """
            <?xml version="1.1" encoding="ISO-8859-1" standalone="yes"?>
            <root>
              Content
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testCdata() {
        val result = xml {
            tag("data") {
                cdata("<script>alert('Hello');</script>")
            }
        }
        val expected = """
            <data>
              <![CDATA[<script>alert('Hello');</script>]]>
            </data>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testComment() {
        val result = xml {
            comment("This is a comment")
            tag("root") {
                text("Content")
            }
        }
        val expected = """
            <!-- This is a comment -->
            <root>
              Content
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testProcessingInstruction() {
        val result = xml {
            processingInstruction("xml-stylesheet", "type=\"text/css\" href=\"style.css\"")
            tag("root") {
                text("Content")
            }
        }
        val expected = """
            <?xml-stylesheet type="text/css" href="style.css"?>
            <root>
              Content
            </root>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testDoctype() {
        val result = xml {
            doctype("html")
            tag("html") {
                tag("body") {
                    text("Content")
                }
            }
        }
        val expected = """
            <!DOCTYPE html>
            <html>
              <body>
                Content
              </body>
            </html>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testDoctypeWithPublicId() {
        val result = xml {
            doctype(
                "html",
                "-//W3C//DTD XHTML 1.0 Strict//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"
            )
            tag("html") {
                tag("body") {
                    text("Content")
                }
            }
        }
        val expected = """
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html>
              <body>
                Content
              </body>
            </html>
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun testComplexXml() {
        val result = xml {
            xmlDeclaration()
            doctype(
                "html",
                "-//W3C//DTD XHTML 1.0 Transitional//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"
            )
            tag("html", linkedMapOf("xmlns" to "http://www.w3.org/1999/xhtml")) {
                tag("head") {
                    tag("title") {
                        text("XML Test")
                    }
                    selfClosingTag("meta", linkedMapOf("charset" to "UTF-8"))
                    processingInstruction("xml-stylesheet", "type=\"text/css\" href=\"style.css\"")
                }
                tag("body") {
                    tag("h1") {
                        text("XML Builder Test")
                    }
                    tag("div", linkedMapOf("class" to "content")) {
                        tag("p") {
                            text("This is a paragraph with ")
                            tag("strong") {
                                text("bold text")
                            }
                            text(" and ")
                            tag("em") {
                                text("emphasized text")
                            }
                            text(".")
                        }
                        tag("ul") {
                            tag("li") {
                                text("Item 1")
                            }
                            tag("li") {
                                text("Item 2")
                            }
                            tag("li") {
                                text("Item 3")
                            }
                        }
                    }
                    comment("This is a comment at the end of the document")
                }
            }
        }

        // We don't need to check the exact output, just that it doesn't throw exceptions
        // and produces a non-empty string
        assertTrue(result.isNotEmpty())
    }
}
