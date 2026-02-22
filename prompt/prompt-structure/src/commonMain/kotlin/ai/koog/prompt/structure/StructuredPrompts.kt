package ai.koog.prompt.structure

import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * An object that provides utilities for formatting structured output prompts.
 */
public object StructuredOutputPrompts {
    /**
     * Formats and appends the structured data output to the provided text builder.
     */
    public fun outputInstructionPrompt(builder: TextContentBuilderBase<*>, structure: Structure<*, *>): TextContentBuilderBase<*> = builder.apply {
        markdown {
            h2("NEXT MESSAGE OUTPUT FORMAT")
            +"The output in the next message MUST ADHERE TO ${structure.id} format."
            br()

            structure.definition(this)
        }
    }

    /**
     * Formats and appends structure examples, if they are present in the provided [structure], to the provided text builder,
     * to show an LLM expected output format. If [Structure.examples] is empty, nothing is appended.
     */
    public fun <T> examplesPrompt(builder: TextContentBuilderBase<*>, structure: Structure<T, *>): TextContentBuilderBase<*> = builder.apply {
        markdown {
            if (structure.examples.isNotEmpty()) {
                h4("EXAMPLES")

                if (structure.examples.size == 1) {
                    +"Here is an example of a valid response:"
                } else {
                    +"Here are some examples of valid responses:"
                }

                structure.examples.forEach { example ->
                    codeblock(
                        code = ai.koog.prompt.text.text {
                            structure(structure, example)
                        },
                    )
                }
            }
        }
    }
}
