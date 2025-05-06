package ai.jetbrains.code.prompt.executor.llms

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.mpp.LoggerFactory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.DirectLLMClient
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Executes prompts using a direct client for communication with large language model (LLM) providers.
 *
 * This class provides functionality to execute prompts with optional tools and retrieve either a list of responses
 * or a streaming flow of response chunks from the LLM provider. It delegates the actual LLM interaction to the provided
 * implementation of `DirectLLMClient`.
 *
 * @constructor Creates an instance of `LLMCodePromptExecutor`.
 * @param llmClient The client used for direct communication with the LLM provider.
 */
open class SingleLLMCodePromptExecutor(
    private val llmClient: DirectLLMClient,
) : CodePromptExecutor {
    companion object {
        private val logger = LoggerFactory.create("ai.jetbrains.code.prompt.executor.llms.LLMCodePromptExecutor")
    }

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools" }

        val response = llmClient.execute(prompt, tools)

        logger.debug { "Response: $response" }

        return response
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt" }

        val responseFlow = llmClient.executeStreaming(prompt)

        responseFlow.collect { chunk ->
            emit(chunk)
        }
    }
}