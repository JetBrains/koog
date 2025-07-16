package ai.koog.agents.core.feature

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.LLMChoice
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow

/**
 * A wrapper around [ai.koog.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.koog.prompt.executor.model.PromptExecutor] to wrap.
 * @property pipeline The [AIAgentPipeline] associated with the executor.
 */
public class PromptExecutorProxy(
    private val executor: PromptExecutor,
    private val pipeline: AIAgentPipeline,
    private val runId: String,
) : PromptExecutor {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing LLM call (prompt: $prompt, tools: [${tools.joinToString { it.name }}])" }
        pipeline.onBeforeLLMCall(runId, prompt, model, tools)

        val responses = executor.execute(prompt, model, tools)

        logger.debug { "Finished LLM call with responses: [${responses.joinToString { "${it.role}: ${it.content}" }}]" }
        pipeline.onAfterLLMCall(runId, prompt, model, tools, responses)

        return responses
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        logger.debug { "Executing LLM streaming call (prompt: $prompt)" }
        val stream = executor.executeStreaming(prompt, model)

        return stream
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
                messageBuilder.appendLine("- Response #${index}")
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
        pipeline.onBeforeLLMCall(runId, prompt, model, emptyList())
        val result = executor.moderate(prompt, model)
        logger.debug { "Finished moderation LLM request with response: $result" }
        pipeline.onAfterLLMCall(runId, prompt, model, emptyList(), responses = emptyList(), moderationResponse = result)
        return result
    }
}
