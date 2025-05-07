package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TestLLMExecutor : PromptExecutor {
    override suspend fun execute(prompt: Prompt): String {
        return handlePrompt(prompt).content
    }

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> {
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
