package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.mpp.LoggerFactory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * A wrapper around [ai.jetbrains.code.prompt.executor.model.PromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [ai.jetbrains.code.prompt.executor.model.PromptExecutor] to wrap.
 * @property pipeline The [AgentPipeline] associated with the executor.
 */
class PromptExecutorProxy(
    private val executor: PromptExecutor,
    private val pipeline: AgentPipeline
) : PromptExecutor {

    companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.local.agent.PipelineAwarePromptExecutor")
    }

    override suspend fun execute(prompt: Prompt, model: LLModel): String {
        logger.debug { "Executing LLM call prompt: $prompt" }
        pipeline.onBeforeLLMCall(prompt)

        val response = executor.execute(prompt, model)

        logger.debug { "Finished LLM call with response: $response" }
        pipeline.onAfterLLMCall(response)

        return response
    }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }
        pipeline.onBeforeLLMWithToolsCall(prompt, tools)

        val response = executor.execute(prompt, model, tools)

        logger.debug { "Finished LLM call with response: $response" }
        pipeline.onAfterLLMWithToolsCall(response, tools)

        return response
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return executor.executeStreaming(prompt, model)
    }
}
