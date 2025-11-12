package ai.koog.prompt.structure

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * An object that provides utilities for formatting structured output prompts.
 */
public object StructuredOutputPrompts {
    /**
     * Formats and appends the structured data output to the provided MarkdownContentBuilder.
     *
     * @param structure The StructuredData instance containing the format ID and definition for the output.
     */
    public fun outputInstructionPrompt(builder: TextContentBuilderBase<*>, structure: Structure<*, *>): TextContentBuilderBase<*> = builder.apply {
        markdown {
            h2("NEXT MESSAGE OUTPUT FORMAT")
            +"The output in the next message MUST ADHERE TO ${structure.id} format."
            br()

            structure.definition(this)
        }
    }
}
