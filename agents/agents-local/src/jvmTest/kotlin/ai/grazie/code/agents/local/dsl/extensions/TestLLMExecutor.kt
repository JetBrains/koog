package ai.grazie.code.agents.local.dsl.extensions

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TestLLMExecutor : PromptExecutor {
    // Track the number of TLDR messages created
    var tldrCount = 0
        private set

    // Store the messages for inspection
    val messages = mutableListOf<Message>()

    // Reset the state for a new test
    fun reset() {
        tldrCount = 0
        messages.clear()
    }

    override suspend fun execute(prompt: Prompt): String {
        return handlePrompt(prompt).content
    }

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> = flow { emit(handlePrompt(prompt).content) }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        prompt.messages.forEach { println("[DEBUG_LOG] Message: ${it.content}") }

        // Store all messages for later inspection
        messages.addAll(prompt.messages)

        // For compression test, return a TLDR summary
        if (prompt.messages.any { it.content.contains("Create a comprehensive summary of this conversation") }) {
            tldrCount++
            val tldrResponse = Message.Assistant("TLDR #$tldrCount: Summary of conversation history")
            messages.add(tldrResponse)
            return tldrResponse
        }

        val response = Message.Assistant("Default test response")
        messages.add(response)
        return response
    }
}
