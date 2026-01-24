package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * A processor for handling and modifying LLM responses.
 */
public abstract class ResponseProcessor() {

    /**
     * Processes the given LLM responses.
     * These responses were received using [executor], [prompt], [model], [tools].
     */
    public abstract suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>
    ): List<Message.Response>

    /**
     * Processes a single LLM response.
     */
    public suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        response: Message.Response
    ): Message.Response = process(
        executor,
        prompt,
        model,
        tools,
        listOf(response)
    ).first()

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
            responses: List<Message.Response>
        ): List<Message.Response> {
            var result = responses
            for (processor in processors) {
                result = processor.process(executor, prompt, model, tools, result)
            }
            return result
        }
    }

    /**
     * Chains two processors together.
     */
    public operator fun plus(other: ResponseProcessor): ResponseProcessor = Chain(this, other)
}
