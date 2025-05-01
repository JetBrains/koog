package ai.grazie.code.prompt.markdown

import ai.jetbrains.code.prompt.text.TextContentBuilder

/**
 * A dedicated builder for creating markdown content.
 * Wraps TextContentBuilder and provides markdown-specific functionality.
 */
class MarkdownContentBuilder : TextContentBuilder() {
    companion object {
        private val INDENTATION_ITEM = " ".repeat(2)
    }

    /**
     * Adds a markdown header to the content.
     * @param level The header level (1-6)
     * @param text The header text
     */
    fun header(level: Int, text: String) {
        require(level in 1..6) { "Header level must be between 1 and 6" }
        val prefix = "#".repeat(level)
        +"$prefix $text"
    }

    /**
     * Adds a level 1 header (# Header) to the content.
     * @param text The header text
     */
    fun h1(text: String) = header(1, text)

    /**
     * Adds a level 2 header (## Header) to the content.
     * @param text The header text
     */
    fun h2(text: String) = header(2, text)

    /**
     * Adds a level 3 header (### Header) to the content.
     * @param text The header text
     */
    fun h3(text: String) = header(3, text)

    /**
     * Adds a level 4 header (#### Header) to the content.
     * @param text The header text
     */
    fun h4(text: String) = header(4, text)

    /**
     * Adds a level 5 header (##### Header) to the content.
     * @param text The header text
     */
    fun h5(text: String) = header(5, text)

    /**
     * Adds a level 6 header (###### Header) to the content.
     * @param text The header text
     */
    fun h6(text: String) = header(6, text)

    /**
     * Adds a bold text (**text**) to the content.
     * @param text The text to make bold
     */
    fun bold(text: String) {
        +"**$text**"
    }

    /**
     * Adds an italic text (*text*) to the content.
     * @param text The text to make italic
     */
    fun italic(text: String) {
        +"*$text*"
    }

    /**
     * Adds a strikethrough text (~~text~~) to the content.
     * @param text The text to strikethrough
     */
    fun strikethrough(text: String) {
        +"~~$text~~"
    }

    /**
     * Adds a code span (`code`) to the content.
     * @param code The code to add
     */
    fun code(code: String) {
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
    fun codeblock(code: String, language: String = "") {
        +"```$language"
        +code
        +"```"
    }

    /**
     * Adds a link ([text](url)) to the content.
     * @param text The link text
     * @param url The link URL
     */
    fun link(text: String, url: String) {
        +"[$text]($url)"
    }

    /**
     * Adds an image (![alt](url)) to the content.
     * @param alt The image alt text
     * @param url The image URL
     */
    fun image(alt: String, url: String) {
        +"![$alt]($url)"
    }

    /**
     * Adds a horizontal rule (---) to the content.
     */
    fun horizontalRule() {
        +"---"
    }

    /**
     * Adds a blockquote to the content.
     * @param text The text to quote
     */
    fun blockquote(text: String) {
        text.split("\n").forEach {
            +"> $it"
        }
    }

    fun line(block: LineContext.() -> Unit) {
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
    fun table(
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

    class LineContext(val builder: TextContentBuilder = TextContentBuilder()) {
        fun space(): LineContext {
            builder.text(" ")
            return this
        }

        fun text(text: String): LineContext {
            builder.text(text)
            return this
        }

        fun bold(text: String): LineContext {
            builder.text("**$text**")
            return this
        }

        fun italic(text: String): LineContext {
            builder.text("*$text*")
            return this
        }

        fun strikethrough(text: String): LineContext {
            builder.text("~~$text~~")
            return this
        }

        fun code(code: String): LineContext {
            builder.text("`$code`")
            return this
        }

        fun link(text: String, url: String): LineContext {
            builder.text("[$text]($url)")
            return this
        }

        fun image(alt: String, url: String): LineContext {
            builder.text("![$alt]($url)")
            return this
        }
    }

    /**
     * Context for building bulleted lists.
     */
    inner class ListContext(val bullet: (counter: Int) -> String) {
        private var counter = 0

        /**
         * Adds a bulleted list item.
         */
        fun item(text: String) {
            val bullet = bullet(counter++)
            for ((index, line) in text.split("\n").withIndex()) {
                val text = when {
                    index == 0 -> "$bullet$line"
                    line.isNotBlank() -> " ".repeat(bullet.length) + line
                    else -> line
                }
                +text
            }
        }

        /**
         * Adds a bulleted list item with a block of content for nested items.
         */
        fun item(block: MarkdownContentBuilder.() -> Unit) {
            item(MarkdownContentBuilder().apply(block).build())
        }

        fun item(title: String, block: MarkdownContentBuilder.() -> Unit) {
            item(
                "$title\n" + MarkdownContentBuilder().apply(block).build()
            )
        }
    }


    /**
     * Adds a bulleted list with a block structure.
     * @param block The list content builder
     */
    fun bulleted(block: ListContext.() -> Unit) {
        val context = ListContext { "- " }
        context.block()
    }

    /**
     * Adds a numbered list with a block structure.
     * @param block The list content builder
     */
    fun numbered(block: ListContext.() -> Unit) {
        val context = ListContext { "${it + 1}. " }
        context.block()
    }
}

/**
 * Enum for table column alignments.
 */
enum class TableAlignment {
    LEFT, CENTER, RIGHT
}

// ... (markdown extension functions remain the same) ...
inline fun StringBuilder.markdown(init: MarkdownContentBuilder.() -> Unit) {
    append(MarkdownContentBuilder().apply(init).build())
}

inline fun TextContentBuilder.markdown(init: MarkdownContentBuilder.() -> Unit) {
    text(MarkdownContentBuilder().apply(init).build())
}

/**
 * Creates a markdown document with the given content.
 * @param init The content builder
 * @return The markdown document as a string
 */
fun markdown(init: MarkdownContentBuilder.() -> Unit): String {
    return MarkdownContentBuilder().apply(init).build()
}