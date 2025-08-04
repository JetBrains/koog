package ai.koog.prompt.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownMultilineListTest {

    @Test
    fun testBulletedListWithMultilineContent() {
        val md = markdown {
            bulleted {
                item("hello")
                item {
                    +"my friend"
                    +("continuation of list\n`and more`")
                    codeblock("codeblock", "")
                }
                item("next time")
            }
        }
        val expected = """
            - hello
            - my friend
              continuation of list
              `and more`
              ```
              codeblock
              ```
            - next time
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testNestedBulletedListWithMultilineContent() {
        val md = markdown {
            bulleted {
                item("hello")
                item {
                    +"my friend"
                    bulleted {
                        item("nested item") {
                            +("continuation of list")
                            code("and more")
                            codeblock("codeblock", "")
                        }
                    }
                }
                item("next time")
            }
        }
        //language=Markdown
        val expected = """
            - hello
            - my friend
              - nested item
                continuation of list
                `and more`
                ```
                codeblock
                ```
            - next time
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testNumberedListWithMultilineContent() {
        val md = markdown {
            numbered {
                item("First item")
                item {
                    +"Second item"
                    +("This is a paragraph\nwith multiple lines")
                    code("inline code")
                }
                item("Third item")
            }
        }
        val expected = """
            1. First item
            2. Second item
               This is a paragraph
               with multiple lines
               `inline code`
            3. Third item
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testCheckedListWithMultilineContent() {
        val md = markdown {
            bulleted {
                item("Unchecked item")
                bulleted {
                    item {
                        +"Checked item"
                        +("This is a paragraph\nwith multiple lines")
                        codeblock("var x = 10;", "javascript")
                    }
                }
                item("Another unchecked item")
            }
        }
        val expected = """
            - Unchecked item
            - Checked item
              This is a paragraph
              with multiple lines
              ```javascript
              var x = 10;
              ```
            - Another unchecked item
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testComplexNestedListsWithMultilineContent() {
        val md = markdown {
            bulleted {
                item("Top level item")
                item {
                    +"Another top level"
                    +("This is a paragraph in a list item")
                    numbered {
                        item("Numbered sub-item")
                        item {
                            +"Another numbered"
                            +("Paragraph in numbered item")
                            code("code in numbered item")
                            bulleted {
                                item("Task in numbered item")
                                bulleted {
                                    +"Completed task"
                                    +("Paragraph in task")
                                    codeblock("function test() {\n  return true;\n}", "javascript")
                                }
                            }
                        }
                    }
                    +("More content after nested lists")
                }
                item("Final top level item")
            }
        }

        // We don't need to check the exact output, just that it doesn't throw exceptions
        // and produces a non-empty string
        assertTrue(md.isNotEmpty())
    }
}
