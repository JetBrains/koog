package ai.koog.prompt.structure.markdown

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * Represents a specific definition of structured data that uses Markdown for its schema
 * and example formatting. This class is part of a framework for constructing and handling
 * structured data definitions through textual builders.
 *
 * @property id A string identifier for the structured data definition.
 * @property schema A lambda representing the Markdown-based schema definition, applied
 * to a [TextContentBuilder].
 * @property examples An optional lambda providing Markdown-formatted examples of the
 * structured data, applied to a [TextContentBuilder].
 * @property definitionPrompt Prompt with definition, explaining the structure to the LLM.
 */
public class MarkdownStructureDefinition(
    public val id: String,
    public val schema: TextContentBuilder.() -> Unit,
    public val examples: (TextContentBuilder.() -> Unit)? = null,
    private val definitionPrompt: (
        builder: TextContentBuilderBase<*>,
        structureDefinition: MarkdownStructureDefinition
    ) -> TextContentBuilderBase<*> = ::defaultDefinitionPrompt,
) : StructureDefinition {

    override fun definition(builder: TextContentBuilderBase<*>): TextContentBuilderBase<*> = definitionPrompt(builder, this)

    /**
     * Companion object for [MarkdownStructureDefinition] class, providing utility methods.
     */
    public companion object {
        /**
         * Default prompt explaining the structure definition of [MarkdownStructureDefinition] to the LLM.
         */
        public fun defaultDefinitionPrompt(
            builder: TextContentBuilderBase<*>,
            structureDefinition: MarkdownStructureDefinition
        ): TextContentBuilderBase<*> = builder.apply {
            with(structureDefinition) {
                +"DEFINITION OF $id"
                +"The $id format is defined only and solely with Markdown, without any additional characters, backticks or anything similar."
                newline()

                +"You must adhere to the following Markdown schema:"
                markdown {
                    schema(this)
                }
                newline()

                if (examples != null) {
                    +"Here are some examples of the $id format:"
                    markdown {
                        examples.invoke(this)
                    }
                }
            }
        }
    }
}
