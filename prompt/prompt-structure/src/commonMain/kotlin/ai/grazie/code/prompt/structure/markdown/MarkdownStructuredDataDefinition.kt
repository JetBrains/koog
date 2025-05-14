package ai.grazie.code.prompt.structure.markdown

import ai.grazie.code.prompt.markdown.markdown
import ai.grazie.code.prompt.structure.StructuredDataDefinition
import ai.jetbrains.code.prompt.text.TextContentBuilder

public class MarkdownStructuredDataDefinition(
    private val id: String,
    private val schema: TextContentBuilder.() -> Unit,
    private val examples: (TextContentBuilder.() -> Unit)? = null): StructuredDataDefinition {

    override fun definition(builder: TextContentBuilder): TextContentBuilder {
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
