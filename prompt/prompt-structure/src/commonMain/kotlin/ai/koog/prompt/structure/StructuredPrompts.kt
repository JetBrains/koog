package ai.koog.prompt.structure

import ai.koog.prompt.markdown.MarkdownContentBuilder
import ai.koog.prompt.text.TextContentBuilder
/**
 * An object that provides utilities for formatting structured output prompts.
 */
public object StructuredOutputPrompts {
    /**
     * Formats and appends the structured data output to the provided MarkdownContentBuilder.
     *
     * @param builder The MarkdownContentBuilder instance used to append the formatted output.
     * @param structure The StructuredData instance containing the format ID and definition for the output.
     */
    public fun output(builder: MarkdownContentBuilder, structure: StructuredData<*>): TextContentBuilder =
        builder.apply {
            h2("NEXT MESSAGE OUTPUT FORMAT")
            +"The output in the next message MUST ADHERE TO ${structure.id} format."
            structure.definition(this)
        }
}
