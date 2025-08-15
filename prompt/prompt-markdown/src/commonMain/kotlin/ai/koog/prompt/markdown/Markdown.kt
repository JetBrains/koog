package ai.koog.prompt.markdown

import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * A dedicated builder for creating markdown content.
 * Wraps [TextContentBuilder] and provides markdown-specific functionality for generating
 * well-formatted Markdown documents programmatically.
 *
 * Example usage:
 * ```kotlin
 * val document = markdown {
 *     h1("Hello, Markdown!")
 *     +"This is a paragraph of text."
 *
 *     h2("Features")
 *     bulleted {
 *         item("Easy to use")
 *         item("Type-safe")
 *         item("Extensible")
 *     }
 *
 *     link("Learn more", "https://example.com")
 * }
 * ```
 */
public class MarkdownContentBuilder : TextContentBuilder() {
    private companion object {
        private val INDENTATION_ITEM = " ".repeat(2)
    }

    /**
     * Adds a markdown header to the content.
     * @param level The header level (1-6)
     * @param text The header text
     */
    public fun header(level: Int, text: String) {
        require(level in 1..6) { "Header level must be between 1 and 6" }
        val prefix = "#".repeat(level)
        +"$prefix $text"
    }

    /**
     * Adds a level 1 header (# Header) to the content.
     * @param text The header text
     */
    public fun h1(text: String): Unit = header(1, text)

    /**
     * Adds a level 2 header (## Header) to the content.
     * @param text The header text
     */
    public fun h2(text: String): Unit = header(2, text)

    /**
     * Adds a level 3 header (### Header) to the content.
     * @param text The header text
     */
    public fun h3(text: String): Unit = header(3, text)

    /**
     * Adds a level 4 header (#### Header) to the content.
     * @param text The header text
     */
    public fun h4(text: String): Unit = header(4, text)

    /**
     * Adds a level 5 header (##### Header) to the content.
     * @param text The header text
     */
    public fun h5(text: String): Unit = header(5, text)

    /**
     * Adds a level 6 header (###### Header) to the content.
     * @param text The header text
     */
    public fun h6(text: String): Unit = header(6, text)

    /**
     * Adds a bold text (**text**) to the content.
     * @param text The text to make bold
     */
    public fun bold(text: String) {
        +"**$text**"
    }

    /**
     * Adds an italic text (*text*) to the content.
     * @param text The text to make italic
     */
    public fun italic(text: String) {
        +"*$text*"
    }

    /**
     * Adds a strikethrough text (~~text~~) to the content.
     * @param text The text to strikethrough
     */
    public fun strikethrough(text: String) {
        +"~~$text~~"
    }

    /**
     * Adds a code span (`code`) to the content.
     * @param code The code to add
     */
    public fun code(code: String) {
        +"`$code`"
    }

    /**
     * Adds a code block with optional language specification to the content.
     * ```language
     * code
     * ```
     * @param code The code to add
     * @param language The language for syntax highlighting (optional)
     */
    public fun codeblock(code: String, language: String = "") {
        +"```$language"
        +code
        +"```"
    }

    /**
     * Adds a link ([text](url)) to the content.
     * @param text The link text
     * @param url The link URL
     */
    public fun link(text: String, url: String) {
        +"[$text]($url)"
    }

    /**
     * Adds an image (![alt](url)) to the content.
     * @param alt The image alt text
     * @param url The image URL
     */
    public fun image(alt: String, url: String) {
        +"![$alt]($url)"
    }

    /**
     * Adds a horizontal rule (---) to the content.
     */
    public fun horizontalRule() {
        +"---"
    }

    /**
     * Adds a blockquote to the content.
     * @param text The text to quote
     */
    public fun blockquote(text: String) {
        text.split("\n").forEach {
            +"> $it"
        }
    }

    /**
     * Creates a single line of markdown content with mixed formatting.
     * Useful for creating complex inline formatting combinations.
     *
     * Example:
     * ```kotlin
     * line {
     *     text("This is ")
     *     bold("bold")
     *     text(" and ")
     *     italic("italic")
     *     text(" text with a ")
     *     link("link", "https://example.com")
     * }
     * ```
     *
     * @param block The line content builder
     */
    public fun line(block: LineContext.() -> Unit) {
        val text = LineContext().apply(block).builder.build()
        if (text.isNotBlank()) {
            +text
        }
    }

    /**
     * Adds a table to the content.
     * @param headers The table headers
     * @param rows The table rows, each row is a list of cells
     * @param alignments The column alignments (optional)
     */
    public fun table(
        headers: List<String>,
        rows: List<List<String>>,
        alignments: List<TableAlignment> = List(headers.size) { TableAlignment.LEFT }
    ) {
        require(headers.isNotEmpty()) { "Table must have at least one column" }
        require(alignments.size == headers.size) { "Number of alignments must match number of columns" }

        // Headers
        +"| ${headers.joinToString(" | ")} |"

        // Separator row with alignments
        val separators = alignments.map {
            when (it) {
                TableAlignment.LEFT -> ":---"
                TableAlignment.CENTER -> ":---:"
                TableAlignment.RIGHT -> "---:"
            }
        }
        +"| ${separators.joinToString(" | ")} |"

        // Data rows
        rows.forEach { row ->
            require(row.size == headers.size) { "Row size must match number of columns" }
            +"| ${row.joinToString(" | ")} |"
        }
    }

    /**
     * Context for building a single line of markdown with mixed formatting elements.
     * Used within the [line] method to create complex inline formatting.
     *
     * All methods return the context itself to allow for method chaining.
     *
     * @property builder The underlying [TextContentBuilder] used to build the content
     */
    public class LineContext(internal val builder: TextContentBuilder = TextContentBuilder()) {
        /**
         * Adds a space to the line.
         * @return This context for method chaining
         */
        public fun space(): LineContext {
            builder.text(" ")
            return this
        }

        /**
         * Adds plain text to the line.
         * @param text The text to add
         * @return This context for method chaining
         */
        public fun text(text: String): LineContext {
            builder.text(text)
            return this
        }

        /**
         * Adds bold text (**text**) to the line.
         * @param text The text to make bold
         * @return This context for method chaining
         */
        public fun bold(text: String): LineContext {
            builder.text("**$text**")
            return this
        }

        /**
         * Adds italic text (*text*) to the line.
         * @param text The text to make italic
         * @return This context for method chaining
         */
        public fun italic(text: String): LineContext {
            builder.text("*$text*")
            return this
        }

        /**
         * Adds strikethrough text (~~text~~) to the line.
         * @param text The text to strikethrough
         * @return This context for method chaining
         */
        public fun strikethrough(text: String): LineContext {
            builder.text("~~$text~~")
            return this
        }

        /**
         * Adds a code span (`code`) to the line.
         * @param code The code to add
         * @return This context for method chaining
         */
        public fun code(code: String): LineContext {
            builder.text("`$code`")
            return this
        }

        /**
         * Adds a link ([text](url)) to the line.
         * @param text The link text
         * @param url The link URL
         * @return This context for method chaining
         */
        public fun link(text: String, url: String): LineContext {
            builder.text("[$text]($url)")
            return this
        }

        /**
         * Adds an image (![alt](url)) to the line.
         * @param alt The image alt text
         * @param url The image URL
         * @return This context for method chaining
         */
        public fun image(alt: String, url: String): LineContext {
            builder.text("![$alt]($url)")
            return this
        }
    }

    /**
     * Context for building structured lists (bulleted, numbered).
     *
     * This class provides methods for adding list items with various content types,
     * including plain text, nested content, and titled content with nested elements.
     *
     * @property bullet A function that generates the bullet/prefix for each list item based on its index
     */
    public inner class ListContext(private val bullet: (counter: Int) -> String) {
        private var counter = 0

        /**
         * Adds a list item with the given text.
         *
         * Handles multiline text by properly indenting continuation lines.
         *
         * @param text The text content of the list item
         */
        public fun item(text: String) {
            val bullet = bullet(counter++)
            for ((index, line) in text.split("\n").withIndex()) {
                val lineText = when {
                    index == 0 -> "$bullet$line"
                    line.isNotBlank() -> " ".repeat(bullet.length) + line
                    else -> line
                }
                +lineText
            }
        }

        /**
         * Adds a list item with content generated from a block.
         *
         * Useful for creating list items with complex content or nested elements.
         *
         * Example:
         * ```kotlin
         * bulleted {
         *     item {
         *         +"Complex item with multiple paragraphs"
         *         codeblock("val x = 1", "kotlin")
         *     }
         * }
         * ```
         *
         * @param block The content builder for the list item
         */
        public fun item(block: MarkdownContentBuilder.() -> Unit) {
            item(MarkdownContentBuilder().apply(block).build())
        }

        /**
         * Adds a list item with a title and nested content.
         *
         * The title appears on the first line, and the nested content follows on subsequent lines.
         *
         * Example:
         * ```kotlin
         * bulleted {
         *     item("Main point:", {
         *         +"Supporting details"
         *         bulleted {
         *             item("Sub-point 1")
         *             item("Sub-point 2")
         *         }
         *     })
         * }
         * ```
         *
         * @param title The title text for the list item
         * @param block The content builder for the nested content
         */
        public fun item(title: String, block: MarkdownContentBuilder.() -> Unit) {
            item(
                "$title\n" + MarkdownContentBuilder().apply(block).build()
            )
        }
    }

    /**
     * Adds a bulleted list with a block structure.
     *
     * Creates an unordered list with bullet points (`-`).
     *
     * Example:
     * ```kotlin
     * bulleted {
     *     item("First item")
     *     item("Second item") {
     *         // Nested content
     *         +"Additional details"
     *         bulleted {
     *             item("Nested item 1")
     *             item("Nested item 2")
     *         }
     *     }
     *     item("Third item")
     * }
     * ```
     *
     * @param block The list content builder
     */
    public fun bulleted(block: ListContext.() -> Unit) {
        val context = ListContext { "- " }
        context.block()
    }

    /**
     * Adds a numbered list with a block structure.
     *
     * Creates an ordered list with sequential numbers (`1.`, `2.`, etc.).
     *
     * Example:
     * ```kotlin
     * numbered {
     *     item("First step")
     *     item("Second step") {
     *         // Nested content
     *         +"Details about this step"
     *         bulleted {
     *             item("Important note")
     *             item("Another note")
     *         }
     *     }
     *     item("Final step")
     * }
     * ```
     *
     * @param block The list content builder
     */
    public fun numbered(block: ListContext.() -> Unit) {
        val context = ListContext { "${it + 1}. " }
        context.block()
    }
}

/**
 * Enum for table column alignments in markdown tables.
 */
public enum class TableAlignment {
    /**
     * Left-aligned column (`:---`)
     */
    LEFT,

    /**
     * Center-aligned column (`:---:`)
     */
    CENTER,

    /**
     * Right-aligned column (`---:`)
     */
    RIGHT
}

/**
 * Extension function to append markdown content to a StringBuilder.
 *
 * Example:
 * ```kotlin
 * val sb = StringBuilder()
 * sb.markdown {
 *     h1("Title")
 *     +"Content"
 * }
 * ```
 *
 * @param init The markdown content builder
 */
public inline fun StringBuilder.markdown(init: MarkdownContentBuilder.() -> Unit) {
    append(MarkdownContentBuilder().apply(init).build())
}

/**
 * Extension function to add markdown content to a TextContentBuilder.
 *
 * Useful for embedding markdown content within other text content.
 *
 * Example:
 * ```kotlin
 * TextContentBuilder().apply {
 *     text("Some text before markdown.")
 *     markdown {
 *         h2("Markdown Section")
 *         bulleted {
 *             item("Item 1")
 *             item("Item 2")
 *         }
 *     }
 *     text("Some text after markdown.")
 * }
 * ```
 *
 * @param init The markdown content builder
 */
public inline fun TextContentBuilderBase<*>.markdown(init: MarkdownContentBuilder.() -> Unit) {
    text(MarkdownContentBuilder().apply(init).build())
}

/**
 * Creates a markdown document with the given content.
 * @param init The content builder
 * @return The markdown document as a string
 */
public fun markdown(init: MarkdownContentBuilder.() -> Unit): String {
    return MarkdownContentBuilder().apply(init).build()
}
