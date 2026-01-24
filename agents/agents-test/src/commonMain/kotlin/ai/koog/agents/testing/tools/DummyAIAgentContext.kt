@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.testing.tools

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.BaseBuilder
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A mock implementation of the [AIAgentContext] interface, used for testing purposes.
 *
 * @constructor Creates a new instance of `DummyAIAgentContext` using a predefined
 * `AIAgentContextMockBuilder`.
 * @param builder A builder object used to initialize the mock properties of the context.
 */
@TestOnly
public class DummyAIAgentContext(
    private val builder: AIAgentContextMockBuilder,
    override val agentId: String = "DummyAgentId",
) : AIAgentGraphContextBase {
    override val parentContext: AIAgentGraphContextBase? = null

    /**
     * Indicates whether a Language Learning Model (LLM) is defined in the current context.
     *
     * This property is true if the LLM context (`llm`) has been initialized, providing functionality for handling
     * LLM-related operations within the agent context. It can be used for conditional logic to verify if LLM-specific
     * capabilities are available.
     */
    public val isLLMDefined: Boolean = builder.llm != null

    /**
     * Indicates whether the environment for the agent context is defined.
     *
     * This property evaluates to `true` if the `environment` property in the builder
     * is not null, meaning that a specific environment has been explicitly set for
     * the agent context. If `false`, it implies that no environment has been
     * defined and a default or mock environment may be used in its place.
     */
    public val isEnvironmentDefined: Boolean = builder.environment != null

    private var _environment: AIAgentEnvironment? = builder.environment
    private var _agentInput: Any? = builder.agentInput
    private var _agentInputType: KType? = builder.agentInputType
    private var _config: AIAgentConfig? = builder.config
    private var _llm: AIAgentLLMContext? = builder.llm
    private var _stateManager: AIAgentStateManager? = builder.stateManager
    private var _storage: AIAgentStorage? = builder.storage
    private var _runId: String? = builder.runId
    private var _strategyName: String? = builder.strategyName
    private var _executionInfo: AgentExecutionInfo? = builder.executionInfo

    @OptIn(InternalAgentsApi::class)
    private var _pipeline: AIAgentGraphPipeline = AIAgentGraphPipeline(
        _config ?: AIAgentConfig(Prompt.Empty, OllamaModels.Meta.LLAMA_3_2, 100)
    )

    override val environment: AIAgentEnvironment
        get() = _environment ?: throw NotImplementedError("Environment is not mocked")

    override val agentInput: Any
        get() = _agentInput ?: throw NotImplementedError("Agent input is not mocked")

    override val agentInputType: KType
        get() = _agentInputType ?: throw NotImplementedError("Agent input type is not mocked")

    override val config: AIAgentConfig
        get() = _config ?: throw NotImplementedError("Config is not mocked")

    override val llm: AIAgentLLMContext
        get() = _llm ?: throw NotImplementedError("LLM is not mocked")

    override val stateManager: AIAgentStateManager
        get() = _stateManager ?: throw NotImplementedError("State manager is not mocked")

    override val storage: AIAgentStorage
        get() = _storage ?: throw NotImplementedError("Storage is not mocked")

    override val runId: String
        get() = _runId ?: throw NotImplementedError("Session UUID is not mocked")

    override val strategyName: String
        get() = _strategyName ?: throw NotImplementedError("Strategy name is not mocked")

    @OptIn(InternalAgentsApi::class)
    override val pipeline: AIAgentGraphPipeline
        get() = _pipeline

    override var executionInfo: AgentExecutionInfo
        get() = _executionInfo ?: throw NotImplementedError("Execution info is not mocked")
        set(value) {
            _executionInfo = value
        }

    override fun store(key: AIAgentStorageKey<*>, value: Any) {
        throw NotImplementedError("store() is not supported for mock")
    }

    override fun <T> get(key: AIAgentStorageKey<*>): T {
        throw NotImplementedError("get() is not supported for mock")
    }

    override fun remove(key: AIAgentStorageKey<*>): Boolean {
        throw NotImplementedError("remove() is not supported for mock")
    }

    override suspend fun getHistory(): List<Message> = emptyList()

    /**
     * Creates a new instance of `AIAgentContextBase` with the specified parameters,
     * copying the properties from the current instance with the provided updates.
     *
     * @param environment The environment in which the AI agent operates, allowing interaction with external systems.
     * @param agentInput The input object provided to the AI agent, representing data or context for the agent to process.
     * @param agentInputType The type of the agent input, used to interpret the structure or nature of the input.
     * @param config The configuration settings for the AI agent, such as model specifics and operational limits.
     * @param llm The language model context used by the AI agent for generating responses or processing input.
     * @param stateManager The state management associated with the AI agent, responsible for tracking execution state and history.
     * @param storage A storage mechanism for the AI agent, enabling persistence of key-value information.
     * @param runId A unique identifier for the current execution or operational instance of the AI agent.
     * @param strategyName The name of the strategy used during the AI agent's execution cycle.
     * @param pipeline The pipeline configuration used by the AI agent to define the processing steps.
     * @return An instance of `AIAgentContextBase` with the updated parameters and copied configurations.
     */
    override fun copy(
        environment: AIAgentEnvironment,
        agentId: String,
        agentInput: Any?,
        agentInputType: KType,
        config: AIAgentConfig,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        runId: String,
        strategyName: String,
        pipeline: AIAgentGraphPipeline,
        executionInfo: AgentExecutionInfo,
        parentContext: AIAgentGraphContextBase?,
    ): AIAgentGraphContextBase = DummyAIAgentContext(
        builder.copy(
            environment = environment,
            agentInput = agentInput,
            agentInputType = agentInputType,
            config = config,
            llm = llm,
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategyName,
            executionInfo = executionInfo
        ),
    )

    override suspend fun fork(): AIAgentGraphContextBase {
        throw NotImplementedError("Forking is not supported for mock")
    }

    override suspend fun replace(context: AIAgentContext) {
        throw NotImplementedError("Forking is not supported for mock")
    }
}

/**
 * A base interface for building mock implementations of the [AIAgentContext] interface.
 *
 * This interface provides configurable properties and methods for creating a mock
 * AI agent context, enabling the customization of its environment, input, configuration,
 * state management, and more. It is intended for use in testing scenarios and allows
 * for the creation of testable mock instances of AI agent contexts.
 *
 * Extends the [BaseBuilder] interface for constructing instances of type [AIAgentContext].
 */
@TestOnly
public interface AIAgentContextMockBuilderBase : BaseBuilder<AIAgentContext> {
    /**
     * Represents the environment used by the AI agent to interact with external systems.
     *
     * This variable enables the association of an AI agent's context with a specific instance of
     * `AIAgentEnvironment`. The `AIAgentEnvironment` interface provides mechanisms for tool execution,
     * problem reporting, and termination signaling, serving as the bridge between the agent and its surrounding environment.
     *
     * When set, this variable allows the agent to execute tools through the connected environment, as well as
     * handle exceptions and send termination messages as needed during operation.
     *
     * The value can be null, indicating that no environment is currently associated with the context.
     *
     * @see AIAgentEnvironment
     */
    public var environment: AIAgentEnvironment?

    /**
     * Represents the input to be used by the AI agent during its execution.
     * This variable can be set to define specific data or context relevant to the agent's task.
     * It is nullable, indicating that the agent may operate without an explicitly defined input.
     */
    public var agentInput: Any?

    /**
     * Represents the [KType] of the [agentInput].
     */
    public var agentInputType: KType?

    /**
     * Specifies the configuration for the AI agent.
     *
     * This property allows setting an instance of [AIAgentConfig], which defines various parameters
     * and settings for the AI agent, such as the model, prompt structure, execution strategies, and constraints.
     * It provides the core configuration required for the agent's setup and behavior.
     *
     * If null, the configuration will not be explicitly defined, and the agent may rely on default or externally
     * supplied configurations.
     */
    public var config: AIAgentConfig?

    /**
     * Represents the LLM context associated with an AI agent during testing scenarios.
     * This variable is used to configure and manage the context for an AI agent's
     * large language model (LLM) interactions, including tools, prompt handling,
     * and model-specific attributes.
     *
     * This is part of the mock building process to facilitate controlled testing of agent behaviors
     * and interactions with LLMs.
     *
     * @see AIAgentLLMContext
     */
    public var llm: AIAgentLLMContext?

    /**
     * Represents an optional state manager for an AI agent in the context of building its mock environment.
     * The `stateManager` is responsible for maintaining and managing the internal state of the agent in
     * a thread-safe manner, ensuring consistency during state modifications.
     *
     * This property can be used to define a customized or mocked state management behavior when setting
     * up the test environment for the AI agent. If left unset, no state management behavior is applied
     * in the mock context.
     */
    public var stateManager: AIAgentStateManager?

    /**
     * Represents a concurrent-safe key-value storage used for managing data within the context of mock
     * AI agent construction. This property typically holds an optional instance of [AIAgentStorage],
     * which provides methods to store, retrieve, and manage typed key-value pairs in a thread-safe manner.
     *
     * The storage is designed to facilitate the sharing of structured and unstructured data within
     * different components of the AI agent during the testing and building phase.
     *
     * The storage can be configured or accessed to simulate various testing scenarios or to mock
     * internal states of the agent.
     */
    public var storage: AIAgentStorage?

    /**
     * Represents the unique identifier associated with the session in the mock builder context.
     *
     * This property is optional and may be used to specify or retrieve the id that ties the
     * run to a specific context or operation.
     */
    public var runId: String?

    /**
     * Represents the identifier of a strategy to be used within the context of an AI agent.
     *
     * This variable allows specifying or retrieving the unique identifier associated with a
     * particular strategy. It can be null if no strategy is defined or required.
     */
    public var strategyName: String?

    /**
     * Represents execution-specific context information for the mock AI agent builder.
     * This variable allows tracking and observability of the agent's execution flow.
     *
     * By leveraging the properties defined in [AgentExecutionInfo], this information
     * aids in linking, tracing, and managing execution paths throughout the agent's lifecycle.
     */
    public var executionInfo: AgentExecutionInfo?

    /**
     * Creates and returns a copy of the current instance of `AIAgentContextMockBuilderBase`.
     *
     * @return A new instance of `AIAgentContextMockBuilderBase` with the same properties as the original.
     */
    public fun copy(
        environment: AIAgentEnvironment? = this.environment,
        agentInput: Any? = this.agentInput,
        agentInputType: KType? = this.agentInputType,
        config: AIAgentConfig? = this.config,
        llm: AIAgentLLMContext? = this.llm,
        stateManager: AIAgentStateManager? = this.stateManager,
        storage: AIAgentStorage? = this.storage,
        runId: String? = this.runId,
        strategyName: String? = this.strategyName,
        executionInfo: AgentExecutionInfo? = this.executionInfo,
    ): AIAgentContextMockBuilderBase

    /**
     * Builds and returns an instance of [AIAgentContext] based on the current properties
     * of the builder. This method creates a finalized AI agent context, integrating all the
     * specified configurations, environment settings, and components into a coherent context
     * object ready for use.
     *
     * @return A fully constructed [AIAgentContext] instance representing the configured agent context.
     */
    override fun build(): AIAgentContext
}

/**
 * AIAgentContextMockBuilder is a builder class for constructing a mock implementation of an AI agent context.
 * It provides mechanisms to configure various components of the AI agent context before constructing it.
 * This class is intended for use in testing scenarios and extends `AIAgentContextMockBuilderBase`.
 */
@TestOnly
public class AIAgentContextMockBuilder : AIAgentContextMockBuilderBase {
    /**
     * Represents the AI agent's environment in which the context is being executed.
     *
     * This property allows setting or retrieving the `AIAgentEnvironment` instance associated with
     * the builder, enabling the integration of external environments. The environment provides
     * mechanisms for tool execution, error reporting, and communication of termination signals.
     *
     * It is nullable, as the environment may not always be defined during initialization.
     *
     * @see AIAgentEnvironment
     */
    override var environment: AIAgentEnvironment? = null

    /**
     * Represents the agent's input data used in constructing or testing the agent's context.
     *
     * This property is optional and can be null, indicating that no specific input is provided for the agent.
     * It is used during the construction or copying of an agent's context to define the data the agent operates on.
     */
    override var agentInput: Any? = "test-input-default"

    /**
     * Represents the [KType] of the [agentInput].
     */
    override var agentInputType: KType? = typeOf<String>()

    /**
     * Represents the AI agent configuration used in the mock builder.
     *
     * This property holds the agent's configuration, which may include the parameters for prompts,
     * the language model to be used, iteration limits, and strategies for handling missing tools.
     * It is used in constructing or copying an AI agent context during testing or mock setup.
     *
     * The configuration, represented by [AIAgentConfig], can be modified or replaced
     * depending on the requirements of the mock or testing scenario. A `null` value indicates
     * the absence of a specific configuration.
     */
    override var config: AIAgentConfig? = null

    /**
     * Represents the context for accessing and managing an AI agent's LLM (Large Language Model) configuration
     * and behavior. The `llm` property allows you to define or override the LLM context for the agent,
     * including tools, prompt handling, and interaction with external dependencies.
     *
     * Can be used for dependency injection, mock testing, or modifying the LLM behavior dynamically during
     * runtime. If set to `null`, it indicates that no specific LLM context is defined, and defaults or
     * fallback mechanisms may be used by the containing class.
     */
    override var llm: AIAgentLLMContext? = null

    /**
     * An overrideable property for managing the agent's state using an instance of [AIAgentStateManager].
     *
     * The `stateManager` provides thread-safe mechanisms to update, lock, and access the internal
     * state of the AI agent. It ensures the consistency of state modifications and employs a
     * mutual exclusion mechanism to synchronize coroutines accessing the state.
     *
     * This property can be used for customizing state management within the context of the
     * `AIAgentContextMockBuilder` and its associated operations such as copying or building
     * mock agent contexts.
     *
     * By default, it is initialized to `null` and can be set or overridden to integrate a
     * specific `AIAgentStateManager` instance for managing agent state in custom scenarios.
     */
    override var stateManager: AIAgentStateManager? = null

    /**
     * Represents a concurrent-safe key-value storage instance for an AI agent.
     *
     * This property holds a reference to an optional [AIAgentStorage] implementation, which enables the
     * handling of typed keys and respective values in a thread-safe manner within the agent context.
     * The storage can be used to store, retrieve, or manage custom data uniquely identified by specific keys.
     *
     * It can be configured or overridden during the agent context setup or through later modifications
     * to the context builder. If not provided, the default value remains `null`.
     */
    override var storage: AIAgentStorage? = null

    /**
     * Defines the unique identifier for the session context within the agent's lifecycle.
     * This property can be used to correlate and differentiate multiple sessions for the same agent
     * or across different agents.
     *
     * The `runId` can be null, indicating that the session has not been associated with an identifier.
     */
    @OptIn(ExperimentalUuidApi::class)
    override var runId: String? = "test-run-id-${Uuid.random()}"

    /**
     * Represents the identifier for the strategy to be used in the agent context.
     *
     * This property is used to distinguish and configure different strategies within an AI agent's
     * workflow. It may determine how the agent processes inputs, selects methodologies, or executes
     * tasks, depending on the applied strategy.
     *
     * Can be null if a strategy is not explicitly defined or required.
     */
    override var strategyName: String? = "test-strategy-default"

    /**
     * Represents execution-specific context information for the mock AI agent builder.
     * This variable allows tracking and observability of the agent's execution flow.
     *
     * By leveraging the properties defined in [AgentExecutionInfo], this information
     * aids in linking, tracing, and managing execution paths throughout the agent's lifecycle.
     */
    override var executionInfo: AgentExecutionInfo? = null

    /**
     * Creates and returns a new copy of the current `AIAgentContextMockBuilder` instance.
     * The copied instance contains the same state and configuration as the original.
     *
     * @return a new `AIAgentContextMockBuilder` instance with the same properties as the original.
     */
    override fun copy(
        environment: AIAgentEnvironment?,
        agentInput: Any?,
        agentInputType: KType?,
        config: AIAgentConfig?,
        llm: AIAgentLLMContext?,
        stateManager: AIAgentStateManager?,
        storage: AIAgentStorage?,
        runId: String?,
        strategyName: String?,
        executionInfo: AgentExecutionInfo?,
    ): AIAgentContextMockBuilder {
        return AIAgentContextMockBuilder().also {
            it.environment = environment
            it.agentInput = agentInput
            it.agentInputType = agentInputType
            it.config = config
            it.llm = llm
            it.stateManager = stateManager
            it.storage = storage
            it.runId = runId
            it.strategyName = strategyName
            it.executionInfo = executionInfo
        }
    }

    /**
     * Builds and returns an instance of `DummyAgentContext`.
     *
     * This method finalizes the current configuration of the `AIAgentContextMockBuilder` instance
     * by creating a copy of its current state and passing it to the constructor of `DummyAgentContext`.
     *
     * @return A `DummyAgentContext` instance initialized with the current state of the builder.
     */
    override fun build(): DummyAIAgentContext {
        return DummyAIAgentContext(this.copy())
    }

    /**
     * Companion object providing utility methods for the encompassing class.
     */
    private companion object {
        /**
         * Creates a dummy proxy implementation of the specified type [T] using the provided name.
         *
         * The returned proxy is an instance of [T] that contains stubbed behavior for unimplemented
         * properties and methods. It is primarily intended for mocking or testing purposes.
         *
         * @param name A unique name for the proxy to associate with its string representation and errors.
         * @return A dummy proxy of type [T] with the provided name.
         */
        @Suppress("UNCHECKED_CAST", "unused")
        private inline fun <reified T : Any> createDummyProxy(name: String): T {
            return ProxyHandler<T>(name).createProxy()
        }
    }

    /**
     * ProxyHandler is a utility class that dynamically creates a proxy object of a specified type.
     * The proxy implements default behavior for methods such as `toString`, `equals`, property access,
     * and method invocation, providing a placeholder implementation.
     *
     * This class is typically used to generate mock or dummy objects for testing or prototyping purposes.
     *
     * @param T The type of the proxy object to be created. Must be a non-abstract class.
     * @param name A string identifier associated with the proxy. This is used in placeholder implementation
     *             to display information about the proxy.
     */
    public class ProxyHandler<T : Any>(private val name: String) {
        /**
         * Creates a proxy instance of type [T]. The proxy is a dummy implementation that provides default
         * behavior for overridden methods such as `toString`, `equals`, and unimplemented operations.
         *
         * @return A proxy instance of type [T] with default, unimplemented functionality.
         */
        @Suppress("UNCHECKED_CAST")
        public fun createProxy(): T {
            return object : Any() {
                /**
                 * Returns a string representation of the object with the format `DummyProxy<name>`.
                 *
                 * This implementation provides a simple description of the proxy object that includes
                 * the name of the proxy. It is useful for debugging or logging purposes to identify
                 * the specific proxy instance by its name.
                 *
                 * @return A string in the format `DummyProxy<name>`.
                 */
                override fun toString() = "DummyProxy<$name>"

                /**
                 * Checks whether this instance is equal to the specified object.
                 *
                 * @param other The object to compare with this instance.
                 * @return True if this instance is the same as the specified object, otherwise false.
                 */
                override fun equals(other: Any?): Boolean {
                    return this === other
                }

                /**
                 * Retrieves the value of a property specified by its name.
                 * This operator is intended to simulate property access in a proxy-like manner.
                 *
                 * @param propertyName The name of the property to retrieve.
                 * @return The value of the specified property, or null.
                 * @throws IllegalStateException This function always throws an exception indicating unimplemented property access.
                 */
                @Suppress("UNUSED_PARAMETER")
                operator fun get(propertyName: String): Any {
                    error("Unimplemented property access: $name.$propertyName")
                }

                /**
                 * Invokes a method by its name and passes the provided arguments.
                 *
                 * This method is typically used to simulate an unimplemented method call, throwing an error
                 * to indicate that the called method is not yet implemented.
                 *
                 * @param methodName The name of the method to invoke.
                 * @param args The arguments to pass to the method, provided as a vararg of any type.
                 * @return This function does not return a value as it throws an error instead.
                 */
                @Suppress("UNUSED_PARAMETER")
                fun invoke(methodName: String, vararg args: Any?): Any {
                    error("Unimplemented method call: $name.$methodName(${args.joinToString()})")
                }
            } as T
        }
    }
}
