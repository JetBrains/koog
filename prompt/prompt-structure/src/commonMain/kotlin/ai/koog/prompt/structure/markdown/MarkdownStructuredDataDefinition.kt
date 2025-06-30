package ai.koog.prompt.structure.markdown

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructuredDataDefinition
import ai.koog.prompt.text.TextContentBuilderBase
import ai.koog.prompt.text.TextContentBuilder

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
 */
public class MarkdownStructuredDataDefinition(
    private val id: String,
    private val schema: TextContentBuilder.() -> Unit,
    private val examples: (TextContentBuilder.() -> Unit)? = null): StructuredDataDefinition {

    override fun definition(builder: TextContentBuilderBase<*>): TextContentBuilderBase<*> {
        return builder.apply {
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
