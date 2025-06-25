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
