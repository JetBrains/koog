package ai.koog.prompt.text

/**
 * Builds a textual content using a provided builder block and returns it as a string.
 *
 * @param block A lambda function applied to a [TextContentBuilder] instance, where the textual content is constructed.
 * @return A string representation of the built content after applying the builder block.
 */
public fun text(block: TextContentBuilder.() -> Unit): String = TextContentBuilder().apply(block).build()

