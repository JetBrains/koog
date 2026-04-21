package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONSerializer

/**
 * Configuration for a [ResponseProcessor] process request.
 *
 * @property responseProcessor The processor to use.
 * @property serializer Serializer to use to de/serialize tool calls and results.
 */
public class ResponseProcessorConfig(
    public val responseProcessor: ResponseProcessor,
    public val serializer: JSONSerializer,
)

/**
 * Executes the given prompt and processes responses using the given [processorConfig].
 */
public suspend fun PromptExecutor.executeProcessed(
    prompt: Prompt,
    model: LLModel,
    tools: List<ToolDescriptor>,
    processorConfig: ResponseProcessorConfig? = null,
): List<Message.Response> {
    val responses = execute(prompt, model, tools)

    return processorConfig
        ?.let { it.responseProcessor.process(this, prompt, model, tools, responses, it.serializer) }
        ?: responses
}
