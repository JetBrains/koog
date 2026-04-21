package ai.koog.integration.tests.utils

import ai.koog.prompt.message.Message
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path

object MediaTestUtils {
    fun getImageFileForScenario(scenario: MediaTestScenarios.ImageTestScenario, testResourcesDir: Path): Path {
        return when (scenario) {
            MediaTestScenarios.ImageTestScenario.BASIC_PNG -> {
                testResourcesDir.resolve("test.png")
            }

            MediaTestScenarios.ImageTestScenario.BASIC_JPG -> {
                testResourcesDir.resolve("basic.jpeg")
            }

            MediaTestScenarios.ImageTestScenario.EMPTY_IMAGE -> {
                testResourcesDir.resolve("empty.png")
            }

            MediaTestScenarios.ImageTestScenario.CORRUPTED_IMAGE -> {
                testResourcesDir.resolve("corrupted.png")
            }
        }
    }

    fun createTextFileForScenario(scenario: MediaTestScenarios.TextTestScenario, testResourcesDir: Path): Path {
        val textContent = when (scenario) {
            MediaTestScenarios.TextTestScenario.BASIC_TEXT ->
                "This is a simple text for testing basic text processing capabilities."

            MediaTestScenarios.TextTestScenario.EMPTY_TEXT ->
                ""

            MediaTestScenarios.TextTestScenario.UTF8_ENCODING ->
                "This text contains UTF-8 characters: é, ü, ñ, ç, ß, 你好, こんにちは, Привет"

            MediaTestScenarios.TextTestScenario.ASCII_ENCODING ->
                "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n" +
                    "   /\\_/\\  \n" +
                    "  ( o.o ) \n" +
                    "   > ^ <\n" +
                    "(∑, ∞, ∂)\n"

            MediaTestScenarios.TextTestScenario.UNICODE_TEXT -> """
            Unicode Text Examples:

            • Chinese: 你好，世界！(Hello, world!)
            • Japanese: こんにちは、世界！(Hello, world!)
            • Korean: 안녕하세요, 세계! (Hello, world!)
            • Russian: Привет, мир! (Hello, world!)
            • Arabic: مرحبا بالعالم! (Hello, world!)
            • Hebrew: שלום עולם! (Hello, world!)
            • Greek: Γειά σου Κόσμε! (Hello, world!)
            • Thai: สวัสดีชาวโลก! (Hello, world!)

            Emoji: 😀 🌍 🚀 🎉 🐱 🌈

            Mathematical Symbols: ∑ ∫ ∏ √ ∞ ∆ π Ω

            Currency Symbols: $ € £ ¥ ₹ ₽ ₩
            """.trimIndent()

            MediaTestScenarios.TextTestScenario.CORRUPTED_TEXT -> {
                return testResourcesDir.resolve("corrupted.txt")
            }
        }

        val file = testResourcesDir.resolve("test_${scenario.name.lowercase()}.txt")
        Files.writeString(file, textContent)
        return file
    }

    fun createMarkdownFileForScenario(scenario: MediaTestScenarios.MarkdownTestScenario, testResourcesDir: Path): Path {
        val markdownContent = when (scenario) {
            MediaTestScenarios.MarkdownTestScenario.BASIC_MARKDOWN -> """
                This is a simple markdown file for testing basic markdown processing.

                It includes **bold text**, *italic text*, and [a link](https://example.com).

                ---

                > This is a blockquote.
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.MALFORMED_SYNTAX -> """
                This is **bold text without closing

                This is *italic without closing

                [Link without URL]

                ![Image without src]

                ## Header without space#
                
                - List
                  - Subitem
                    - Wrong nesting
                  - Another one
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.NESTED_FORMATTING -> """
                **Bold text with *italic inside* and more bold**
                
                ***Combined formatting with `code inside` and ~~strikethrough~~***
                
                ~~Strikethrough with **bold** and *italic* inside~~
                
                `Code with **bold** and *italic*`
                
                > Quote with **bold**
                > > Nested quote with *italic*
                > > > Even more nested with `code`
                
                - **Bold list item**
                  - *Italic subitem*
                    - `Code in subitem`
                      - ~~Strikethrough in deeply nested item~~
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.EMBEDDED_HTML -> """
                <div class="container">
                  <h2>HTML Header</h2>
                  <p style="color: red;">Red text</p>
                </div>

                Regular Markdown text with <strong>HTML bold</strong> and <em>HTML italic</em>.

                <table>
                  <tr>
                    <td>HTML table</td>
                    <td>**Markdown in HTML**</td>
                  </tr>
                </table>

                <script>
                  alert('JavaScript code');
                </script>

                <!-- HTML comment -->

                <img src="image.jpg" alt="HTML image" />

                ## Header with <span style="color: blue;">blue text</span>
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.EMPTY_CODE_BLOCKS -> """
                ```javascript
                ```
                ```python
                ```
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.BROKEN_LINKS -> """
                [Link without URL]

                [Link with empty URL]()

                [Link to non-existent file](nonexistent.md)

                [Link with wrong protocol](https://example.com)

                ![Image without src]

                ![Image with wrong path](images/not-found.jpg)

                [Link with spaces in URL](https://example.com/path with spaces)

                [Unclosed link](https://example.com

                [Link with wrong brackets](https://example.com]

                [](https://example.com) <!-- Empty link text -->

                [Link to localhost](https://localhost:9999/invalid)

                [Relative link](../../../nonexistent.html)

                [Link with anchor to non-existent element](#nonexistent-anchor)
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.COMPLEX_NESTED_LISTS -> """
                1. First level
                   1. Second level numbered
                      - Third level bulleted
                        1. Fourth level numbered again
                           - Fifth level bulleted
                             * Sixth level with asterisk
                               + Seventh level with plus
                                 1. Eighth level numbered
                   2. Continuation of second level
                      - Item with long text that wraps to multiple lines and contains **bold text** and *italic*
                        
                        With a paragraph inside the list item.
                        
                        And another paragraph.
                        
                        ```javascript
                        // Code inside list item
                        console.log('Hello from nested list');
                        ```
                        
                        > Quote inside list item
                        > with multiple lines
                        
                        - Sublist inside item with code and quote

                2. Second item of first level
                   - Mixed list
                     1. Numbered inside bulleted
                        * Bulleted inside numbered
                          - Bulleted again
                            1. Numbered again
                              - Very deep nesting
                                + Different markers
                                  * Even deeper
                                    - Maximum depth?

                - Regular bulleted list
                  * With different markers
                    + On different levels
                      - Fourth type of marker
                  
                  Text at the same level as the list
                  
                  - List continuation after text
                    
                    With paragraph inside
                    
                    - And sublist

                List with wrong indentation:
                - Item 1
                 - Item with 1 space
                   - Item with 3 spaces
                     - Item with 5 spaces
                - Back to root
            """.trimIndent()
        }

        val file = testResourcesDir.resolve("test_${scenario.name.lowercase()}.md")
        Files.writeString(file, markdownContent)
        return file
    }

    fun createAudioFileForScenario(scenario: MediaTestScenarios.AudioTestScenario, testResourcesDir: Path): Path {
        return when (scenario) {
            MediaTestScenarios.AudioTestScenario.BASIC_WAV -> {
                testResourcesDir.resolve("test.wav")
            }

            MediaTestScenarios.AudioTestScenario.BASIC_MP3 -> {
                testResourcesDir.resolve("test.mp3")
            }

            MediaTestScenarios.AudioTestScenario.CORRUPTED_AUDIO -> {
                testResourcesDir.resolve("test_corrupted.wav")
            }
        }
    }

    fun createVideoFileForScenario(testResourcesDir: Path): Path {
        return testResourcesDir.resolve("video.mp4")
    }

    fun checkExecutorMediaResponse(response: Message.Response) {
        with(response) {
            checkResponseBasic(this)
            content.lowercase() shouldNotContain "error processing" shouldNotContain "unable to process" shouldNotContain "cannot process"
        }
    }

    fun checkImageAnalysisResponse(response: Message.Response) {
        checkExecutorMediaResponse(response)

        val content = response.content.lowercase()
        val imageHints = listOf("image", "picture", "illustration", "photo", "graphic")
        val visualDetailHints = listOf(
            "shows", "depicts", "contains", "background", "color", "shape", "object", "subject", "wing", "body"
        )

        imageHints.any(content::contains).shouldBe(true)
        visualDetailHints.any(content::contains).shouldBe(true)
    }

    fun checkResponseBasic(response: Message.Response) {
        response shouldNotBeNull {
            content.shouldNotBeBlank()
            content.length shouldBeGreaterThan 20
        }
    }
}
