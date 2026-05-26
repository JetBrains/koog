package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONSerializer

/**
 * A processor for handling and modifying LLM responses.
 */
public abstract class ResponseProcessor {

    /**
     * Processes a single LLM response.
     */
    public abstract suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        response: Message.Assistant,
        serializer: JSONSerializer,
    ): Message.Assistant

    /**
     * Chains multiple response processors together.
     */
    public class Chain(vararg processors: ResponseProcessor) : ResponseProcessor() {
        private val processors = processors.toList()

        override suspend fun process(
            executor: PromptExecutor,
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
            response: Message.Assistant,
            serializer: JSONSerializer,
        ): Message.Assistant {
            var result = response
            for (processor in processors) {
                result = processor.process(executor, prompt, model, tools, result, serializer)
            }
            return result
        }
    }

    /**
     * Chains two processors together.
     */
    public operator fun plus(other: ResponseProcessor): ResponseProcessor = Chain(this, other)
}
