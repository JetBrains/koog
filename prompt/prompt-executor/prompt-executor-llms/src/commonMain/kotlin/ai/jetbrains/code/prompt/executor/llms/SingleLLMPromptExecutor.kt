package ai.jetbrains.code.prompt.executor.llms

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.mpp.LoggerFactory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.clients.DirectLLMClient
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
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
 * @constructor Creates an instance of `LLMPromptExecutor`.
 * @param llmClient The client used for direct communication with the LLM provider.
 */
open class SingleLLMPromptExecutor(
    private val llmClient: DirectLLMClient,
) : PromptExecutor {
    companion object {
        private val logger = LoggerFactory.create("ai.jetbrains.code.prompt.executor.llms.LLMPromptExecutor")
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val response = llmClient.execute(prompt, model, tools)

        logger.debug { "Response: $response" }

        return response
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }

        val responseFlow = llmClient.executeStreaming(prompt, model)

        responseFlow.collect { chunk ->
            emit(chunk)
        }
    }
}
