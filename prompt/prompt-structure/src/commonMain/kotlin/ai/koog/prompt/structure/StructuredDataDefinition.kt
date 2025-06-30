package ai.koog.prompt.structure

import ai.koog.prompt.text.TextContentBuilderBase
import ai.koog.prompt.text.TextContentBuilder

/**
 * Represents the definition of structured data, enabling content construction and customization.
 *
 * This interface provides a contract for defining structured data using a [TextContentBuilder],
 * which facilitates the creation and management of text content. Implementations of this interface
 * can define how structured content should be constructed, supporting a fluent API for content generation.
 */
public interface StructuredDataDefinition {
    /**
     * Defines the structure of textual content using the provided [TextContentBuilder].
     *
     * This function allows customization of the content through the builder pattern,
     * enabling users to add structured text and formatting as needed.
     *
     * @param builder The [TextContentBuilderBase] instance for constructing textual content.
     * @return The modified [TextContentBuilderBase] containing the structured content.
     */
    public fun definition(builder: TextContentBuilderBase<*>): TextContentBuilderBase<*>
}
