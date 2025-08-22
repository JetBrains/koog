package ai.koog.integration.tests.utils

import ai.koog.prompt.message.Message
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

object MediaTestUtils {
    fun getImageFileForScenario(scenario: MediaTestScenarios.ImageTestScenario, testResourcesDir: Path): Path {
        return when (scenario) {
            MediaTestScenarios.ImageTestScenario.BASIC_PNG -> {
                testResourcesDir.resolve("test.png")
            }

            MediaTestScenarios.ImageTestScenario.BASIC_JPG -> {
                testResourcesDir.resolve("test.jpeg")
            }

            MediaTestScenarios.ImageTestScenario.EMPTY_IMAGE -> {
                testResourcesDir.resolve("empty.png")
            }

            MediaTestScenarios.ImageTestScenario.CORRUPTED_IMAGE -> {
                testResourcesDir.resolve("corrupted.png")
            }

            MediaTestScenarios.ImageTestScenario.LARGE_IMAGE -> {
                testResourcesDir.resolve("large.jpg")
            }

            MediaTestScenarios.ImageTestScenario.LARGE_IMAGE_ANTHROPIC -> {
                testResourcesDir.resolve("large_5.jpg")
            }

            MediaTestScenarios.ImageTestScenario.SMALL_IMAGE -> {
                testResourcesDir.resolve("small.png")
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
                "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n" +
                    "   /\\_/\\  \n" +
                    "  ( o.o ) \n" +
                    "   > ^ <\n" +
                    "(∑, ∞, ∂)\n"

            MediaTestScenarios.TextTestScenario.CODE_SNIPPET -> """
            // Java code snippet
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }

            # Python code snippet
            def greet(name):
                return f"Hello, {name}!"

            print(greet("World"))
            """.trimIndent()

            MediaTestScenarios.TextTestScenario.FORMATTED_TEXT -> """
            # Heading 1
            ## Heading 2
            ### Heading 3

            This is a paragraph with *italic* and **bold** text.

            * Bullet point 1
            * Bullet point 2
              * Nested bullet point

            1. Numbered item 1
            2. Numbered item 2
               1. Nested numbered item

            > This is a blockquote

            ---

            This is another paragraph after a horizontal rule.
            """.trimIndent()

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

            MediaTestScenarios.TextTestScenario.LONG_TEXT_5_MB -> { // for Anthropic
                return testResourcesDir.resolve("fakefile_5MB.txt")
            }

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

            MediaTestScenarios.MarkdownTestScenario.HEADERS -> """
                # H1 Header

                ## H2 Header

                ### H3 Header

                #### H4 Header

                ##### H5 Header

                ###### H6 Header
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.LISTS -> """
                ## Unordered List

                - Item 1
                - Item 2
                  - Nested item 2.1
                  - Nested item 2.2
                - Item 3

                ## Ordered List

                1. First item
                2. Second item
                   1. Nested item 2.1
                   2. Nested item 2.2
                3. Third item
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.CODE_BLOCKS -> """
                Inline code: `const x = 10;`

                ```javascript
                // JavaScript code block
                function greet(name) {
                    return `Hello, ${'$'}{name}!`;
                }

                console.log(greet('World'));
                ```

                ```python
                # Python code block
                def greet(name):
                    return f"Hello, {name}!"

                print(greet("World"))
                ```
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.LINKS -> """
                ## Basic Links

                [Link to Google](https://www.google.com)

                [Link with title](https://www.example.com "Example Website")

                <https://www.example.com> - Automatic link

                ## Reference Links

                [Reference link][ref1]

                [ref1]: https://www.reference.com "Reference Website"

                ## Image Links

                ![Alt text for image](https://example.com/image.jpg "Image Title")
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.TABLES -> """
                ## Simple Table

                | Header 1 | Header 2 | Header 3 |
                |----------|----------|----------|
                | Cell 1   | Cell 2   | Cell 3   |
                | Cell 4   | Cell 5   | Cell 6   |

                ## Table with Alignment

                | Left-aligned | Center-aligned | Right-aligned |
                |:-------------|:--------------:|--------------:|
                | Left         | Center         | Right         |
                | Text         | Text           | Text          |
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.FORMATTING -> """
                ## Text Formatting

                **Bold text** and __also bold text__

                *Italic text* and _also italic text_

                ***Bold and italic*** and ___also bold and italic___

                ~~Strikethrough text~~

                ## Horizontal Rules

                ---

                ***

                ___

                ## Escaping Characters

                \*Not italic\*

                \`Not code\`

                \# Not a heading
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

            MediaTestScenarios.MarkdownTestScenario.IRREGULAR_TABLES -> """
                | Header 1 | Header 2 
                | --- | ---
                | Cell 1 | Cell 2 |

                | Without | Separator |
                | Row without right border
                | --- | --- | --- |
                | Too many | columns | here | and more |

                Header 1 | Header 2 | Header 3
                --- | ---
                Missing | separators

                |  | Empty header |
                | --- | --- |
                | Normal cell | |

                | Very long header that doesn't fit | Short |
                |---|---|
                | Short | Very long cell with lots of text that goes beyond boundaries |
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.MATH_NOTATION -> """
                Inline formula: \$\E = mc^2${'$'}

                Block formula:
                ${'$'}${'$'}
                \int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}
                ${'$'}${'$'}

                Complex formula:
                ${'$'}${'$'}
                \sum_{n=1}^{\infty} \frac{1}{n^2} = \frac{\pi^2}{6}
                ${'$'}${'$'}

                Matrix:
                ${'$'}${'$'}
                \begin{pmatrix}
                a & b \\
                c & d
                \end{pmatrix}
                ${'$'}${'$'}

                Formula with ${'$'}\alpha, \beta, \gamma${'$'} Greek letters.

                ${'$'}${'$'}
                \lim_{x \to \infty} \frac{1}{x} = 0
                ${'$'}${'$'}

                Fraction: ${'$'}\frac{a}{b} = \frac{numerator}{denominator}${'$'}

                Invalid LaTeX: ${'$'}\undefined{command}${'$'}                
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.EMPTY_CODE_BLOCKS -> """
                ```javascript
                ```
                ```python
                ```
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.SPECIAL_CHARS_HEADERS -> """
                # Header with symbols: !@#${'$'}%^&*()

                ## Header with emoji: 🚀 Rocket 🌟

                ### Header with quotes: "This is a header"

                #### Header with apostrophe: That's a header

                ##### Header with &amp; ampersand &lt;tags&gt;

                ###### Header with < > brackets and | pipe

                # Header with [square] brackets

                ## Header with {curly} brackets

                ### Header with symbols: ~!@#${'$'}%^&*()_+{}|:"<>?

                #### Header with unicode: 中文 Русский العربية

                ##### Header with math: ∑∞∫∂∆

                ###### Header with \\backslashes\\
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.BROKEN_LINKS -> """
                [Link without URL]

                [Link with empty URL]()

                [Link to non-existent file](nonexistent.md)

                [Link with wrong protocol](htps://example.com)

                ![Image without src]

                ![Image with wrong path](images/not-found.jpg)

                [Link with spaces in URL](http://example.com/path with spaces)

                [Unclosed link](http://example.com

                [Link with wrong brackets](http://example.com]

                [](http://example.com) <!-- Empty link text -->

                [Link to localhost](http://localhost:9999/invalid)

                [Relative link](../../../nonexistent.html)

                [Link with anchor to non-existent element](#nonexistent-anchor)
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.EMPTY_MARKDOWN -> ""

            MediaTestScenarios.MarkdownTestScenario.MIXED_INDENTATION -> """
                  - Item with 2 spaces
                    - Subitem with 4 spaces
                	- Subitem with tab
                        - Subitem with 8 spaces
                  	- Mixed indentation (space + tab)

                1. Numbered list
                   - Bulleted subitem
                	2. Numbered subitem with tab
                    3. Numbered with 4 spaces
                  	- Mixed indentation

                    Code with 4 spaces
                	Code with tab
                      Code with 6 spaces
                  	Code with mixed indentation

                > Quote
                  > Quote with 2 spaces
                	> Quote with tab
                    > Quote with 4 spaces

                - List item
                  Continuation with 2 spaces
                	Continuation with tab
                    Continuation with 4 spaces
            """.trimIndent()

            MediaTestScenarios.MarkdownTestScenario.COMMENTS -> """
                <!-- This is an HTML comment -->

                Regular text.

                <!-- 
                Multi-line
                HTML comment
                -->

                [//]: # (This is an alternative comment style)

                [//]: # "Another way"

                [comment]: <> (Link-style comment)

                <!-- Comment with **Markdown** inside -->

                Text with <!-- inline comment --> continuation.

                <!-- Comment spanning
                multiple
                lines -->

                [//]: # (Comment between list items)

                - Item 1
                [//]: # (Hidden comment)
                - Item 2

                <!-- Comment in code:
                ```javascript
                javascript console.log('hidden');
                ```
                -->
                [comment]: # (Comment at end of file)
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

    fun checkExecutorMediaResponse(response: Message.Response) {
        checkResponseBasic(response)
        val responseLowerCase = response.content.lowercase()
        assertFalse(responseLowerCase.contains("error processing"), "Result should not contain error messages")
        assertFalse(
            responseLowerCase.contains("unable to process"),
            "Result should not indicate inability to process"
        )
        assertFalse(responseLowerCase.contains("cannot process"), "Result should not indicate inability to process")
    }

    fun checkResponseBasic(response: Message.Response) {
        assertNotNull(response, "Response should not be null")
        assertTrue(response.content.isNotBlank(), "Result should not be empty or blank")
        assertTrue(response.content.length > 20, "Result should contain more than 20 characters")
    }
}
