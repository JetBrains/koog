package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
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

    override suspend fun execute(prompt: Prompt, model: LLModel): String {
        return handlePrompt(prompt).content
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow { emit(handlePrompt(prompt).content) }

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
