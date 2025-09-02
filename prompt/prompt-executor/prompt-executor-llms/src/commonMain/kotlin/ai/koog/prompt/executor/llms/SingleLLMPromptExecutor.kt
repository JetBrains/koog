package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow

/**
 * Executes prompts using a direct client for communication with large language model (LLM) providers.
 *
 * This class provides functionality to execute prompts with optional tools and retrieve either a list of responses
 * or a streaming flow of response chunks from the LLM provider. It delegates the actual LLM interaction to the provided
 * implementation of `LLMClient`.
 *
 * @constructor Creates an instance of `LLMPromptExecutor`.
 * @param llmClient The client used for direct communication with the LLM provider.
 */
public open class SingleLLMPromptExecutor(
    private val llmClient: LLMClient,
) : PromptExecutor {
    private companion object {
        private val logger = KotlinLogging.logger("ai.koog.prompt.executor.llms.LLMPromptExecutor")
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        val response = llmClient.execute(prompt, model, tools)
        logger.debug { "Response: $response" }

        return response
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }

        return llmClient.executeStreaming(prompt, model)
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        val choices = llmClient.executeMultipleChoices(prompt, model, tools)
        logger.debug { "Choices: $choices" }

        return choices
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = llmClient.moderate(prompt, model)
}
