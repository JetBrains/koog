package ai.grazie.code.agents.tools.registry

import ai.grazie.code.agents.tools.registry.prompts.SystemPromptEnvSetup
import ai.grazie.code.prompt.markdown.markdown
import ai.jetbrains.code.prompt.text.TextContentBuilder


/**
 * Serves as a centralized repository for prompts used by agents.
 */
object GlobalAgentPrompts {
    object Generic {
        fun TextContentBuilder.summarizeInTLDR() {
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
    }

    object EnvSetup {
        val SystemPrompt = SystemPromptEnvSetup
    }
}
