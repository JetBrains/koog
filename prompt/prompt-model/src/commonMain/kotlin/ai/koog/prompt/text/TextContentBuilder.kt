package ai.koog.prompt.text

import ai.koog.prompt.dsl.PromptDSL

/**
 * A content builder class for building and manipulating purely textual content.
 *
 * @see TextContentBuilderBase
 */
@PromptDSL
public open class TextContentBuilder : TextContentBuilderBase<String>() {
    /**
     * Constructs and returns the accumulated textual content stored in the builder.
     *
     * @return A string representation of the textual content built using the current builder.
     */
    override fun build(): String = textBuilder.toString()
}

/**
 * Builds a textual content using a provided builder block and returns it as a string.
 *
 * @param block A lambda function applied to a [TextContentBuilder] instance, where the textual content is constructed.
 * @return A string representation of the built content after applying the builder block.
 */
public fun text(block: TextContentBuilder.() -> Unit): String = TextContentBuilder().apply(block).build()

/**
 * Extension function to add text content to a TextContentBuilder.
 *
 * Useful for embedding text content within other text content.
 *
 * Example:
 * ```kotlin
 * TextContentBuilder().apply {
 *     text { +"Some text before markdown." }
 *     text { +"Some other text." }
 * }
 * ```
 *
 * @param init The text content builder
 */
public inline fun TextContentBuilderBase<*>.text(init: TextContentBuilder.() -> Unit) {
    text(TextContentBuilder().apply(init).build())
}
