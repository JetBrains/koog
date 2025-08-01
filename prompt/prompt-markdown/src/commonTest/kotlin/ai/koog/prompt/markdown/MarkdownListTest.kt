package ai.koog.prompt.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownListTest {

    @Test
    fun testBulletedList() {
        val md = markdown {
            bulleted {
                item("Item 1")
                item("Item 2")
                item("Item 3")
            }
        }
        val expected = """
            - Item 1
            - Item 2
            - Item 3
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testNumberedList() {
        val md = markdown {
            numbered {
                item("Item 1")
                item("Item 2")
                item("Item 3")
            }
        }
        val expected = """
            1. Item 1
            2. Item 2
            3. Item 3
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testNestedLists() {
        val md = markdown {
            bulleted {
                item("Item 1")
                item {
                    +"Item 2"
                    bulleted {
                        item("Nested item 1")
                        item("Nested item 2")
                    }
                }
                item("Item 3")
            }
        }
        val expected = """
            - Item 1
            - Item 2
              - Nested item 1
              - Nested item 2
            - Item 3
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testMixedNestedLists() {
        val md = markdown {
            bulleted {
                item("Bullet 1")
                item {
                    +"Bullet 2"
                    numbered {
                        item("Numbered 1")
                        item {
                            +"Numbered 2"
                            bulleted {
                                item("Task 1")
                                item("Task 2")
                            }
                        }
                    }
                }
                item("Bullet 3")
            }
        }
        //language=Markdown
        val expected = """
            - Bullet 1
            - Bullet 2
              1. Numbered 1
              2. Numbered 2
                 - Task 1
                 - Task 2
            - Bullet 3
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testComplexDocument() {
        val md = markdown {
            h1("Project Tasks")
            +"Here's a list of tasks for the project:"

            bulleted {
                item("Frontend")
                item {
                    +"Backend"
                    bulleted {
                        item("Set up database")
                        item("Create API endpoints")
                        item {
                            +"Implement authentication"
                            numbered {
                                item("Research OAuth providers")
                                item("Implement login flow")
                                item("Add session management")
                            }
                        }
                    }
                }
                item {
                    +"DevOps"
                    numbered {
                        item("Set up CI/CD")
                        item("Configure monitoring")
                    }
                }
            }

            h2("Timeline")
            +"The project will be completed in phases:"

            numbered {
                item("Planning")
                item {
                    +"Development"
                    bulleted {
                        item("Sprint 1")
                        item("Sprint 2")
                        item("Sprint 3")
                    }
                }
                item("Testing")
                item("Deployment")
            }
        }

        // We don't need to check the exact output, just that it doesn't throw exceptions
        // and produces a non-empty string
        assertTrue(md.isNotEmpty())
    }
}
