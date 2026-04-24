package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecutorHooksHelper.executeWithHook
import ai.koog.prompt.executor.model.ExecutorHooksHelper.streamingWithHook
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorHooks
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
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
@Deprecated(
    "Please use MultiLLMPromptExecutor instead",
    replaceWith = ReplaceWith("MultiLLMPromptExecutor", "ai.koog.prompt.executor.llms.MultiLLMPromptExecutor")
)
public open class SingleLLMPromptExecutor(
    private val llmClient: LLMClient,
) : PromptExecutor() {
    private companion object {
        private val logger = KotlinLogging.logger("ai.koog.prompt.executor.llms.LLMPromptExecutor")
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hooks: PromptExecutorHooks?
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        return executeWithHook(InitialExecutionIntent(prompt, tools, model), hook = hooks?.execute) { finalIntent ->
            llmClient.execute(finalIntent.prompt, model, finalIntent.tools)
                .also { logger.debug { "Response: $it" } }
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hooks: PromptExecutorHooks?
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with tools: $tools and model: $model" }
        return streamingWithHook(InitialExecutionIntent(prompt, tools, model), hook = hooks?.streaming) { finalIntent ->
            llmClient.executeStreaming(finalIntent.prompt, model, finalIntent.tools)
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hooks: PromptExecutorHooks?
    ): List<LLMChoice> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        return executeWithHook(InitialExecutionIntent(prompt, tools, model), hook = hooks?.multipleChoices) { finalIntent ->
            llmClient.executeMultipleChoices(finalIntent.prompt, model, finalIntent.tools)
                .also { logger.debug { "Choices: $it" } }
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hooks: PromptExecutorHooks?
    ): ModerationResult =
        executeWithHook(InitialExecutionIntent(prompt = prompt, model = model), model, hooks?.moderation) { finalIntent ->
            llmClient.moderate(finalIntent.prompt, model)
        }

    override suspend fun models(): List<LLModel> = llmClient.models()

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        return llmClient.getStandardJsonSchemaGenerator()
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        return llmClient.getBasicJsonSchemaGenerator()
    }

    override fun close() {
        llmClient.close()
    }
}
