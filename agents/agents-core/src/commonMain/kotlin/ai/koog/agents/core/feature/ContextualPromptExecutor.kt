package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.jvm.JvmSynthetic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap;
 * @property context The [AIAgentContext] associated with the agent executing the prompt.
 */
@InternalAgentsApi
public class ContextualPromptExecutor(
    private val executor: PromptExecutor,
    private val context: AIAgentContext,
) : PromptExecutor() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()

        val promptBeforeInterceptors = context.llm.prompt // because onLLMCallStarting might change context.llm.prompt

        logger.debug { "Starting LLM call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        context.pipeline.onLLMCallStarting(eventId, context.executionInfo, context, context.runId, prompt, model, tools)

        val effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
            logger.debug { "Executing LLM call with modified prompt (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
            context.llm.prompt
        } else {
            logger.debug { "Executing LLM call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
            prompt
        }

        try {
            val response = executor.execute(effectivePrompt, model, tools)
            logger.trace { "Finished LLM call (event id: $eventId) with responses: $response]" }

            context.pipeline.onLLMCallCompleted(eventId, context.executionInfo, context, context.runId, effectivePrompt, model, tools, response, moderationResponse = null)
            return response
        } catch (e: Throwable) {
            logger.debug(e) { "Error in executing LLM call (event id: $eventId): $e" }
            context.pipeline.onLLMCallFailed(eventId, context.executionInfo, context, context.runId, prompt, model, tools, error = e)
            throw e
        }
    }

    /**
     * Executes a streaming call to the language model with tool support.
     *
     * This method wraps the underlying executor's streaming functionality with pipeline hooks
     * to enable monitoring and processing of stream events. It triggers before-stream handlers
     * before starting, stream-frame handlers for each frame received, and after-stream handlers
     * upon completion.
     *
     * @param prompt The prompt to send to the language model
     * @param model The language model to use for streaming
     * @param tools The list of available tool descriptors for the streaming call
     * @return A Flow of StreamFrame objects representing the streaming response
     */
    @JvmSynthetic
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        @OptIn(ExperimentalUuidApi::class)
        val eventId: String = Uuid.random().toString()

        logger.debug { "Executing LLM streaming call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }

        var effectivePrompt: Prompt = prompt

        return flow {
            val promptBeforeInterceptors = context.llm.prompt // because onLLMStreamingStarting might change it

            logger.debug { "Starting LLM streaming call (event id: $eventId)" }
            context.pipeline.onLLMStreamingStarting(eventId, context.executionInfo, context, context.runId, prompt, model, tools)

            effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
                logger.debug { "Executing LLM streaming call with modified prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${tools.joinToString { it.name }}])" }
                context.llm.prompt
            } else {
                logger.debug { "Executing LLM streaming call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
                prompt
            }

            executor.executeStreaming(effectivePrompt, model, tools).collect { frame ->
                emit(frame)
            }
        }
            .onEach { frame ->
                logger.trace { "Received frame from LLM streaming call (event id: $eventId): $frame" }
                context.pipeline.onLLMStreamingFrameReceived(eventId, context.executionInfo, context, context.runId, prompt = effectivePrompt, model, streamFrame = frame)
            }
            .catch { error ->
                logger.debug(error) { "Error in LLM streaming call (event id: $eventId): $error" }
                context.pipeline.onLLMStreamingFailed(eventId, context.executionInfo, context, context.runId, prompt = effectivePrompt, model, error = error)

                throw error
            }
            .onCompletion { error ->
                logger.debug(error) { "Finished LLM streaming call (event id: $eventId): $error" }

                // Note: it will be executed in any case (even if error is null)
                context.pipeline.onLLMStreamingCompleted(eventId, context.executionInfo, context, context.runId, prompt = effectivePrompt, model, tools)
            }
    }

    // TODO: Add Pipeline interceptors for this method. Without them features cannot modify prompts before calls to LLMs.
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): LLMChoice {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }

        val responses = executor.executeMultipleChoices(prompt, model, tools)

        logger.debug { "Finished LLM call with LLM Choice response: $responses" }

        return responses
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()
        val promptBeforeInterceptors = context.llm.prompt

        logger.debug { "Starting moderation LLM request (event id: $eventId, prompt: $prompt)" }

        context.pipeline.onLLMCallStarting(eventId, context.executionInfo, context, context.runId, prompt, model, tools = emptyList())

        val effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
            logger.debug { "Executing moderation LLM request with modified prompt (event id: $eventId, prompt: ${context.llm.prompt})" }
            context.llm.prompt
        } else {
            logger.debug { "Executing moderation LLM request (event id: $eventId, prompt: $prompt)" }
            prompt
        }

        try {
            val result = executor.moderate(effectivePrompt, model)
            logger.trace { "Finished moderation LLM request (event id: $eventId) with response: $result" }

            context.pipeline.onLLMCallCompleted(eventId, context.executionInfo, context, context.runId, effectivePrompt, model, tools = emptyList(), response = null, moderationResponse = result)
            return result
        } catch (e: Throwable) {
            logger.debug(e) { "Error in moderation LLM request (event id: $eventId): $e" }
            context.pipeline.onLLMCallFailed(eventId, context.executionInfo, context, context.runId, prompt, model, tools = emptyList(), error = e)

            throw e
        }
    }

    override suspend fun models(): List<LLModel> {
        return executor.models()
    }

    override fun getStandardJsonSchemaGenerator(model: LLModel): StandardJsonSchemaGenerator {
        return executor.getStandardJsonSchemaGenerator(model)
    }

    override fun getBasicJsonSchemaGenerator(model: LLModel): BasicJsonSchemaGenerator {
        return executor.getBasicJsonSchemaGenerator(model)
    }

    override fun close() {
        executor.close()
    }
}
