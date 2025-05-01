package ai.grazie.code.prompt.structure

import ai.grazie.code.prompt.markdown.MarkdownContentBuilder

object StructuredOutputPrompts {
    fun output(builder: MarkdownContentBuilder, structure: StructuredData<*>) = builder.apply {
        h2("NEXT MESSAGE OUTPUT FORMAT")
        +"The output in the next message MUST ADHERE TO ${structure.id} format."
        structure.definition(this)
    }
}