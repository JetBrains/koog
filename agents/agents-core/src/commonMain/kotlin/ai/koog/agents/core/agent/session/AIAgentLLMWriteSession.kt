@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * A session for managing interactions with a language learning model (LLM)
 * and tools in an agent environment. This class provides functionality for executing
 * LLM requests, managing tools, and customizing prompts dynamically within a specific
 * session context.
 *
 * @property environment The agent environment that provides the session with tool execution
 * and error handling capabilities.
 * @property toolRegistry The registry containing tools available for use within the session.
 * @property clock The clock used for message timestamps
 */
public expect class AIAgentLLMWriteSession internal constructor(
    delegate: AIAgentLLMWriteSessionImpl
) : AIAgentLLMWriteSessionAPI {

    /**
     * Creates an instance of `AIAgentLLMWriteSession` with the provided dependencies and configuration.
     *
     * @param environment The environment in which the AI agent operates, providing context or resources.
     * @param executor The `PromptExecutor` responsible for executing prompts and managing interactions.
     * @param tools A list of tool descriptors that define the behavior and capabilities of the tools available to the session.
     * @param toolRegistry The registry maintaining a collection of available tools for the session.
     * @param prompt The initial prompt that sets the context or goal for the session.
     * @param model The language model (`LLModel`) used by the session for generating responses and actions.
     * @param responseProcessor An optional processor for handling and transforming model responses, or null if not required.
     * @param config Configuration settings (`AIAgentConfig`) that define session-specific parameters and behavior.
     * @param clock The clock used to track time-related operations within the session.
     */
    public constructor(
        environment: AIAgentEnvironment,
        executor: PromptExecutor,
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        config: AIAgentConfig,
        clock: Clock
    )

    @PublishedApi
    internal val delegate: AIAgentLLMWriteSessionImpl

    @get:JvmName("environment")
    @InternalAgentsApi
    public override val environment: AIAgentEnvironment

    @get:JvmName("toolRegistry")
    @InternalAgentsApi
    public override val toolRegistry: ToolRegistry

    @get:JvmName("clock")
    public override val clock: Clock
    override val config: AIAgentConfig

    override var prompt: Prompt

    override var tools: List<ToolDescriptor>

    override var model: LLModel

    @InternalAgentsApi
    override var isActive: Boolean

    override var responseProcessor: ResponseProcessor?

    @InternalAgentsApi
    override fun validateSession()

    @InternalAgentsApi
    override fun preparePrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): Prompt

    @InternalAgentsApi
    override fun executeStreaming(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame>

    @InternalAgentsApi
    override suspend fun executeMultiple(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): List<Message.Response>

    @InternalAgentsApi
    override suspend fun executeSingle(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): Message.Response

    public override fun <TArgs, TResult> findTool(tool: Tool<TArgs, TResult>): SafeTool<TArgs, TResult>

    @Suppress("UNCHECKED_CAST")
    public override fun <TArgs, TResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult>

    public override fun appendPrompt(body: PromptBuilder.() -> Unit)

    @Deprecated("Use `appendPrompt` instead", ReplaceWith("appendPrompt(body)"))
    public override fun updatePrompt(body: PromptBuilder.() -> Unit)

    public override fun rewritePrompt(body: (prompt: Prompt) -> Prompt)

    public override fun changeModel(newModel: LLModel)

    public override fun changeLLMParams(newParams: LLMParams): Unit

    override suspend fun requestLLMMultipleWithoutTools(): List<Message.Response>

    override suspend fun requestLLMWithoutTools(): Message.Response

    override suspend fun requestLLMOnlyCallingTools(): Message.Response

    override suspend fun requestLLMMultipleOnlyCallingTools(): List<Message.Response>

    override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response

    override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response

    override suspend fun requestLLM(): Message.Response
    override suspend fun requestLLMStreaming(): Flow<StreamFrame>
    override suspend fun requestModeration(moderatingModel: LLModel?): ModerationResult

    override suspend fun requestLLMMultiple(): List<Message.Response>

    override suspend fun <T> requestLLMStructured(
        config: StructuredRequestConfig<T>,
    ): Result<StructuredResponse<T>>

    override suspend fun <T> requestLLMStructured(
        serializer: KSerializer<T>,
        examples: List<T>,
        fixingParser: StructureFixingParser?
    ): Result<StructuredResponse<T>>

    /**
     * Requests a structured response from the language model session, with optional examples for guidance
     * and a parser to fix structure-related issues in the response.
     *
     * @param T The type of the structured data expected in the response.
     * @param examples A list of examples to guide the language model in producing the desired structured response.
     *                 Defaults to an empty list if no examples are provided.
     * @param fixingParser An optional parser to validate and correct the structure of the language model's response.
     *                     Defaults to null if no fixing parser is provided.
     * @return A [Result] containing a [StructuredResponse] of type [T], which includes the structured data or error details.
     */
    public suspend inline fun <reified T> requestLLMStructured(
        examples: List<T> = emptyList(),
        fixingParser: StructureFixingParser? = null
    ): Result<StructuredResponse<T>>

    override suspend fun <T> parseResponseToStructuredResponse(
        response: Message.Assistant,
        config: StructuredRequestConfig<T>
    ): StructuredResponse<T>

    override suspend fun requestLLMMultipleChoices(): List<LLMChoice>

    final override fun close()

    public open override suspend fun requestLLMStreaming(definition: StructureDefinition?): Flow<StreamFrame>

    /**
     * Transforms a flow of arguments into a flow of results by asynchronously executing the given tool in parallel.
     *
     * @param TArgs the type of the arguments required by the tool.
     * @param TResult the type of the result produced by the tool, extending ToolResult.
     * @param safeTool the tool to be executed for each input argument.
     * @param concurrency the maximum number of parallel executions allowed. Defaults to 16.
     * @return a flow of results wrapped in SafeTool.Result for each input argument.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>>

    /**
     * Executes a flow of tool arguments in parallel by invoking the provided tool's raw execution method.
     * Converts each argument in the flow into a string result returned from the tool.
     *
     * @param safeTool The tool to execute, wrapped in a SafeTool to ensure safety during execution.
     * @param concurrency The maximum number of parallel calls to the tool. Default is 16.
     * @return A flow of string results derived from executing the tool's raw method.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<String>

    /**
     * Executes the given tool in parallel for each element in the flow of arguments, up to the specified level of concurrency.
     *
     * @param TArgs The type of arguments consumed by the tool.
     * @param TResult The type of result produced by the tool.
     * @param tool The tool instance to be executed in parallel.
     * @param concurrency The maximum number of concurrent executions. Default value is 16.
     * @return A flow emitting the results of the tool executions wrapped in a SafeTool.Result object.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>>

    /**
     * Transforms a Flow of tool argument objects into a Flow of parallel tool execution results, using the specified tool class.
     *
     * @param TArgs The type of the tool arguments that the Flow emits.
     * @param TResult The type of the results produced by the tool.
     * @param toolClass The class of the tool to be invoked in parallel for processing the arguments.
     * @param concurrency The maximum number of parallel executions allowed. Default is 16.
     * @return A Flow containing the results of the tool executions, wrapped in `SafeTool.Result`.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>>

    /**
     * Converts a flow of arguments into a flow of raw string results by executing the corresponding tool calls in parallel.
     *
     * @param TArgs the type of arguments required by the tool.
     * @param TResult the type of result produced by the tool.
     * @param toolClass the class of the tool to be invoked.
     * @param concurrency the number of concurrent tool calls to be executed. Defaults to 16.
     * @return a flow of raw string results from the parallel tool calls.
     */
    public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<String>
}

/**
 * Executes the specified tool with the given arguments and returns the result within a [SafeTool.Result] wrapper.
 *
 * @param TArgs the type of arguments required by the tool.
 * @param TResult the type of result returned by the tool, implementing `ToolResult`.
 * @param tool the tool to be executed.
 * @param args the arguments required to execute the tool.
 * @return a `SafeTool.Result` containing the tool's execution result of type `TResult`.
 */
public suspend inline fun <reified TArgs, reified TResult> AIAgentLLMWriteSession.callTool(
    tool: Tool<TArgs, TResult>,
    args: TArgs
): SafeTool.Result<TResult> {
    return findTool(tool::class).execute(args)
}

/**
 * Executes a tool by its name with the provided arguments.
 *
 * @param toolName The name of the tool to be executed.
 * @param args The arguments required to execute the tool.
 * @return A [SafeTool.Result] containing the result of the tool execution, which is a subtype of [ai.koog.agents.core.tools.ToolResult].
 */
public suspend inline fun <reified TArgs> AIAgentLLMWriteSession.callTool(
    toolName: String,
    args: TArgs
): SafeTool.Result<out Any?> {
    return findToolByName<TArgs>(toolName).execute(args)
}

/**
 * Executes a tool identified by its name with the provided arguments and returns the raw string result.
 *
 * @param toolName The name of the tool to be executed.
 * @param args The arguments to be passed to the tool.
 * @return The raw result of the tool's execution as a String.
 */
public suspend inline fun <reified TArgs> AIAgentLLMWriteSession.callToolRaw(
    toolName: String,
    args: TArgs
): String {
    return findToolByName<TArgs>(toolName).executeRaw(args)
}

/**
 * Executes a tool operation based on the provided tool class and arguments.
 *
 * @param TArgs The type of arguments required by the tool.
 * @param TResult The type of result produced by the tool.
 * @param toolClass The class of the tool to be executed.
 * @param args The arguments to be passed to the tool for its execution.
 * @return A result wrapper containing either the successful result of the tool's execution or an error.
 */
public suspend inline fun <reified TArgs, reified TResult> AIAgentLLMWriteSession.callTool(
    toolClass: KClass<out Tool<TArgs, TResult>>,
    args: TArgs
): SafeTool.Result<TResult> {
    val tool = findTool(toolClass)
    return tool.execute(args)
}

/**
 * Invokes a tool of the specified type with the provided arguments.
 *
 * @param args The input arguments required for the tool execution.
 * @return A `SafeTool.Result` containing the outcome of the tool's execution, which may be of any type that extends `ToolResult`.
 */
public suspend inline fun <reified ToolT : Tool<Any?, Any?>> AIAgentLLMWriteSession.callTool(
    args: Any?
): SafeTool.Result<out Any?> {
    val tool = findTool(ToolT::class)
    return tool.executeUnsafe(args)
}

/**
 * Finds and retrieves a tool by its name and argument/result types.
 *
 * This function looks for a tool in the tool registry by its name and ensures that the tool
 * is compatible with the specified argument and result types. If no matching tool is found,
 * or if the specified types are incompatible, an exception is thrown.
 *
 * @param toolName the name of the tool to retrieve
 * @return the tool that matches the specified name and types
 * @throws IllegalArgumentException if the tool is not defined or the types are incompatible
 */
public inline fun <reified TArgs, reified TResult> AIAgentLLMWriteSession.findToolByNameAndArgs(
    toolName: String
): Tool<TArgs, TResult> =
    @Suppress("UNCHECKED_CAST")
    (
        toolRegistry.getTool(toolName) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments")
        )

/**
 * Finds a tool by its name and ensures its arguments are compatible with the specified type.
 *
 * @param toolName The name of the tool to be retrieved.
 * @return A SafeTool instance wrapping the tool with the specified argument type.
 * @throws IllegalArgumentException If the tool with the specified name is not defined or its arguments
 * are incompatible with the expected type.
 */
public inline fun <reified TArgs> AIAgentLLMWriteSession.findToolByName(toolName: String): SafeTool<TArgs, *> {
    @Suppress("UNCHECKED_CAST")
    val tool = (
        toolRegistry.getTool(toolName) as? Tool<TArgs, *>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments")
        )

    return SafeTool(tool, environment, clock)
}
