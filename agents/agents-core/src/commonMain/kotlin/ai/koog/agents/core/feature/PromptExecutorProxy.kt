package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
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
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap.
 * @property pipeline The [ai.koog.agents.core.feature.pipeline.AIAgentPipeline] associated with the executor.
 */
@OptIn(ExperimentalUuidApi::class)
public class PromptExecutorProxy(
    private val executor: PromptExecutor,
    private val pipeline: AIAgentPipeline,
    private val runId: String,
) : PromptExecutor {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        val callId = Uuid.random().toString()
        logger.debug { "Executing LLM call (prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        pipeline.onLLMCallStarting(runId, callId, prompt, model, tools)

        val responses = executor.execute(prompt, model, tools)

        logger.trace { "Finished LLM call with responses: [${responses.joinToString { "${it.role}: ${it.content}" }}]" }
        pipeline.onLLMCallCompleted(runId, callId, prompt, model, tools, responses)

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
        logger.debug { "Executing LLM streaming call (prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        val callId: String = Uuid.random().toString()
        return executor.executeStreaming(prompt, model, tools)
            .onStart {
                logger.debug { "Starting LLM streaming call" }
                pipeline.onLLMStreamingStarting(runId, callId, prompt, model, tools)
            }
            .onEach { frame ->
                logger.debug { "Received frame from LLM streaming call: $frame" }
                pipeline.onLLMStreamingFrameReceived(runId, callId, frame)
            }
            .catch { error ->
                logger.debug(error) { "Error in LLM streaming call" }
                pipeline.onLLMStreamingFailed(runId, callId, error)
                throw error
            }
            .onCompletion { error ->
                logger.debug(error) { "Finished LLM streaming call" }
                pipeline.onLLMStreamingCompleted(runId, callId, prompt, model, tools)
            }
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }

        val responses = executor.executeMultipleChoices(prompt, model, tools)

        logger.debug {
            val messageBuilder = StringBuilder()
                .appendLine("Finished LLM call with LLM Choice response:")

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
        logger.debug { "Executing moderation LLM request (prompt: $prompt)" }
        val callId = Uuid.random().toString()

        pipeline.onLLMCallStarting(runId, callId, prompt, model, emptyList())

        val result = executor.moderate(prompt, model)
        logger.trace { "Finished moderation LLM request with response: $result" }

        pipeline.onLLMCallCompleted(runId, callId, prompt, model, emptyList(), responses = emptyList(), moderationResponse = result)
        return result
    }

    override suspend fun models(): List<String> {
        return executor.models()
    }

    override fun close() {
        executor.close()
    }
}
