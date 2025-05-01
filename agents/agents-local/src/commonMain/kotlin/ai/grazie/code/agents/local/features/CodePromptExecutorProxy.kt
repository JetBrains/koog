package ai.grazie.code.agents.local.features

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.utils.mpp.LoggerFactory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.executor.model.CodePromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * A wrapper around [CodePromptExecutor] that allows for adding internal functionality to the executor
 * to catch and log events related to LLM calls.
 *
 * @property executor The [CodePromptExecutor] to wrap.
 * @property pipeline The [AIAgentPipeline] associated with the executor.
 */
class CodePromptExecutorProxy(
  private val executor: CodePromptExecutor,
  private val pipeline: AIAgentPipeline
): CodePromptExecutor {

    companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.local.agent.PipelineAwareCodePromptExecutor")
    }

    override suspend fun execute(prompt: Prompt): String {
        logger.debug { "Executing LLM call prompt: $prompt" }
        pipeline.onBeforeLLMCall(prompt)

        val response = executor.execute(prompt)

        logger.debug { "Finished LLM call with response: $response" }
        pipeline.onAfterLLMCall(response)

        return response
    }

    override suspend fun execute(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        logger.debug { "Executing LLM call prompt: $prompt with tools: [${tools.joinToString { it.name }}]" }
        pipeline.onBeforeLLMWithToolsCall(prompt, tools)

        val response = executor.execute(prompt, tools)

        logger.debug { "Finished LLM call with response: $response" }
        pipeline.onAfterLLMWithToolsCall(response, tools)

        return response
    }

    override suspend fun executeStreaming(prompt: Prompt): Flow<String> {
        return executor.executeStreaming(prompt)
    }
}
