package ai.koog.agents.core.feature.executor

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
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
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorEvent
import ai.koog.prompt.executor.model.PromptExecutorHook
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
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Wraps this executor in the appropriate contextual executor for the given [context].
 *
 * If this executor is a [HookablePromptExecutor], returns a [ContextualPromptExecutor] that provides a contextual
 * hook to drive pipeline callbacks. Otherwise returns a [LegacyContextualPromptExecutor] that intercepts calls
 * directly; consider migrating the executor to [HookablePromptExecutor] to get full pipeline integration.
 */
@InternalAgentsApi
public fun PromptExecutor.contextual(context: AIAgentContext): PromptExecutor =
    if (this is HookablePromptExecutor) {
        ContextualPromptExecutor(this, context)
    } else {
        LegacyContextualPromptExecutor(this, context)
    }

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap;
 * @property context The [AIAgentContext] associated with the agent that is executing the prompt.
 */
@InternalAgentsApi
public class ContextualPromptExecutor(
    private val executor: HookablePromptExecutor,
    private val context: AIAgentContext,
) : PromptExecutor() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val executorHook = ContextualPromptExecutorHook(context, logger)

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()
        val promptExecutionContext = promptExecutionContext(eventId)

        val promptBeforeInterceptors = context.llm.prompt // because onLLMCallStarting might change context.llm.prompt

        logger.debug { "Requested LLM call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        context.pipeline.onLLMCallStarting(eventId, context.executionInfo, context.runId, prompt, model, tools, context)

        val effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
            logger.debug { "Executing LLM call with modified prompt (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
            context.llm.prompt
        } else {
            prompt
        }

        return executor.execute(effectivePrompt, model, tools, promptExecutionContext)
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()
        val promptExecutionContext = promptExecutionContext(eventId)
        val promptBeforeInterceptors = context.llm.prompt

        logger.debug { "Requested moderation LLM request (event id: $eventId, prompt: $prompt)" }

        context.pipeline.onLLMCallStarting(eventId, context.executionInfo, context.runId, prompt, model, tools = emptyList(), context)

        val effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
            logger.debug { "Executing moderation LLM request with modified prompt (event id: $eventId, prompt: ${context.llm.prompt})" }
            context.llm.prompt
        } else {
            prompt
        }

        return executor.moderate(effectivePrompt, model, promptExecutionContext)
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
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> {
        @OptIn(ExperimentalUuidApi::class)
        val eventId: String = Uuid.random().toString()
        val promptExecutionContext = promptExecutionContext(eventId)

        var effectivePrompt: Prompt = prompt

        return flow {
            val promptBeforeInterceptors = context.llm.prompt // because onLLMStreamingStarting might change it

            logger.debug { "Starting LLM streaming call (event id: $eventId)" }
            context.pipeline.onLLMStreamingStarting(eventId, context.executionInfo, context.runId, prompt, model, tools, context)

            effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
                logger.debug { "Executing LLM streaming call with modified prompt (event id: $eventId, prompt: ${context.llm.prompt}, tools: [${tools.joinToString { it.name }}])" }
                context.llm.prompt
            } else {
                prompt
            }

            executor.executeStreaming(effectivePrompt, model, tools, promptExecutionContext).collect { frame ->
                emit(frame)
            }
        }
    }

    // TODO: Add Pipeline interceptors for this method. Without them features cannot modify prompts before calls to LLMs.
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }

        val responses = executor.executeMultipleChoices(prompt, model, tools, PromptExecutionContext())

        logger.debug {
            val messageBuilder = StringBuilder().appendLine("Finished LLM call with LLM Choice response:")

            responses.forEachIndexed { index, response ->
                messageBuilder.appendLine("- Response #$index")
                response.forEach { message ->
                    messageBuilder.appendLine("  -- [${message.role}] ${message.content}")
                }
            }

            "Finished LLM call with responses: $messageBuilder"
        }

        return responses
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

    private fun promptExecutionContext(eventId: String): PromptExecutionContext =
        PromptExecutionContext(
            promptExecutionId = eventId,
            executorHook = executorHook
        )

    private class ContextualPromptExecutorHook(
        private val context: AIAgentContext,
        private val logger: KLogger,
    ) : PromptExecutorHook {

        override suspend fun handle(event: PromptExecutorEvent) {
            when (event) {
                is ExecutionRequested -> {
                    logger.debug {
                        "Inner executor received LLM call request (event id: ${event.promptExecutionId}, prompt: ${event.prompt}, tools: [${event.tools.joinToString { it.name }}])"
                    }
                }

                is ModerationRequested -> {
                    logger.debug {
                        "Inner executor received moderation LLM request (event id: ${event.promptExecutionId}, prompt: ${event.prompt})"
                    }
                }

                is StreamingRequested -> {
                    logger.debug {
                        "Inner executor received LLM streaming request (event id: ${event.promptExecutionId}, prompt: ${event.prompt}, tools: [${event.tools.joinToString { it.name }}])"
                    }
                }

                is ExecutionDispatched -> {
                    logger.debug {
                        "Executing LLM call (event id: ${event.promptExecutionId}, prompt: ${event.prompt}, tools: [${event.tools.joinToString { it.name }}])"
                    }
                    context.pipeline.onLLMCallDispatched(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context
                    )
                }

                is ModerationDispatched -> {
                    logger.debug {
                        "Executing moderation LLM request (event id: ${event.promptExecutionId}, prompt: ${event.prompt})"
                    }
                    context.pipeline.onLLMCallDispatched(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = emptyList(),
                        context = context
                    )
                }

                is StreamingDispatched -> {
                    logger.debug {
                        "Executing LLM streaming call (event id: ${event.promptExecutionId}, prompt: ${event.prompt}, tools: [${event.tools.joinToString { it.name }}])"
                    }
                    context.pipeline.onLLMStreamingDispatched(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context
                    )
                }

                is ExecutionCompleted -> {
                    logger.trace {
                        "Finished LLM call (event id: ${event.promptExecutionId}) with responses: [${event.responses.joinToString { "${it.role}: ${it.content}" }}]"
                    }
                    context.pipeline.onLLMCallCompleted(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        responses = event.responses,
                        moderationResponse = null,
                        context = context
                    )
                }

                is ModerationCompleted -> {
                    logger.trace {
                        "Finished moderation LLM request (event id: ${event.promptExecutionId}) with response: ${event.result}"
                    }
                    context.pipeline.onLLMCallCompleted(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = emptyList(),
                        responses = emptyList(),
                        moderationResponse = event.result,
                        context = context
                    )
                }

                is StreamingFrameReceived -> {
                    logger.trace {
                        "Received frame from LLM streaming call (event id: ${event.promptExecutionId}): ${event.frame}"
                    }
                    context.pipeline.onLLMStreamingFrameReceived(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        streamFrame = event.frame,
                        context = context
                    )
                }

                is StreamingCompleted -> {
                    logger.debug { "Finished LLM streaming call (event id: ${event.promptExecutionId})" }
                    context.pipeline.onLLMStreamingCompleted(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context
                    )
                }

                is ExecutionFailed -> {
                    logger.debug(event.error) {
                        "Error in executing LLM call (event id: ${event.promptExecutionId}): ${event.error}"
                    }
                    context.pipeline.onLLMCallFailed(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context,
                        error = event.error
                    )
                }

                is ModerationFailed -> {
                    logger.debug(event.error) {
                        "Error in moderation LLM request (event id: ${event.promptExecutionId}): ${event.error}"
                    }
                    context.pipeline.onLLMCallFailed(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = emptyList(),
                        context = context,
                        error = event.error
                    )
                }

                is StreamingFailed -> {
                    logger.debug(event.error) {
                        "Error in LLM streaming call (event id: ${event.promptExecutionId}): ${event.error}"
                    }
                    context.pipeline.onLLMStreamingFailed(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        error = event.error,
                        context = context
                    )
                    // note: this is intended due to backward compatibility:
                    // we want to signal stream completion event if it failed
                    context.pipeline.onLLMStreamingCompleted(
                        eventId = event.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context
                    )
                }

                // TODO: Add Pipeline interceptors for executeMultipleChoices.
                is MultipleChoicesRequested,
                is MultipleChoicesDispatched,
                is MultipleChoicesCompleted,
                is MultipleChoicesFailed -> Unit
            }
        }
    }
}
