package ai.koog.integration.tests.utils

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking

object JavaUtils {
    @JvmStatic
    fun executeClientBlocking(
        client: LLMClient,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> = runBlocking {
        client.execute(prompt, model, tools)
    }

    @JvmStatic
    fun executeExecutorBlocking(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList()
    ): List<Message.Response> = runBlocking {
        executor.execute(prompt, model, tools)
    }

    @JvmStatic
    fun <T : Any> requestLLMStructuredBlocking(
        context: AIAgentFunctionalContext,
        message: String,
        outputType: Class<T>
    ): T = runBlocking {
        context.requestLLMStructured(message, outputType.kotlin, emptyList(), null).getOrThrow().data
    }
}
