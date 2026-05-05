package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.ExecutionCompleted
import ai.koog.prompt.executor.model.ExecutionFailed
import ai.koog.prompt.executor.model.ExecutionRequested
import ai.koog.prompt.executor.model.ExecutionSubmitted
import ai.koog.prompt.executor.model.ModerationCompleted
import ai.koog.prompt.executor.model.ModerationFailed
import ai.koog.prompt.executor.model.ModerationRequested
import ai.koog.prompt.executor.model.ModerationSubmitted
import ai.koog.prompt.executor.model.MultipleChoicesCompleted
import ai.koog.prompt.executor.model.MultipleChoicesFailed
import ai.koog.prompt.executor.model.MultipleChoicesRequested
import ai.koog.prompt.executor.model.MultipleChoicesSubmitted
import ai.koog.prompt.executor.model.ObservablePromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.PromptExecutorEvent
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap;
 * @property context The [AIAgentContext] associated with the agent that is executing the prompt.
 */
@InternalAgentsApi
public class ContextualPromptExecutor(
    private val executor: ObservablePromptExecutor,
    private val context: AIAgentContext,
) : PromptExecutor() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val observer = ContextualPromptExecutorObserver(executor.events, context, logger)
    init {
        observer.startObserving()
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()
        val promptExecutionContext = PromptExecutionContext(promptExecutionId = eventId)

        val promptBeforeInterceptors = context.llm.prompt // because onLLMCallRequested might change context.llm.prompt

        logger.debug { "Requested LLM call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        context.pipeline.onLLMCallRequested(eventId, context.executionInfo, context.runId, prompt, model, tools, context)

        val effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
            logger.debug { "Executing LLM call with modified prompt (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
            context.llm.prompt
        } else {
            logger.debug { "Executing LLM call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
            prompt
        }

        return try {
            executor.execute(effectivePrompt, model, tools, promptExecutionContext)
                .also { responses ->
                    logger.trace { "Finished LLM call (event id: $eventId) with responses: [${responses.joinToString { "${it.role}: ${it.content}" }}]" }
                }
        } catch (error: Throwable) {
            logger.debug(error) { "Error in executing LLM call (event id: $eventId): $error" }
            throw error
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()
        val promptExecutionContext = PromptExecutionContext(promptExecutionId = eventId)
        val promptBeforeInterceptors = context.llm.prompt

        logger.debug { "Requested moderation LLM request (event id: $eventId, prompt: $prompt)" }

        context.pipeline.onLLMCallRequested(eventId, context.executionInfo, context.runId, prompt, model, tools = emptyList(), context)

        val effectivePrompt = if (context.llm.prompt !== promptBeforeInterceptors) {
            logger.debug { "Executing moderation LLM request with modified prompt (event id: $eventId, prompt: ${context.llm.prompt})" }
            context.llm.prompt
        } else {
            logger.debug { "Executing moderation LLM request (event id: $eventId, prompt: $prompt)" }
            prompt
        }

        return try {
            executor.moderate(effectivePrompt, model, promptExecutionContext)
                .also { result ->
                    logger.trace { "Finished moderation LLM request (event id: $eventId) with response: $result" }
                }
        } catch (error: Throwable) {
            logger.debug(error) { "Error in moderation LLM request (event id: $eventId): $error" }
            throw error
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
            context.pipeline.onLLMStreamingStarting(eventId, context.executionInfo, context.runId, prompt, model, tools, context)

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
                context.pipeline.onLLMStreamingFrameReceived(eventId, context.executionInfo, context.runId, prompt = effectivePrompt, model, streamFrame = frame, context)
            }
            .catch { error ->
                logger.debug(error) { "Error in LLM streaming call (event id: $eventId): $error" }
                context.pipeline.onLLMStreamingFailed(eventId, context.executionInfo, context.runId, prompt = effectivePrompt, model, error = error, context)

                throw error
            }
            .onCompletion { error ->
                logger.debug(error) { "Finished LLM streaming call (event id: $eventId): $error" }

                // Note: it will be executed in any case (even if error is null)
                context.pipeline.onLLMStreamingCompleted(eventId, context.executionInfo, context.runId, prompt = effectivePrompt, model, tools, context)
            }
    }

    // TODO: Add Pipeline interceptors for this method. Without them features cannot modify prompts before calls to LLMs.
    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }

        val responses = executor.executeMultipleChoices(prompt, model, tools)

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
        observer.close()
        executor.close()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class ContextualPromptExecutorObserver(
        private val events: Flow<PromptExecutorEvent>,
        private val context: AIAgentContext,
        private val logger: KLogger
    ): AutoCloseable {

        private val bridgeJob = SupervisorJob()
        private val bridgeScope = CoroutineScope(bridgeJob + Dispatchers.Default)

        private val isObserving = AtomicBoolean(false)

        fun startObserving() {
            if (isObserving.compareAndSet(false, true)) {
                bridgeScope.launch {
                    events.collect { event -> handleEvent(event, context) }
                }
            }
        }

        override fun close() {
            bridgeJob.cancel()
            isObserving.store(false)
        }

        private suspend fun handleEvent(
            event: PromptExecutorEvent,
            context: AIAgentContext
        ) {
            when (event) {
                is ExecutionRequested, is MultipleChoicesRequested, is ModerationRequested -> {
                    logger.debug { "Effective executor received ExecutionRequested event for prompt: ${event.prompt}, model: ${event.model}, tools: [${event.tools.joinToString { it.name }}]" }
                }

                is ExecutionSubmitted, is MultipleChoicesSubmitted, is ModerationSubmitted -> {
                    context.pipeline.onLLMCallSubmitted(
                        eventId = event.context.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context
                    )
                }

                is ExecutionCompleted, is MultipleChoicesCompleted, is ModerationCompleted -> {
                    context.pipeline.onLLMCallCompleted(
                        eventId = event.context.promptExecutionId,
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

                is ExecutionFailed, is MultipleChoicesFailed, is ModerationFailed -> {
                    context.pipeline.onLLMCallFailed(
                        eventId = event.context.promptExecutionId,
                        executionInfo = context.executionInfo,
                        runId = context.runId,
                        prompt = event.prompt,
                        model = event.model,
                        tools = event.tools,
                        context = context,
                        error = event.error
                    )
                }

                else -> Unit
            }
        }

        private fun handleSubmission(){

        }

        private fun handleCompletion(){

        }

        private fun handleFailure(){

        }
    }
}

