package ai.koog.prompt.text

import ai.koog.prompt.dsl.PromptDSL

/**
 * A base utility class for building and manipulating content based on the text.
 * This class can be extended to support more types of text-based content, e.g. text with attachments or some metadata.
 *
 * Provides methods for constructing formatted strings with features such as inserting text,
 * adding new lines, and applying padding. The builder pattern supports a fluent and convenient
 * approach to managing text content.
 */
@PromptDSL
public abstract class TextContentBuilderBase<TContent> {
        /**
     * Represents the position of a caret within a text document.
     *
     * @property line The line number where the caret is located, starting from 0.
     * @property offset The position within the line (character index) where the caret is located, starting from 0.
     */
    public data class Caret(val line: Int, val offset: Int)

    /**
     * Internal property used to accumulate and manage the textual content being constructed.
     *
     * This `StringBuilder` instance serves as the core mechanism for appending, modifying,
     * and managing the text content within the context of `TextContentBuilder`. It is used
     * by various functions such as `text`, `newline`, and `textWithNewLine` to build and format
     * the resulting text dynamically.
     */
    protected val textBuilder: StringBuilder = StringBuilder()

    /**
     * Represents the current caret position in the text being built.
     *
     * The caret's position is determined by the number of lines and the character offset in the last line
     * of the internal text content managed by the builder.
     */
    public val caret: Caret
        get() = Caret(textBuilder.lines().size, textBuilder.lines().lastOrNull()?.length ?: 0)

    /**
     * Defines a custom operator function for the String class.
     * This unary `not` operator (`!`) is invoked on a String instance
     * and performs a custom action defined by the `text` function.
     *
     * The implementation of this function uses the `this` keyword to reference
     * the current String the operator is called on and passes it to the `text` function.
     *
     * This is typically used to enhance the behavior or further abstract operations
     * on String objects in a concise and intuitive manner.
     */
    public operator fun String.not() {
        text(this)
    }

    /**
     * Adds the given string as a new line of text to the content being built by the [TextContentBuilder].
     *
     * This operator function ensures that the text is appended on a new line if the caret is not
     * already at the beginning of a new line. If the caret is positioned at the start of a line,
     * the string is directly appended.
     *
     * The method is designed to simplify building multiline text content and integrates seamlessly
     * with the [TextContentBuilder], supporting fluent and declarative content construction.
     */
    public open operator fun String.unaryPlus() {
        textWithNewLine(this)
    }

    /**
     * Appends the given text to the current content.
     *
     * @param text The string to be appended to the content.
     */
    public fun text(text: String) {
        textBuilder.append(text)
    }

    /**
     * Adds the given text to the content. If the caret is not at the beginning of the line,
     * a newline is added before appending the text.
     *
     * @param text The text to be added to the content.
     */
    public fun textWithNewLine(text: String) {
        if (caret.offset > 0) newline()
        text(text)
    }

    /**
     * Adds padding to each line of the content produced by the provided builder block.
     *
     * @param padding The string to prepend to each line of the content.
     * @param body A lambda function applied to a [TextContentBuilder] instance, where the content is constructed.
     */
    public fun padding(padding: String, body: TextContentBuilder.() -> Unit) {
        val content = TextContentBuilder().apply(body).build()
        for (line in content.lines()) {
            +"$padding$line"
        }
    }


    /**
     * Appends a newline character to the underlying text builder.
     *
     * This method is primarily used to ensure proper formatting
     * and separation of text content in the [TextContentBuilder] class.
     */
    public fun newline() {
        textBuilder.append("\n")
    }

    /**
     * Adds two consecutive newline characters to the text content.
     *
     * This function is typically used to create a double line break, ensuring
     * greater spacing between sections of the textual content.
     */
    public fun br() {
        newline()
        newline()
    }

    /**
     * Constructs and returns the content
     */
    public abstract fun build(): TContent
}