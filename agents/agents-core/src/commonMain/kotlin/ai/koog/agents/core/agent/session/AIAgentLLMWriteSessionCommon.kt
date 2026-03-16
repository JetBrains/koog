@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * Common base implementation for mutable LLM sessions shared across platform-specific actual classes.
 */
public abstract class AIAgentLLMWriteSessionCommon internal constructor(
    @property:InternalAgentsApi public val environment: AIAgentEnvironment,
    private val executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    @property:InternalAgentsApi
    public val toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    public val config: AIAgentConfig,
    public val clock: Clock,
) : AutoCloseable {
    protected val readSession: AIAgentLLMReadSession
        get() = AIAgentLLMReadSession(
            tools = tools,
            executor = executor,
            prompt = prompt,
            model = model,
            responseProcessor = responseProcessor,
            config = config,
        )

    /**
     * Represents the current prompt associated with the LLM session.
     */
    public var prompt: Prompt by ActiveProperty(prompt) { isActive }

    /**
     * Provides a list of available tools in the session.
     */
    public var tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }

    /**
     * Represents the active language model used within the session.
     */
    public var model: LLModel by ActiveProperty(model) { isActive }

    /**
     * Represents the active response processor within the session.
     */
    public var responseProcessor: ResponseProcessor? by ActiveProperty(responseProcessor) { isActive }

    private var isActive: Boolean = true

    /**
     * Finds a specific tool instance from the tool registry by a tool instance type.
     */
    public fun <TArgs, TResult> findTool(tool: Tool<TArgs, TResult>): SafeTool<TArgs, TResult> {
        return findTool(tool::class)
    }

    /**
     * Finds a specific tool instance from the tool registry by tool class.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <TArgs, TResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult> {
        val tool = toolRegistry.tools.find(toolClass::isInstance) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool with type ${toolClass.simpleName} is not defined")

        return SafeTool(tool, environment, clock)
    }

    /**
     * Appends messages to the current prompt using [PromptBuilder].
     */
    public fun appendPrompt(body: PromptBuilder.() -> Unit) {
        prompt = prompt(prompt, clock, body)
    }

    /**
     * Updates the current prompt by applying modifications defined in the provided block.
     */
    @Deprecated("Use `appendPrompt` instead", ReplaceWith("appendPrompt(body)"))
    public fun updatePrompt(body: PromptBuilder.() -> Unit) {
        appendPrompt(body)
    }

    /**
     * Rewrites the current prompt by applying a transformation function.
     */
    public fun rewritePrompt(body: (prompt: Prompt) -> Prompt) {
        prompt = body(prompt)
    }

    /**
     * Updates the active model in this session.
     */
    public fun changeModel(newModel: LLModel) {
        model = newModel
    }

    /**
     * Updates LLM parameters on the current prompt.
     */
    public fun changeLLMParams(newParams: LLMParams): Unit = rewritePrompt {
        prompt.withParams(newParams)
    }

    /**
     * Sends a request without tool usage and appends all received responses to the prompt.
     */
    public suspend fun requestLLMMultipleWithoutTools(): List<Message.Response> {
        return readSession.requestLLMMultipleWithoutTools().also { responses ->
            appendPrompt { messages(responses) }
        }
    }

    /**
     * Sends a request without tool usage and appends the received response to the prompt.
     */
    public suspend fun requestLLMWithoutTools(): Message.Response {
        return readSession.requestLLMWithoutTools().also { response -> appendPrompt { message(response) } }
    }

    /**
     * Sends a request that enforces tool calling and appends the received response to the prompt.
     */
    public suspend fun requestLLMOnlyCallingTools(): Message.Response {
        return readSession.requestLLMOnlyCallingTools()
            .also { response -> appendPrompt { message(response) } }
    }

    /**
     * Sends a request that enforces tool calling and appends all received responses to the prompt.
     */
    public suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response> {
        return readSession.requestLLMMultipleOnlyCallingTools()
            .also { responses -> appendPrompt { messages(responses) } }
    }

    /**
     * Sends a request while forcing a specific tool and appends the response to the prompt.
     */
    public suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        return readSession.requestLLMForceOneTool(tool)
            .also { response -> appendPrompt { message(response) } }
    }

    /**
     * Sends a request while forcing a specific tool and appends the response to the prompt.
     */
    public suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return readSession.requestLLMForceOneTool(tool)
            .also { response -> appendPrompt { message(response) } }
    }

    /**
     * Sends a request to LLM and appends the response to the prompt.
     */
    public suspend fun requestLLM(): Message.Response {
        return readSession.requestLLM().also { response ->
            appendPrompt { message(response) }
        }
    }

    /**
     * Sends a streaming request to LLM.
     */
    public suspend fun requestLLMStreaming(): Flow<StreamFrame> {
        return readSession.requestLLMStreaming()
    }

    /**
     * Sends a moderation request using the specified moderating model or the session model.
     */
    public suspend fun requestModeration(moderatingModel: LLModel? = null): ModerationResult {
        return readSession.requestModeration(moderatingModel)
    }

    /**
     * Sends a request to LLM and appends all received responses to the prompt.
     */
    public suspend fun requestLLMMultiple(): List<Message.Response> {
        return readSession.requestLLMMultiple().also { responses ->
            appendPrompt {
                responses.forEach { message(it) }
            }
        }
    }

    /**
     * Sends a request to LLM and gets a structured response, appending the assistant message on success.
     */
    public suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return readSession.requestLLMStructured(config, fixingParser).also {
            it.onSuccess { response -> appendPrompt { message(response.message) } }
        }
    }

    /**
     * Requests a structured response from the language model using a reified serializer.
     */
    public suspend inline fun <reified T> requestLLMStructured(
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> = requestLLMStructured(
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser,
    )

    /**
     * Sends a request to LLM and gets a structured response, appending the assistant message on success.
     */
    public suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>> {
        return readSession.requestLLMStructured(serializer, examples, fixingParser).also {
            it.onSuccess { response -> appendPrompt { message(response.message) } }
        }
    }

    /**
     * Parses a structured response from an assistant message using the specified configuration.
     */
    public suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>,
        fixingParser: StructureFixingParser? = null
    ): StructuredResponse<T> {
        return readSession.parseResponseToStructuredResponse(response, config, fixingParser)
    }

    /**
     * Sends a request to LLM and returns all available response choices.
     */
    public suspend fun requestLLMMultipleChoices(): List<LLMChoice> {
        return readSession.requestLLMMultipleChoices()
    }

    /**
     * Streams a response from LLM, optionally adding a structure definition to the prompt beforehand.
     */
    public suspend fun requestLLMStreaming(definition: StructureDefinition? = null): Flow<StreamFrame> {
        if (definition != null) {
            val prompt = prompt(prompt, clock) {
                user {
                    definition.definition(this)
                }
            }
            this.prompt = prompt
        }

        return readSession.requestLLMStreaming()
    }

    /**
     * Converts each flow item into a parallel tool call using an already resolved [SafeTool].
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        flow { emit(safeTool.execute(args, config.serializer)) }
    }

    /**
     * Converts each flow item into a parallel tool call and emits only raw string content.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<String> = flatMapMerge(concurrency) { args ->
        flow { emit(safeTool.execute(args, config.serializer).content) }
    }

    /**
     * Converts each flow item into a parallel tool call using a tool instance.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        val safeTool = findTool(tool::class)
        flow { emit(safeTool.execute(args, config.serializer)) }
    }

    /**
     * Converts each flow item into a parallel tool call using a tool class.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> {
        val tool = findTool(toolClass)
        return toParallelToolCalls(tool, concurrency)
    }

    /**
     * Converts each flow item into a parallel tool call using a tool class and emits raw string content.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<String> {
        val tool = findTool(toolClass)
        return toParallelToolCallsRaw(tool, concurrency)
    }

    override fun close() {
        isActive = false
    }
}
