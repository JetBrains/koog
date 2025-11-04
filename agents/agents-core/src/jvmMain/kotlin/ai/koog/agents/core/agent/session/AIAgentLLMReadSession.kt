@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.session

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import java.util.concurrent.ExecutorService

public actual class AIAgentLLMReadSession actual constructor(
    tools: List<ToolDescriptor>,
    executor: PromptExecutor,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfig,
) : AIAgentLLMSession by AIAgentLLMSessionImpl(executor, tools, prompt, model, config) {

    /**
     * Executes multiple tasks or requests associated with the given `Prompt` and `ToolDescriptor` list.
     *
     * This method allows parallel execution of provided tools based on the given prompt,
     * utilizing an optional `ExecutorService` for concurrency management.
     *
     * @param prompt the prompt to be used for the task execution
     * @param tools the list of tools to be executed in conjunction with the prompt
     * @param executorService an optional executor service for managing parallel execution;
     *        if null, the default dispatcher is used
     * @return a list of `Message.Response` objects representing the results of the executed tasks
     */
    @JavaAPI
    @JvmOverloads
    public fun executeMultiple(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        executorService: ExecutorService? = null
    ): List<Message.Response> = config.runOnStrategyDispatcher(executorService) {
        executeMultiple(prompt, tools)
    }


    /**
     * Executes a single task or request associated with the given `Prompt` and `ToolDescriptor` list.
     *
     * This method processes the provided tools based on the given prompt, utilizing an optional
     * `ExecutorService` for managing execution context or concurrency.
     *
     * @param prompt the prompt to be used for the task execution
     * @param tools the list of tools to be executed in conjunction with the prompt
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a `Message.Response` object representing the result of the executed task
     */
    @JavaAPI
    @JvmOverloads
    public fun executeSingle(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
        executorService: ExecutorService? = null
    ): Message.Response =
        config.runOnStrategyDispatcher(executorService) {
            executeSingle(prompt, tools)
        }


    /**
     * Sends a request to the language model without utilizing any tools and returns multiple responses.
     *
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a list of response messages from the language model
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMMultipleWithoutTools(
        executorService: ExecutorService? = null
    ): List<Message.Response> = config.runOnStrategyDispatcher(executorService) {
        requestLLMMultipleWithoutTools()
    }

    /**
     * Sends a request to the language model without utilizing any tools and returns a single response.
     *
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a response message from the language model
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMWithoutTools(
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnStrategyDispatcher(executorService) {
        requestLLMWithoutTools()
    }

    /**
     * Sends a request to the language model that is allowed to only perform tool calls
     * without generating a regular text response.
     *
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return the response containing tool calls from the language model
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMOnlyCallingTools(
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnStrategyDispatcher(executorService) {
        requestLLMOnlyCallingTools()
    }

    /**
     * Sends a request to the language model and forces it to use exactly one specific tool,
     * identified by a [ToolDescriptor].
     *
     * @param tool the tool descriptor that the language model must use
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return the response from the language model containing the forced tool call
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMForceOneTool(
        tool: ToolDescriptor,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnStrategyDispatcher(executorService) {
        requestLLMForceOneTool(tool)
    }

    /**
     * Sends a request to the language model and forces it to use exactly one specific tool instance.
     *
     * @param tool the tool instance that the language model must use
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return the response from the language model containing the forced tool call
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMForceOneTool(
        tool: Tool<*, *>,
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnStrategyDispatcher(executorService) {
        requestLLMForceOneTool(tool)
    }


    /**
     * Sends a request to the language model using the current session configuration
     * and returns a single response.
     *
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return the response message from the language model
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLM(
        executorService: ExecutorService? = null
    ): Message.Response = config.runOnStrategyDispatcher(executorService) {
        requestLLM()
    }


    /**
     * Sends a request to the language model and returns a streaming response as a [Flow] of [StreamFrame].
     *
     * Note: the returned [Flow] must be collected from a coroutine context by the caller.
     *
     * @param executorService an optional executor service used to start the streaming coroutine;
     *        if null, the default dispatcher is used
     * @return a flow of streaming frames from the language model
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMStreaming(
        executorService: ExecutorService? = null
    ): Flow<StreamFrame> = config.runOnStrategyDispatcher(executorService) {
        requestLLMStreaming()
    }

    /**
     * Sends a moderation request to the moderation model.
     *
     * @param moderatingModel an optional model to be used for moderation; if null, the default model is used
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return the moderation result
     */
    @JavaAPI
    @JvmOverloads
    public fun requestModeration(
        moderatingModel: LLModel? = null,
        executorService: ExecutorService? = null
    ): ModerationResult = config.runOnStrategyDispatcher(executorService) {
        requestModeration(moderatingModel)
    }

    /**
     * Sends a request to the language model and returns multiple responses.
     *
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a list of response messages from the language model
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMMultiple(
        executorService: ExecutorService? = null
    ): List<Message.Response> = config.runOnStrategyDispatcher(executorService) {
        requestLLMMultiple()
    }

    /**
     * Sends a structured request to the language model using a [StructuredRequestConfig].
     *
     * @param config the configuration describing the expected structured output and parsing behavior
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a [Result] containing a [StructuredResponse] on success or an error on failure
     */
    @JavaAPI
    @JvmOverloads
    public fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
        executorService: ExecutorService? = null
    ): Result<StructuredResponse<T>> = this.config.runOnStrategyDispatcher(executorService) {
        requestLLMStructured(config)
    }

    /**
     * Sends a structured request to the language model using an explicit serializer and example values.
     *
     * @param serializer the serializer describing how to encode/decode the structured type [T]
     * @param examples example values to guide the model towards the expected structure
     * @param fixingParser an optional parser used to repair malformed structured responses
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a [Result] containing a [StructuredResponse] on success or an error on failure
     */
    @JavaAPI
    @JvmOverloads
    public fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null,
        executorService: ExecutorService? = null
    ): Result<StructuredResponse<T>> = config.runOnStrategyDispatcher(executorService) {
        requestLLMStructured(serializer, examples, fixingParser)
    }

    /**
     * Parses an assistant response into a strongly typed [StructuredResponse] according to the given configuration.
     *
     * @param response the assistant message to parse
     * @param config the structured request configuration describing the expected output
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return the parsed structured response
     */
    @JavaAPI
    @JvmOverloads
    public fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>,
        executorService: ExecutorService? = null
    ): StructuredResponse<T> = this.config.runOnStrategyDispatcher(executorService) {
        parseResponseToStructuredResponse(response, config)
    }

    /**
     * Sends a request to the language model and returns multiple choice alternatives.
     *
     * @param executorService an optional executor service for managing the execution context;
     *        if null, the default dispatcher is used
     * @return a list of [LLMChoice] instances representing alternative completions
     */
    @JavaAPI
    @JvmOverloads
    public fun requestLLMMultipleChoices(
        executorService: ExecutorService? = null
    ): List<LLMChoice> = config.runOnStrategyDispatcher(executorService) {
        requestLLMMultipleChoices()
    }
}
