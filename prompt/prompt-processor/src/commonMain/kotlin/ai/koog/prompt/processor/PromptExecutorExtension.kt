package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Executes the given prompt and processes responses using the given [responseProcessor].
 */
public suspend fun PromptExecutor.executeProcessed(
    prompt: Prompt,
    model: LLModel,
    tools: List<ToolDescriptor>,
    responseProcessor: ResponseProcessor? = null
): List<Message.Response> {
    val responses = execute(prompt, model, tools)
    return responseProcessor?.process(this, prompt, model, tools, responses) ?: responses
}
