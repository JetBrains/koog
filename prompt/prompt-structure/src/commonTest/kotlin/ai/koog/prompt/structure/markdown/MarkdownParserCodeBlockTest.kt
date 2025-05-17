package ai.koog.prompt.structure.markdown

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserCodeBlockTest {

    @Test
    fun testOnCodeBlockWithSimpleCodeBlock() = runTest {
        val codeBlocks = mutableListOf<String>()

        val parser = markdownParser {
            onCodeBlock { content ->
                codeBlocks.add(content)
            }
        }

        val markdown = """
            # Sample Document
            
            Here is a code block:
            
            ```kotlin
            fun main() {
                println("Hello, World!")
            }
            ```
            
            End of document.
        """.trimIndent()

        parser(markdown)

        assertEquals(1, codeBlocks.size, "Should capture one code block")
        assertTrue(codeBlocks[0].contains("fun main()"), "Should capture the code content")
        assertTrue(codeBlocks[0].contains("println(\"Hello, World!\")"), "Should capture the code content")
    }

    @Test
    fun testMultipleCodeBlocksParsing() = runTest {
        val codeBlocks = mutableListOf<String>()

        val parser = markdownParser {
            onCodeBlock { content ->
                codeBlocks.add(content)
            }
        }

        val markdown = """
            # Multiple Code Blocks
            
            First code block:
            
            ```java
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello from Java!");
                }
            }
            ```
            
            Second code block:
            
            ```python
            def main():
                print("Hello from Python!")
                
            if __name__ == "__main__":
                main()
            ```
            
            End of document.
        """.trimIndent()

        parser(markdown)

        assertEquals(2, codeBlocks.size, "Should capture two code blocks")
        assertTrue(codeBlocks[0].contains("public class Main"), "Should capture Java code content")
        assertTrue(codeBlocks[1].contains("def main():"), "Should capture Python code content")
    }

    @Test
    fun testCodeBlockWithoutLanguage() = runTest {
        val codeBlocks = mutableListOf<String>()

        val parser = markdownParser {
            onCodeBlock { content ->
                codeBlocks.add(content)
            }
        }

        val markdown = """
            # Code Block Without Language
            
            ```
            This is a code block without a language specified.
            It should still be captured.
            ```
        """.trimIndent()

        parser(markdown)

        assertEquals(1, codeBlocks.size, "Should capture one code block")
        assertTrue(codeBlocks[0].contains("This is a code block without a language specified."),
                  "Should capture the code content")
    }

    @Test
    fun testCodeBlockParsing() = runTest {
        val codeBlocks = mutableListOf<String>()

        val parser = markdownStreamingParser {
            onCodeBlock { content ->
                codeBlocks.add(content)
            }
        }

        val markdownFlow = flow {
            emit("# Streaming Code Block\n\n")
            emit("```kotl")
            emit("in\n")
            emit("fun main() {\n")
            emit("    println(\"Hello, World!\")\n")
            emit("}\n")
            emit("```\n\n")
            emit("End of document.")
        }

        parser.parseStream(markdownFlow)

        assertEquals(1, codeBlocks.size, "Should capture one code block")
        assertTrue(codeBlocks[0].contains("fun main()"), "Should capture the code content")
        assertTrue(codeBlocks[0].contains("println(\"Hello, World!\")"), "Should capture the code content")
    }

    @Test
    fun testUnclosedCodeBlockParsing() = runTest {
        val codeBlocks = mutableListOf<String>()

        val parser = markdownParser {
            onCodeBlock { content ->
                codeBlocks.add(content)
            }
        }

        val markdown = """
            # Unclosed Code Block
            
            ```kotlin
            fun main() {
                println("This code block is not closed")
            }
        """.trimIndent()

        parser(markdown)

        assertEquals(1, codeBlocks.size, "Should capture the unclosed code block")
        assertTrue(codeBlocks[0].contains("fun main()"), "Should capture the code content")
        assertTrue(codeBlocks[0].contains("println(\"This code block is not closed\")"),
                  "Should capture the code content")
    }
}