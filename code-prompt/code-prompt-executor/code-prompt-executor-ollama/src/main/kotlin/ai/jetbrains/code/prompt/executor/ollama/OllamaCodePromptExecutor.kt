package ai.jetbrains.code.prompt.executor.ollama

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.create
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.executor.ollama.client.*
import ai.jetbrains.code.prompt.executor.ollama.client.dto.*
import ai.jetbrains.code.prompt.llm.OllamaModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Executes code-related prompts using LLM Chat services.
 * This executor provides a unified way to handle prompts and obtain responses from LLM services.
 */
class OllamaCodePromptExecutor(private val client: OllamaClient) : CodePromptExecutor {
    companion object {
        private val logger = LoggerFactory.create(OllamaCodePromptExecutor::class)
        fun default(): OllamaCodePromptExecutor = OllamaCodePromptExecutor(OllamaClient())
    }


    /**
     * Executes the given prompt and returns the response as message.
     *
     * @param prompt The prompt to execute
     * @return The text response from the LLM service
     * @throws IllegalStateException if no chat service is found for the specified model
     */
    override suspend fun execute(prompt: Prompt): String {
        logger.info { "Executing OLLAMA request" }
        val request = OllamaChatRequestDTO(
            model = prompt.model.toOllamaModelId(),
            messages = prompt.toOllamaChatMessages(),
            stream = false,
        )
        val result = client.chat(request)
        return result.message?.content ?: error("No message in response")
    }

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.info { "Executing OLLAMA request" }

        val request = OllamaChatRequestDTO(
            model = prompt.model.toOllamaModelId(),
            messages = prompt.toOllamaChatMessages(),
            tools = tools.map { it.toOllamaTool() },
            stream = false,
        )
        val result = client.chat(request)
        val message = result.message ?: error("No message in response")

        // Check if the model returned a tool call
        val maybeTool = message.getToolCall()
        if (maybeTool != null) return listOf(maybeTool)

        // Apply custom model-specific conversions if needed
        var content = message.content
        if (prompt.model == OllamaModels.Alibaba.QWQ) {
            content = OllamaCustomModelConverters.qwq(content)
        }

        return listOf(Message.Assistant(content))
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> = flow { emit(execute(prompt)) }
}
