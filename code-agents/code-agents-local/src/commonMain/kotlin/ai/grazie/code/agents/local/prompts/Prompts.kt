package ai.grazie.code.agents.local.prompts

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.text.TextContentBuilder
import ai.grazie.code.prompt.markdown.*

internal object Prompts {
    fun TextContentBuilder.selectRelevantTools(tools: List<ToolDescriptor>, subtaskDescription: String) =
        markdown {
            +"You will be now concentrating on solving the following task:"
            br()

            h2("TASK DESCRIPTION")
            br()
            +subtaskDescription
            br()

            h2("AVAILABLE TOOLS")
            br()
            +"You have the following tools available:"
            br()
            bulleted {
                tools.forEach {
                    item("Name: ${it.name}\nDescription: ${it.description}")
                }
            }
            br()
            br()

            +"Please, provide a list of the tools ONLY RELEVANT FOR THE GIVEN TASK, separated by commas."
            +"Think carefully about the tools you select, and make sure they are relevant to the task."
        }
}