@file:OptIn(InternalAgentsApi::class)

package ai.grazie.code.agents.core.agent.entity.stage

import ai.grazie.code.agents.core.agent.entity.LocalAgentStateManager
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorage
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorageKey
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.environment.SafeTool
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.KotlinAIAgentFeature
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.utils.ActiveProperty
import ai.grazie.code.agents.core.utils.RWLock
import ai.grazie.code.prompt.structure.*
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

/**
 * The `LocalAgentStageContext` interface represents the context of a stage in the lifecycle of a local agent.
 * It provides access to the environment, configuration, LLM context, state management, storage, and other
 * metadata necessary for the operation of the agent stage. Additionally, it supports features for custom
 * workflows and extensibility.
 */
interface LocalAgentStageContext {
    /**
     * Represents the environment in which the agent operates.
     *
     * This variable provides access to essential functionalities for the agent's execution,
     * including interaction with tools, error reporting, and sending termination signals.
     * It is used throughout the agent's lifecycle to facilitate actions and handle outcomes.
     */
    val environment: AgentEnvironment

    /**
     * Represents the input provided to the current stage in the agent's execution context.
     *
     * This value is used to dynamically update the prompt for the large language model (LLM)
     * and influence the behavior or response of the agent in the current stage. It is typically
     * set or modified as part of the execution flow to provide context or additional information
     * relevant to the current stage of processing.
     */
    val stageInput: String

    /**
     * Represents the configuration for a local agent within the current stage context.
     *
     * This configuration is utilized during the execution of the stage to enforce constraints
     * such as the maximum number of iterations an agent can perform, as well as providing
     * the agent's prompt configuration.
     */
    val config: LocalAgentConfig

    /**
     * Represents the local agent's LLM context within the stage, providing mechanisms for managing tools, prompts,
     * and interaction with the execution environment. It ensures thread safety during concurrent read and write
     * operations through the use of sessions.
     *
     * This context plays a foundational role in defining and manipulating tools, prompt execution, and overall
     * behavior during different stages of the agent's lifecycle.
     */
    val llm: LocalAgentLLMContext

    /**
     * Manages and tracks the state of a local agent within the context of its execution.
     *
     * This variable provides synchronized access to the agent's state to ensure thread safety
     * and consistent state transitions during concurrent operations. It acts as a central
     * mechanism for managing state updates and validations across different stages
     * or nodes of the local agent's execution flow.
     *
     * The `stateManager` is utilized extensively in coordinating state changes, such as
     * tracking the number of iterations made by the agent and enforcing execution limits
     * or conditions. This aids in maintaining predictable and controlled behavior
     * of the agent during execution.
     */
    val stateManager: LocalAgentStateManager

    /**
     * Concurrent-safe key-value storage for an agent, used to manage and persist data within the context of
     * a local agent stage execution. The `storage` property provides a thread-safe mechanism for sharing
     * and storing data specific to the agent's operation.
     */
    val storage: LocalAgentStorage

    /**
     * A unique identifier for the current session associated with the local agent stage context.
     * Used to track and differentiate sessions within the execution of the agent pipeline.
     */
    val sessionUuid: UUID

    /**
     * Represents the unique identifier for the strategy being used in the current local agent stage context.
     *
     * This identifier allows the system to specify and reference a particular strategy
     * employed during the execution pipeline of an AI agent and its stages. It can be used
     * for logging, debugging, and switching between different strategies dynamically.
     */
    val strategyId: String

    /**
     * Represents the name of the current execution stage in the local agent's context.
     *
     * This property is used to identify and track the specific stage of processing within
     * the execution pipeline, aiding in debugging, logging, and ensuring proper flow
     * control during the agent's tasks.
     */
    val stageName: String

    /**
     * Represents the AI agent pipeline used within a `LocalAgentStageContext`.
     *
     * This pipeline organizes and processes the sequence of operations or stages required
     * for the execution of an AI agent's tasks.
     *
     * Note: This is an internal API and should not be used directly outside of the intended
     * implementation context. It is annotated with `@InternalAgentsApi` to indicate that
     * it is subject to changes or alterations in future releases.
     */
    @InternalAgentsApi
    val pipeline: AIAgentPipeline

    /**
     * Retrieves a feature from the local agent's storage using the specified key.
     *
     * @param key A uniquely identifying key of type `LocalAgentStorageKey` used to fetch the corresponding feature.
     * @return The feature associated with the provided key, or null if no matching feature is found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <Feature : Any> feature(key: LocalAgentStorageKey<Feature>): Feature?

    /**
     * Retrieves a feature of the specified type from the current context.
     *
     * @param feature The [KotlinAIAgentFeature] instance representing the feature to retrieve.
     *                This parameter defines the configuration and unique identity of the feature.
     * @return The feature instance of type [Feature], or null if the feature is not available in the context.
     */
    fun <Feature : Any> feature(feature: KotlinAIAgentFeature<*, Feature>): Feature?

    /**
     * Creates a new instance of `LocalAgentStageContext` with updated tools, while preserving the other properties
     * of the original context.
     *
     * @param tools The new list of `ToolDescriptor` instances to be set in the context.
     * @return A new `LocalAgentStageContext` instance with the specified tools.
     */
    @InternalAgentsApi
    fun copyWithTools(tools: List<ToolDescriptor>): LocalAgentStageContext {
        return this.copy(llm = llm.copy(tools = tools))
    }

    /**
     * Creates a copy of the current `LocalAgentStageContext` with optional overrides for its properties.
     *
     * @param environment The agent environment to be used, or null to retain the current environment.
     * @param stageInput The input for the stage, or null to retain the current stage input.
     * @param config The local agent configuration, or null to retain the current configuration.
     * @param llm The local agent LLM context, or null to retain the current LLM context.
     * @param stateManager The state manager for the local agent, or null to retain the current state manager.
     * @param storage The local agent's key-value storage, or null to retain the current storage.
     * @param sessionUuid The UUID of the session, or null to retain the current session UUID.
     * @param strategyId The strategy ID, or null to retain the current strategy ID.
     * @param stageName The name of the stage, or null to retain the current stage name.
     * @param pipeline The AI agent pipeline, or null to retain the current pipeline.
     * @return A new instance of `LocalAgentStageContext` with the specified overrides.
     */
    fun copy(
        environment: AgentEnvironment? = null,
        stageInput: String? = null,
        config: LocalAgentConfig? = null,
        llm: LocalAgentLLMContext? = null,
        stateManager: LocalAgentStateManager? = null,
        storage: LocalAgentStorage? = null,
        sessionUuid: UUID? = null,
        strategyId: String? = null,
        stageName: String? = null,
        pipeline: AIAgentPipeline? = null,
    ): LocalAgentStageContext
}

/**
 * Implements the `LocalAgentStageContext` interface, providing the context required for a local
 * agent's stage execution. This class encapsulates configurations, the execution pipeline,
 * agent environment, and tools for handling agent lifecycles and interactions.
 *
 * @constructor Creates an instance of the context with the given parameters.
 *
 * @param environment The agent environment responsible for tool execution and problem reporting.
 * @param stageInput The input provided to the current stage.
 * @param config The configuration settings of the local agent.
 * @param llm The contextual data and execution utilities for the local agent's interaction with LLMs.
 * @param stateManager Manages the internal state of the local agent.
 * @param storage Concurrent-safe storage for managing key-value data across the agent's lifecycle.
 * @param sessionUuid The unique identifier for the agent session.
 * @param strategyId The identifier for the selected strategy in the agent's lifecycle.
 * @param stageName The name of the stage associated with this context.
 * @param pipeline The AI agent pipeline responsible for coordinating stage execution and processing.
 */
class LocalAgentStageContextImpl constructor(
    override val environment: AgentEnvironment,
    override val stageInput: String,
    override val config: LocalAgentConfig,
    override val llm: LocalAgentLLMContext,
    override val stateManager: LocalAgentStateManager,
    override val storage: LocalAgentStorage,
    override val sessionUuid: UUID,
    override val strategyId: String,
    override val stageName: String,
    override val pipeline: AIAgentPipeline,
) : LocalAgentStageContext {
    /**
     * A map storing features associated with the current stage context.
     * The keys represent unique identifiers for specific features, defined as `LocalAgentStorageKey`.
     * The values are the features themselves, which can be of any type.
     *
     * This map is populated by invoking the `getStageFeatures` method, retrieving features
     * based on the handlers registered for the stage's execution context.
     *
     * Used internally to manage and access features during the execution of a stage within the agent pipeline.
     */
    private val features: Map<LocalAgentStorageKey<*>, Any> =
        pipeline.getStageFeatures(this)

    /**
     * Retrieves a feature associated with the given key from the local agent storage.
     *
     * @param key The key of the feature to retrieve.
     * @return The feature associated with the specified key, or null if no such feature exists.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <Feature : Any> feature(key: LocalAgentStorageKey<Feature>): Feature? = features[key] as Feature?

    /**
     * Retrieves an instance of the specified feature from the local agent's storage.
     *
     * @param feature The feature representation, including its key and configuration details,
     *                for identifying and accessing the associated implementation.
     * @return The feature implementation of the specified type if available, or null if it is not present.
     */
    override fun <Feature : Any> feature(feature: KotlinAIAgentFeature<*, Feature>): Feature? = feature(feature.key)

    /**
     * Creates a new instance of `LocalAgentStageContext` with an updated list of tools, replacing the current tools
     * in the LLM context with the provided list.
     *
     * @param tools The new list of tools to be used in the LLM context, represented as `ToolDescriptor` objects.
     * @return A new instance of `LocalAgentStageContext` with the updated tools configuration.
     */
    override fun copyWithTools(tools: List<ToolDescriptor>): LocalAgentStageContext {
        return this.copy(llm = llm.copy(tools = tools))
    }

    /**
     * Creates a copy of the current `LocalAgentStageContextImpl`, allowing for selective overriding of its properties.
     *
     * @param environment The `AgentEnvironment` to be used in the new context, or `null` to retain the current one.
     * @param stageInput The input for the stage, or `null` to retain the current value.
     * @param config The `LocalAgentConfig` for the new context, or `null` to retain the current configuration.
     * @param llm The `LocalAgentLLMContext` to be used, or `null` to retain the current LLM context.
     * @param stateManager The `LocalAgentStateManager` to be used, or `null` to retain the current state manager.
     * @param storage The `LocalAgentStorage` to be used, or `null` to retain the current storage.
     * @param sessionUuid The session UUID, or `null` to retain the current session ID.
     * @param strategyId The strategy identifier, or `null` to retain the current identifier.
     * @param stageName The name of the stage, or `null` to retain the current stage name.
     * @param pipeline The `AIAgentPipeline` to be used, or `null` to retain the current pipeline.
     */
    override fun copy(
        environment: AgentEnvironment?,
        stageInput: String?,
        config: LocalAgentConfig?,
        llm: LocalAgentLLMContext?,
        stateManager: LocalAgentStateManager?,
        storage: LocalAgentStorage?,
        sessionUuid: UUID?,
        strategyId: String?,
        stageName: String?,
        pipeline: AIAgentPipeline?,
    ) = LocalAgentStageContextImpl(
        environment = environment ?: this.environment,
        stageInput = stageInput ?: this.stageInput,
        config = config ?: this.config,
        llm = llm ?: this.llm,
        stateManager = stateManager ?: this.stateManager,
        storage = storage ?: this.storage,
        sessionUuid = sessionUuid ?: this.sessionUuid,
        strategyId = strategyId ?: this.strategyId,
        stageName = stageName ?: this.stageName,
        pipeline = pipeline ?: this.pipeline,
    )
}

/**
 * Represents the context for a local agent LLM, managing tools, prompt handling, and interaction with the
 * environment and execution layers. It provides mechanisms for concurrent read and write operations
 * through sessions, ensuring thread safety.
 *
 * @property tools A list of tool descriptors available for the context.
 * @property toolRegistry A registry that contains metadata about tools and their organization across stages.
 * @property prompt The current LLM prompt being used or updated in write sessions.
 * @property model The current LLM model being used or updated in write sessions.
 * @property promptExecutor The executor responsible for performing operations based on the current prompt.
 * @property environment The environment that manages tool execution and interaction with external dependencies.
 */
data class LocalAgentLLMContext(
    internal var tools: List<ToolDescriptor>,
    val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private var prompt: Prompt,
    private var model: LLModel,
    internal val promptExecutor: PromptExecutor,
    private val environment: AgentEnvironment,
    private val config: LocalAgentConfig,
) {

    private val rwLock = RWLock()

    /**
     * Executes a write session on the LocalAgentLLMContext, ensuring that all active write and read sessions are completed
     * before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <T> writeSession(block: suspend LocalAgentLLMWriteSession.() -> T): T = rwLock.withWriteLock {
        val session = LocalAgentLLMWriteSession(environment, promptExecutor, tools, toolRegistry, prompt, model, config)

        session.use {
            val result = it.block()

            // update tools and prompt after session execution
            this.prompt = it.prompt
            this.tools = it.tools

            result
        }
    }

    /**
     * Executes a read session within the LocalAgentLLMContext, ensuring concurrent safety
     * with active write session and other read sessions.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <T> readSession(block: suspend LocalAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = LocalAgentLLMReadSession(tools, promptExecutor, prompt, model, config)

        session.use { block(it) }
    }

}

/**
 * Represents a session for a local agent that interacts with an LLM (Language Learning Model).
 * The session manages prompt execution, structured outputs, and tools integration.
 *
 * This is a sealed class that provides common behavior and lifecycle management for derived types.
 * It ensures that operations are only performed while the session is active and allows proper cleanup upon closure.
 *
 * @property executor The executor responsible for executing prompts and handling LLM interactions.
 * @constructor Creates an instance of a LocalAgentLLMSession with an executor, a list of tools, and a prompt.
 */
@OptIn(ExperimentalStdlibApi::class)
sealed class LocalAgentLLMSession(
    protected val executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    prompt: Prompt,
    model: LLModel,
    protected val config: LocalAgentConfig,
) : AutoCloseable {
    /**
     * Represents the current prompt associated with the local LLM session.
     * The prompt captures the input messages, model configuration, and parameters
     * used for interactions with the underlying language model.
     *
     * The property is managed using an active state validation mechanism, which ensures
     * that the prompt can only be accessed or modified when the session is active.
     *
     * Delegated by [ActiveProperty] to enforce session-based activity checks,
     * ensuring the property cannot be accessed when the `isActive` predicate evaluates to false.
     *
     * Typical usage includes providing input to LLM requests, such as:
     * - [requestLLMWithoutTools]
     * - [requestLLM]
     * - [requestLLMMultiple]
     * - [requestLLMStructured]
     * - [requestLLMStructuredOneShot]
     */
    open val prompt: Prompt by ActiveProperty(prompt) { isActive }

    /**
     * Provides a list of tools based on the current active state.
     *
     * This property holds a collection of `ToolDescriptor` instances, which describe the tools available
     * for use in the local agent session. The tools are dynamically determined and validated based on the
     * `isActive` state of the session. The property ensures that tools can only be accessed when the session
     * is active, leveraging the `ActiveProperty` delegate for state validation.
     *
     * Accessing this property when the session is inactive will raise an exception, ensuring consistency
     * and preventing misuse of tools outside a valid context.
     */
    open val tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }


    /**
     * Represents the active language model used within the session.
     *
     * This property is backed by a delegate that ensures it can only be accessed
     * while the session is active, as determined by the `isActive` property.
     *
     * The model defines the language generation capabilities available for executing prompts
     * and tool interactions within the session's context.
     *
     * Usage of this property when the session is inactive will result in an exception.
     */
    open val model: LLModel by ActiveProperty(model) { isActive }

    /**
     * A flag indicating whether the session is currently active.
     *
     * This variable is used to ensure that the session operations are only performed when the session is active.
     * Once the session is closed, this flag is set to `false` to prevent further usage.
     */
    protected var isActive = true

    /**
     * Ensures that the session is active before allowing further operations.
     *
     * This method validates the state of the session using the `isActive` property
     * and throws an exception if the session has been closed. It is primarily intended
     * to prevent operations on an inactive or closed session, ensuring safe and valid usage.
     *
     * Throws:
     * - `IllegalStateException` if the session is not active.
     */
    protected fun validateSession() {
        check(isActive) { "Cannot use session after it was closed" }
    }

    protected fun preparePrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
        return config.missingToolsConversionStrategy.convertPrompt(prompt, tools)
    }

    protected suspend fun executeMultiple(prompt: Prompt, tools: List<ToolDescriptor>): List<Message.Response> {
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.execute(preparedPrompt, model, tools)
    }

    protected suspend fun executeSingle(prompt: Prompt, tools: List<ToolDescriptor>): Message.Response =
        executeMultiple(prompt, tools).first()


    open suspend fun requestLLMWithoutTools(): Message.Response {
        validateSession()
        val promptWithDisabledTools = prompt.withUpdatedParams { toolChoice = null }
        return executeSingle(promptWithDisabledTools, emptyList())
    }

    open suspend fun requestLLMOnlyCallingTools(): Message.Response {
        validateSession()
        val promptWithOnlyCallingTools = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Required
        }
        return executeSingle(promptWithOnlyCallingTools, tools)
    }

    open suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        validateSession()
        check(tools.contains(tool)) { "Unable to force call to tool `${tool.name}` because it is not defined" }
        val promptWithForcingOneTool = prompt.withUpdatedParams {
            toolChoice = LLMParams.ToolChoice.Named(tool.name)
        }
        return executeSingle(promptWithForcingOneTool, tools)
    }

    open suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return requestLLMForceOneTool(tool.descriptor)
    }


    /**
     * Sends a request to the underlying LLM and returns the first response.
     * This method ensures the session is active before executing the request.
     *
     * @return The first response message from the LLM after executing the request.
     */
    open suspend fun requestLLM(): Message.Response {
        validateSession()
        return executeSingle(prompt, tools)
    }

    /**
     * Sends a request to the language model, potentially utilizing multiple tools,
     * and returns a list of responses from the model.
     *
     * Before executing the request, the session state is validated to ensure
     * it is active and usable.
     *
     * @return a list of responses from the language model
     */
    open suspend fun requestLLMMultiple(): List<Message.Response> {
        validateSession()
        return executeMultiple(prompt, tools)
    }

    /**
     * Coerce LLM to provide a structured output.
     *
     * @see [executeStructured]
     */
    open suspend fun <T> requestLLMStructured(
        structure: StructuredData<T>,
        retries: Int = 1,
        fixingModel: LLModel = OpenAIModels.GPT4o
    ): StructuredResponse<T> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeStructured(preparedPrompt, model, structure, retries, fixingModel)
    }

    /**
     * Expect LLM to reply in a structured format and try to parse it.
     * For more robust version with model coercion and correction see [requestLLMStructured]
     *
     * @see [executeStructuredOneShot]
     */
    open suspend fun <T> requestLLMStructuredOneShot(structure: StructuredData<T>): StructuredResponse<T> {
        validateSession()
        val preparedPrompt = preparePrompt(prompt, tools)
        return executor.executeStructuredOneShot(preparedPrompt, model, structure)
    }

    final override fun close() {
        isActive = false
    }
}

/**
 * A session for locally managing interactions with a language learning model (LLM)
 * and tools in an agent environment. This class provides functionality for executing
 * LLM requests, managing tools, and customizing prompts dynamically within a specific
 * session context.
 *
 * @property environment The agent environment that provides the session with tool execution
 * and error handling capabilities.
 * @property toolRegistry The registry containing tools available for use within the session.
 */
@Suppress("unused")
class LocalAgentLLMWriteSession internal constructor(
    val environment: AgentEnvironment,
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    val toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    config: LocalAgentConfig,
) : LocalAgentLLMSession(executor, tools, prompt, model, config) {
    /**
     * Represents the prompt object used within the session. The prompt can be accessed or
     * modified only when the session is in an active state, as determined by the `isActive` predicate.
     *
     * This property uses the `ActiveProperty` delegate to enforce the validation of the session's
     * active state before any read or write operations.
     */
    override var prompt: Prompt by ActiveProperty(prompt) { isActive }

    /**
     * Represents a collection of tools that are available for the session.
     * The tools can be accessed or modified only if the session is in an active state.
     *
     * This property uses an `ActiveProperty` delegate to enforce the session's active state
     * as a prerequisite for accessing or mutating the tools list.
     *
     * The list contains tool descriptors, which define the tools' metadata, such as their
     * names, descriptions, and parameter requirements.
     */
    override var tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }


    /**
     * Represents an override property `model` of type `LLModel`.
     * This property is backed by an `ActiveProperty`, which ensures the property value is dynamically updated
     * based on the active state determined by the `isActive` parameter.
     *
     * This implementation allows for reactive behavior, ensuring that the `model` value is updated or resolved
     * only when the `isActive` condition changes.
     */
    override var model: LLModel by ActiveProperty(model) { isActive }

    /**
     * Executes the specified tool with the given arguments and returns the result within a `SafeTool.Result` wrapper.
     *
     * @param TArgs the type of arguments required by the tool, extending `Tool.Args`.
     * @param TResult the type of result returned by the tool, implementing `ToolResult`.
     * @param tool the tool to be executed.
     * @param args the arguments required to execute the tool.
     * @return a `SafeTool.Result` containing the tool's execution result of type `TResult`.
     */
    suspend inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> callTool(
        tool: Tool<TArgs, TResult>,
        args: TArgs
    ): SafeTool.Result<TResult> {
        return findTool(tool::class).execute(args)
    }

    /**
     * Executes a tool by its name with the provided arguments.
     *
     * @param toolName The name of the tool to be executed.
     * @param args The arguments required to execute the tool, which must be a subtype of [Tool.Args].
     * @return A [SafeTool.Result] containing the result of the tool execution, which is a subtype of [ToolResult].
     */
    suspend inline fun <reified TArgs : Tool.Args> callTool(
        toolName: String,
        args: TArgs
    ): SafeTool.Result<out ToolResult> {
        return findToolByName<TArgs>(toolName).execute(args)
    }

    /**
     * Executes a tool identified by its name with the provided arguments and returns the raw string result.
     *
     * @param toolName The name of the tool to be executed.
     * @param args The arguments to be passed to the tool, conforming to the [Tool.Args] type.
     * @return The raw result of the tool's execution as a String.
     */
    suspend inline fun <reified TArgs : Tool.Args> callToolRaw(
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
    suspend inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> callTool(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        args: TArgs
    ): SafeTool.Result<TResult> {
        val tool = findTool(toolClass)
        return tool.execute(args)
    }

    /**
     * Finds and retrieves a tool of the specified type from the tool registry.
     *
     * @param TArgs The type of arguments the tool accepts, extending from Tool.Args.
     * @param TResult The type of result the tool produces, extending from ToolResult.
     * @param toolClass The KClass reference that specifies the type of tool to find.
     * @return A SafeTool instance wrapping the found tool and its environment.
     * @throws IllegalArgumentException if the specified tool is not found in the tool registry.
     */
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult> {
        @Suppress("UNCHECKED_CAST")
        val tool = (toolRegistry.stages.first().tools.find(toolClass::isInstance) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool with type ${toolClass.simpleName} is not defined"))

        return SafeTool(tool, environment)
    }

    /**
     * Invokes a tool of the specified type with the provided arguments.
     *
     * @param args The input arguments required for the tool execution, represented as an instance of `Tool.Args`.
     * @return A `SafeTool.Result` containing the outcome of the tool's execution, which may be of any type that extends `ToolResult`.
     */
    suspend inline fun <reified ToolT : Tool<*, *>> callTool(
        args: Tool.Args
    ): SafeTool.Result<out ToolResult> {
        val tool = findTool<ToolT>()
        return tool.executeUnsafe(args)
    }

    /**
     * Finds and retrieves a tool of the specified type from the current stage of the tool registry.
     * If no tool of the given type is found, an exception is thrown.
     *
     * @return An instance of SafeTool wrapping the tool of the specified type and the current environment.
     * @throws IllegalArgumentException if a tool of the given type is not defined in the tool registry.
     */
    inline fun <reified ToolT : Tool<*, *>> findTool(): SafeTool<*, *> {
        val tool = toolRegistry.stages.first().tools.find(ToolT::class::isInstance) as? ToolT
            ?: throw IllegalArgumentException("Tool with type ${ToolT::class.simpleName} is not defined")

        return SafeTool(tool, environment)
    }

    /**
     * Transforms a flow of arguments into a flow of results by asynchronously executing the given tool in parallel.
     *
     * @param TArgs the type of the arguments required by the tool, extending Tool.Args.
     * @param TResult the type of the result produced by the tool, extending ToolResult.
     * @param safeTool the tool to be executed for each input argument.
     * @param concurrency the maximum number of parallel executions allowed. Defaults to 16.
     * @return a flow of results wrapped in SafeTool.Result for each input argument.
     */
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.execute(args))
        }
    }

    /**
     * Executes a flow of tool arguments in parallel by invoking the provided tool's raw execution method.
     * Converts each argument in the flow into a string result returned from the tool.
     *
     * @param safeTool The tool to execute, wrapped in a SafeTool to ensure safety during execution.
     * @param concurrency The maximum number of parallel calls to the tool. Default is 16.
     * @return A flow of string results derived from executing the tool's raw method.
     */
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<String> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.executeRaw(args))
        }
    }

    /**
     * Executes the given tool in parallel for each element in the flow of arguments, up to the specified level of concurrency.
     *
     * @param TArgs The type of arguments consumed by the tool.
     * @param TResult The type of result produced by the tool.
     * @param tool The tool instance to be executed in parallel.
     * @param concurrency The maximum number of concurrent executions. Default value is 16.
     * @return A flow emitting the results of the tool executions wrapped in a SafeTool.Result object.
     */
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        val safeTool = findTool(tool::class)
        flow {
            emit(safeTool.execute(args))
        }
    }

    /**
     * Transforms a Flow of tool argument objects into a Flow of parallel tool execution results, using the specified tool class.
     *
     * @param TArgs The type of the tool arguments that the Flow emits.
     * @param TResult The type of the results produced by the tool.
     * @param toolClass The class of the tool to be invoked in parallel for processing the arguments.
     * @param concurrency The maximum number of parallel executions allowed. Default is 16.
     * @return A Flow containing the results of the tool executions, wrapped in `SafeTool.Result`.
     */
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> {
        val tool = findTool(toolClass)
        return toParallelToolCalls(tool, concurrency)
    }

    /**
     * Converts a flow of arguments into a flow of raw string results by executing the corresponding tool calls in parallel.
     *
     * @param TArgs the type of arguments required by the tool.
     * @param TResult the type of result produced by the tool.
     * @param toolClass the class of the tool to be invoked.
     * @param concurrency the number of concurrent tool calls to be executed. Defaults to 16.
     * @return a flow of raw string results from the parallel tool calls.
     */
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<String> {
        val tool = findTool(toolClass)
        return toParallelToolCallsRaw(tool, concurrency)
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
    inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> findToolByNameAndArgs(toolName: String): Tool<TArgs, TResult> =
        @Suppress("UNCHECKED_CAST")
        (toolRegistry.getTool(toolName) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments"))

    /**
     * Finds a tool by its name and ensures its arguments are compatible with the specified type.
     *
     * @param toolName The name of the tool to be retrieved.
     * @return A SafeTool instance wrapping the tool with the specified argument type.
     * @throws IllegalArgumentException If the tool with the specified name is not defined or its arguments
     * are incompatible with the expected type.
     */
    inline fun <reified TArgs : Tool.Args> findToolByName(toolName: String): SafeTool<TArgs, *> {
        @Suppress("UNCHECKED_CAST")
        val tool = (toolRegistry.getTool(toolName) as? Tool<TArgs, *>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments"))

        return SafeTool(tool, environment)
    }

    /**
     * Updates the current prompt by applying modifications defined in the provided block.
     * The modifications are applied using a `PromptBuilder` instance, allowing for
     * customization of the prompt's content, structure, and associated messages.
     *
     * @param body A lambda with a receiver of type `PromptBuilder` that defines
     *             the modifications to be applied to the current prompt.
     */
    fun updatePrompt(body: PromptBuilder.() -> Unit) {
        prompt = prompt(prompt, body)
    }

    /**
     * Rewrites the current prompt by applying a transformation function.
     *
     * @param body A lambda function that receives the current prompt and returns a modified prompt.
     */
    fun rewritePrompt(body: (prompt: Prompt) -> Prompt) {
        prompt = body(prompt)
    }

    /**
     * Updates the underlying model in the current prompt with the specified new model.
     *
     * @param newModel The new LLModel to replace the existing model in the prompt.
     */
    fun changeModel(newModel: LLModel) {
        model = newModel
    }

    /**
     * Updates the language model's parameters used in the current session prompt.
     *
     * @param newParams The new set of LLMParams to replace the existing parameters in the prompt.
     */
    fun changeLLMParams(newParams: LLMParams) = rewritePrompt {
        prompt.withParams(newParams)
    }

    /**
     * Sends a request to the Language Model (LLM) without including any tools, processes the response,
     * and updates the prompt with the returned message.
     *
     * LLM might answer only with a textual assistant message.
     *
     * @return the response from the LLM after processing the request, as a [Message.Response].
     */
    override suspend fun requestLLMWithoutTools(): Message.Response {
        return super.requestLLMWithoutTools().also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests a response from the Language Learning Model (LLM) while also processing
     * the response by updating the current prompt with the received message.
     *
     * @return The response received from the Language Learning Model (LLM).
     */
    override suspend fun requestLLMOnlyCallingTools(): Message.Response {
        return super.requestLLMOnlyCallingTools().also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests an LLM (Large Language Model) to forcefully utilize a specific tool during its operation.
     *
     * @param tool A descriptor object representing the tool to be enforced for use by the LLM.
     * @return A response message received from the LLM after executing the enforced tool request.
     */
    override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        return super.requestLLMForceOneTool(tool).also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests the execution of a single specified tool, enforcing its use,
     * and updates the prompt based on the generated response.
     *
     * @param tool The tool that will be enforced and executed. It contains the input and output types.
     * @return The response generated after executing the provided tool.
     */
    override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return super.requestLLMForceOneTool(tool).also { response -> updatePrompt { message(response) } }
    }

    /**
     * Makes an asynchronous request to a Large Language Model (LLM) and updates the current prompt
     * with the response received from the LLM.
     *
     * @return A [Message.Response] object containing the response from the LLM.
     */
    override suspend fun requestLLM(): Message.Response {
        return super.requestLLM().also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests multiple responses from the LLM and updates the prompt with the received responses.
     *
     * This method invokes the superclass implementation to fetch a list of LLM responses. Each
     * response is subsequently used to update the session's prompt. The prompt updating mechanism
     * allows stateful interactions with the LLM, maintaining context across multiple requests.
     *
     * @return A list of `Message.Response` containing the results from the LLM.
     */
    override suspend fun requestLLMMultiple(): List<Message.Response> {
        return super.requestLLMMultiple().also { responses ->
            updatePrompt {
                responses.forEach { message(it) }
            }
        }
    }

    /**
     * Requests an LLM (Language Model) to generate a structured output based on the provided structure.
     * The response is post-processed to update the prompt with the raw response.
     *
     * @param structure The structured data definition specifying the expected structured output format, schema, and parsing logic.
     * @param retries The number of retry attempts to allow in case of generation failures.
     * @param fixingModel The language model to use for re-parsing or error correction during retries.
     * @return A structured response containing both the parsed structure and the raw response text.
     */
    override suspend fun <T> requestLLMStructured(
        structure: StructuredData<T>,
        retries: Int,
        fixingModel: LLModel
    ): StructuredResponse<T> {
        return super.requestLLMStructured(structure, retries, fixingModel).also { response ->
            updatePrompt {
                assistant(response.raw)
            }
        }
    }

    /**
     * Streams the result of a request to a language model.
     *
     * @param definition an optional parameter to define a structured data format. When provided, it will be used
     * in constructing the prompt for the language model request.
     * @return a flow of strings that streams the responses from the language model.
     */
    suspend fun requestLLMStreaming(definition: StructuredDataDefinition? = null): Flow<String> {
        if (definition != null) {
            val prompt = prompt(prompt) {
                user {
                    definition.definition(this)
                }
            }
            this.prompt = prompt
        }

        return executor.executeStreaming(prompt, model)
    }

    /**
     * Sends a request to the LLM using the given structured data and expects a structured response in one attempt.
     * Updates the prompt with the raw response received from the LLM.
     *
     * @param structure The structured data defining the schema, examples, and parsing logic for the response.
     * @return A structured response containing both the parsed data and the raw response text from the LLM.
     */
    override suspend fun <T> requestLLMStructuredOneShot(structure: StructuredData<T>): StructuredResponse<T> {
        return super.requestLLMStructuredOneShot(structure).also { response ->
            updatePrompt {
                assistant(response.raw)
            }
        }
    }
}

class LocalAgentLLMReadSession internal constructor(
    tools: List<ToolDescriptor>,
    executor: PromptExecutor,
    prompt: Prompt,
    model: LLModel,
    config: LocalAgentConfig,
) : LocalAgentLLMSession(executor, tools, prompt, model, config)
