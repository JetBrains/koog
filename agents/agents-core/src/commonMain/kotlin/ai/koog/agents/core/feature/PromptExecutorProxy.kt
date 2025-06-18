package ai.koog.agents.core.feature

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap.
 * @property pipeline The [AIAgentPipeline] associated with the executor.
 */
public class PromptExecutorProxy(
    private val sessionId: String,
    private val executor: PromptExecutor,
    private val pipeline: AIAgentPipeline,
) : PromptExecutor {

    private companion object {
        private val logger = KotlinLogging.logger {  }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing LLM call (prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        pipeline.onBeforeLLMCall(sessionId, prompt, tools, model)

        val responses = executor.execute(prompt, model, tools)

        logger.debug { "Finished LLM call with responses: [${responses.joinToString { "${it.role}: ${it.content}" } }]" }
        pipeline.onAfterLLMCall(sessionId, prompt, tools, model, responses)

        return responses
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing LLM streaming call (prompt: $prompt)" }

        val stream = executor.executeStreaming(prompt, model)
        pipeline.onStartLLMStreaming(sessionId, prompt, model)

        return stream
    }
}
