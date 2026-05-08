package ai.koog.prompt.executor.llms

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.ExecutionCompleted
import ai.koog.prompt.executor.model.ExecutionDispatched
import ai.koog.prompt.executor.model.ExecutionFailed
import ai.koog.prompt.executor.model.ExecutionRequested
import ai.koog.prompt.executor.model.HookablePromptExecutor
import ai.koog.prompt.executor.model.ModerationCompleted
import ai.koog.prompt.executor.model.ModerationDispatched
import ai.koog.prompt.executor.model.ModerationFailed
import ai.koog.prompt.executor.model.ModerationRequested
import ai.koog.prompt.executor.model.MultipleChoicesCompleted
import ai.koog.prompt.executor.model.MultipleChoicesDispatched
import ai.koog.prompt.executor.model.MultipleChoicesFailed
import ai.koog.prompt.executor.model.MultipleChoicesRequested
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.StreamingCompleted
import ai.koog.prompt.executor.model.StreamingDispatched
import ai.koog.prompt.executor.model.StreamingFailed
import ai.koog.prompt.executor.model.StreamingFrameReceived
import ai.koog.prompt.executor.model.StreamingRequested
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
        private val logger = KotlinLogging.logger("ai.koog.prompt.executor.llms.LLMPromptExecutor")
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): List<Message.Response> {
        context.handle(ExecutionRequested(context.promptExecutionId, prompt, model, tools))
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        context.handle(ExecutionDispatched(context.promptExecutionId, prompt, model, tools))

        val response = try {
            llmClient.execute(prompt, model, tools)
        } catch (error: Throwable) {
            context.handle(ExecutionFailed(context.promptExecutionId, prompt, model, tools, error))
            throw error
        }

        context.handle(ExecutionCompleted(context.promptExecutionId, prompt, model, tools, response))
        logger.debug { "Response: $response" }
        return response
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): Flow<StreamFrame> {
        return flow {
            context.handle(StreamingRequested(context.promptExecutionId, prompt, model, tools))
            logger.debug { "Executing streaming prompt: $prompt with tools: $tools and model: $model" }
            context.handle(StreamingDispatched(context.promptExecutionId, prompt, model, tools))

            try {
                llmClient.executeStreaming(prompt, model, tools).collect { frame ->
                    context.handle(StreamingFrameReceived(context.promptExecutionId, prompt, model, tools, frame))
                    emit(frame)
                }
            } catch (error: Throwable) {
                context.handle(StreamingFailed(context.promptExecutionId, prompt, model, tools, error))
                throw error
            }

            context.handle(StreamingCompleted(context.promptExecutionId, prompt, model, tools))
        }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: PromptExecutionContext
    ): List<LLMChoice> {
        context.handle(MultipleChoicesRequested(context.promptExecutionId, prompt, model, tools))
        logger.debug { "Executing prompt: $prompt with tools: $tools and model: $model" }
        context.handle(MultipleChoicesDispatched(context.promptExecutionId, prompt, model, tools))

        val choices = try {
            llmClient.executeMultipleChoices(prompt, model, tools)
        } catch (error: Throwable) {
            context.handle(MultipleChoicesFailed(context.promptExecutionId, prompt, model, tools, error))
            throw error
        }

        context.handle(MultipleChoicesCompleted(context.promptExecutionId, prompt, model, tools, choices))
        logger.debug { "Choices: $choices" }
        return choices
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel, context: PromptExecutionContext): ModerationResult {
        context.handle(ModerationRequested(context.promptExecutionId, prompt, model))
        context.handle(ModerationDispatched(context.promptExecutionId, prompt, model))

        val result = try {
            llmClient.moderate(prompt, model)
        } catch (error: Throwable) {
            context.handle(ModerationFailed(context.promptExecutionId, prompt, model, error))
            throw error
        }

        context.handle(ModerationCompleted(context.promptExecutionId, prompt, model, result))
        return result
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
