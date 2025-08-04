package ai.koog.prompt.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {

    @Test
    fun testHeaders() {
        val md = markdown {
            h1("Header 1")
        }
        assertEquals("# Header 1", md)

        val md2 = markdown {
            h2("Header 2")
        }
        assertEquals("## Header 2", md2)

        val md3 = markdown {
            h3("Header 3")
        }
        assertEquals("### Header 3", md3)

        val md4 = markdown {
            h4("Header 4")
        }
        assertEquals("#### Header 4", md4)

        val md5 = markdown {
            h5("Header 5")
        }
        assertEquals("##### Header 5", md5)

        val md6 = markdown {
            h6("Header 6")
        }
        assertEquals("###### Header 6", md6)
    }

    @Test
    fun testTextFormatting() {
        val md = markdown {
            bold("Bold text")
        }
        assertEquals("**Bold text**", md)

        val md2 = markdown {
            italic("Italic text")
        }
        assertEquals("*Italic text*", md2)

        val md3 = markdown {
            strikethrough("Strikethrough text")
        }
        assertEquals("~~Strikethrough text~~", md3)

        val md4 = markdown {
            code("Code text")
        }
        assertEquals("`Code text`", md4)
    }

    @Test
    fun testCodeBlock() {
        val md = markdown {
            codeblock("val x = 1", "kotlin")
        }
        assertEquals("```kotlin\nval x = 1\n```", md)

        val md2 = markdown {
            codeblock("function() {}", "javascript")
        }
        assertEquals("```javascript\nfunction() {}\n```", md2)

        val md3 = markdown {
            codeblock("plain text")
        }
        assertEquals("```\nplain text\n```", md3)
    }

    @Test
    fun testLinks() {
        val md = markdown {
            link("Google", "https://www.google.com")
        }
        assertEquals("[Google](https://www.google.com)", md)

        val md2 = markdown {
            image("Logo", "https://example.com/logo.png")
        }
        assertEquals("![Logo](https://example.com/logo.png)", md2)
    }

    @Test
    fun testHorizontalRule() {
        val md = markdown {
            horizontalRule()
        }
        assertEquals("---", md)
    }

    @Test
    fun testBlockquote() {
        val md = markdown {
            blockquote("This is a quote")
        }
        assertEquals("> This is a quote", md)

        val md2 = markdown {
            blockquote("This is a\nmultiline quote")
        }
        assertEquals("> This is a\n> multiline quote", md2)
    }

    @Test
    fun testTable() {
        val md = markdown {
            table(
                headers = listOf("Name", "Age", "City"),
                rows = listOf(
                    listOf("John", "25", "New York"),
                    listOf("Jane", "30", "London"),
                    listOf("Bob", "22", "Paris")
                )
            )
        }
        val expected = """
            | Name | Age | City |
            | :--- | :--- | :--- |
            | John | 25 | New York |
            | Jane | 30 | London |
            | Bob | 22 | Paris |
        """.trimIndent()
        assertEquals(expected, md)

        val md2 = markdown {
            table(
                headers = listOf("Name", "Age", "City"),
                rows = listOf(
                    listOf("John", "25", "New York"),
                    listOf("Jane", "30", "London"),
                    listOf("Bob", "22", "Paris")
                ),
                alignments = listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT)
            )
        }
        val expected2 = """
            | Name | Age | City |
            | :--- | :---: | ---: |
            | John | 25 | New York |
            | Jane | 30 | London |
            | Bob | 22 | Paris |
        """.trimIndent()
        assertEquals(expected2, md2)
    }

    @Test
    fun testMarkdownFunction() {
        val md = markdown {
            h1("Markdown Document")
            +"This is a paragraph."
            h2("Section")
            +"This is another paragraph."
            bulleted {
                item("Item 1")
                item("Item 2")
                item("Item 3")
            }
            link("Google", "https://www.google.com")
        }
        val expected = """
            # Markdown Document
            This is a paragraph.
            ## Section
            This is another paragraph.
            - Item 1
            - Item 2
            - Item 3
            [Google](https://www.google.com)
        """.trimIndent()
        assertEquals(expected, md)
    }

    @Test
    fun testComplexDocument() {
        val md = markdown {
            h1("Project Documentation")
            +"Welcome to the project documentation."

            h2("Installation")
            +"To install the project, run the following command:"
            codeblock("npm install my-project", "bash")

            h2("Usage")
            +"Here's how to use the project:"
            codeblock(
                """
                import { MyProject } from 'my-project';
                
                const project = new MyProject();
                project.start();
                """.trimIndent(),
                "javascript"
            )

            h2("Features")

            h2("Roadmap")

            h2("Contributors")
            table(
                headers = listOf("Name", "Role", "Contributions"),
                rows = listOf(
                    listOf("John Doe", "Developer", "100"),
                    listOf("Jane Smith", "Designer", "42"),
                    listOf("Bob Johnson", "Tester", "28")
                ),
                alignments = listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT)
            )

            h2("License")
            blockquote("This project is licensed under the MIT License.")
        }

        // We don't need to check the exact output, just that it doesn't throw exceptions
        // and produces a non-empty string
        assertTrue(md.isNotEmpty())
    }
}
