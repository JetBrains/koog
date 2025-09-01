package ai.koog.agents.examples.tripplanning.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet


class UserTools(private val showUserMessage: suspend (String) -> String) : ToolSet {
    @Tool
    @LLMDescription("Show user a message from the agent and wait for a response. Call this tool to ask the user something.")
    suspend fun showMessage(
        @LLMDescription("The message to show to the user.")
        message: String
    ): String {
        return showUserMessage(message)
    }
}