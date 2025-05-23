package ai.koog.agents.local.features.eventHandler.feature

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TestLLMExecutor : PromptExecutor {
    override suspend fun execute(prompt: Prompt, model: LLModel): String {
        return handlePrompt(prompt).content
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return flow {
            emit(handlePrompt(prompt).content)
        }
    }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        // For a compression test, return a summary
        if (prompt.messages.any { it.content.contains("Summarize all the main achievements") }) {
            return Message.Assistant("Here's a summary of the conversation: Test user asked questions and received responses.")
        }

        return Message.Assistant("Default test response")
    }
}
