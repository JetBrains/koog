package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecuteHook
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.InitialExecutionIntent
import ai.koog.prompt.executor.model.ModerateHook
import ai.koog.prompt.executor.model.MultipleChoicesHook
import ai.koog.prompt.executor.llms.PromptExecutorHelper.executeWithHook
import ai.koog.prompt.executor.llms.PromptExecutorHelper.streamWithHook
import ai.koog.prompt.executor.model.StreamingHook
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
) : HookablePromptExecutor() {

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: ExecuteHook?
    ): List<Message.Response> {
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        return executeWithHook(
            prompt = prompt,
            model = model,
            tools = tools,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            val response = effectiveClient.execute(finalIntent.prompt, effectiveModel, finalIntent.tools)
            logger.debug { "Response: $response" }
            response
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: StreamingHook?
    ): Flow<StreamFrame> {
        logger.debug { "Executing streaming prompt: $prompt with model: $model" }
        return streamWithHook(
            prompt = prompt,
            model = model,
            tools = tools,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            effectiveClient.executeStreaming(finalIntent.prompt, effectiveModel, finalIntent.tools)
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        hook: MultipleChoicesHook?
    ): List<LLMChoice> {
        logger.debug { "Executing multiple choices: $prompt with tools: $tools and model: $model" }
        return executeWithHook(
            prompt = prompt,
            model = model,
            tools = tools,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            val choices = effectiveClient.executeMultipleChoices(finalIntent.prompt, effectiveModel, finalIntent.tools)
            logger.debug { "Choices: $choices" }
            choices
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
        hook: ModerateHook?
    ): ModerationResult {
        logger.debug { "Moderating multi-modal content with model: ${model.id}" }
        return executeWithHook(
            prompt = prompt,
            model = model,
            chooseExecutionSubject = this::chooseExecutionSubject,
            hook = hook
        ) { finalIntent, (effectiveClient, effectiveModel) ->
            effectiveClient.moderate(finalIntent.prompt, effectiveModel)
        }
    }

    private fun chooseExecutionSubject(executionIntent: InitialExecutionIntent): EffectiveExecutionSubject =
        llmClient to executionIntent.model

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
