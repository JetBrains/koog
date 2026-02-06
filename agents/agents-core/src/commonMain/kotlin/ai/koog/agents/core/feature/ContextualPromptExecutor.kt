package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap;
 * @property context The [AIAgentContext] associated with the agent that is executing the prompt.
 */
public class ContextualPromptExecutor(
    private val executor: PromptExecutor,
    private val context: AIAgentContext,
) : PromptExecutor {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()

        logger.debug { "Executing LLM call (event id: $eventId, prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        context.pipeline.onLLMCallStarting(eventId, context.executionInfo, context.runId, prompt, model, tools, context)

        val responses = executor.execute(prompt, model, tools)

        logger.trace { "Finished LLM call (event id: $eventId) with responses: [${responses.joinToString { "${it.role}: ${it.content}" }}]" }
        context.pipeline.onLLMCallCompleted(
            eventId,
            context.executionInfo,
            context.runId,
            prompt,
            model,
            tools,
            responses,
            null,
            context
        )

        return responses
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

        return executor.executeStreaming(prompt, model, tools)
            .onStart {
                logger.debug { "Starting LLM streaming call (event id: $eventId)" }
                context.pipeline.onLLMStreamingStarting(
                    eventId,
                    context.executionInfo,
                    context.runId,
                    prompt,
                    model,
                    tools,
                    context
                )
            }
            .onEach { frame ->
                logger.debug { "Received frame from LLM streaming call (event id: $eventId): $frame" }
                context.pipeline.onLLMStreamingFrameReceived(
                    eventId,
                    context.executionInfo,
                    context.runId,
                    prompt,
                    model,
                    frame,
                    context
                )
            }
            .catch { error ->
                logger.debug(error) { "Error in LLM streaming call (event id: $eventId): $error" }
                context.pipeline.onLLMStreamingFailed(
                    eventId,
                    context.executionInfo,
                    context.runId,
                    prompt,
                    model,
                    error,
                    context
                )
                throw error
            }
            .onCompletion { error ->
                logger.debug(error) { "Finished LLM streaming call (event id: $eventId): $error" }
                context.pipeline.onLLMStreamingCompleted(
                    eventId,
                    context.executionInfo,
                    context.runId,
                    prompt,
                    model,
                    tools,
                    context
                )
            }
    }

    // TODO: Add Pipeline interceptors for this method
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

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        @OptIn(ExperimentalUuidApi::class)
        val eventId = Uuid.random().toString()

        logger.debug { "Executing moderation LLM request (event id: $eventId, prompt: $prompt)" }

        context.pipeline.onLLMCallStarting(
            eventId,
            context.executionInfo,
            context.runId,
            prompt,
            model,
            tools = emptyList(),
            context
        )

        val result = executor.moderate(prompt, model)
        logger.trace { "Finished moderation LLM request (event id: $eventId) with response: $result" }

        context.pipeline.onLLMCallCompleted(
            eventId,
            context.executionInfo,
            context.runId,
            prompt,
            model,
            tools = emptyList(),
            responses = emptyList(),
            moderationResponse = result,
            context = context
        )

        return result
    }

    override suspend fun models(): List<String> {
        return executor.models()
    }

    override fun close() {
        executor.close()
    }
}
