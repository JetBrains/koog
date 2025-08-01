package ai.koog.agents.core.prompt

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.text.TextContentBuilderBase

internal object Prompts {
    fun TextContentBuilderBase<*>.selectRelevantTools(tools: List<ToolDescriptor>, subtaskDescription: String) =
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

    fun TextContentBuilderBase<*>.summarizeInTLDR() =
        markdown {
            +"Create a comprehensive summary of this conversation."
            br()
            +"Include the following in your summary:"
            numbered {
                item("Key objectives and problems being addressed")
                item("All tools used along with their purpose and outcomes")
                item("Critical information discovered or generated")
                item("Current progress status and conclusions reached")
                item("Any pending questions or unresolved issues")
            }
            br()
            +"FORMAT YOUR SUMMARY WITH CLEAR SECTIONS for easy reference, including:"
            bulleted {
                item("Key Objectives")
                item("Tools Used & Results")
                item("Key Findings")
                item("Current Status")
                item("Next Steps")
            }
            br()
            +"This summary will be the ONLY context available for continuing this conversation, along with the system message."
            +"Ensure it contains ALL essential information needed to proceed effectively."
        }
}
