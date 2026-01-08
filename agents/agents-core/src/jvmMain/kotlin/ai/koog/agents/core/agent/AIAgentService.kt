@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock
import java.util.concurrent.ExecutorService

public actual abstract class AIAgentService<Input, Output, TAgent : AIAgent<Input, Output>> {
    public actual abstract val promptExecutor: PromptExecutor
    public actual abstract val agentConfig: AIAgentConfig
    public actual abstract val toolRegistry: ToolRegistry
    public actual abstract suspend fun createAgent(
        id: String?,
        additionalToolRegistry: ToolRegistry,
        agentConfig: AIAgentConfig,
        clock: Clock
    ): TAgent

    public actual abstract suspend fun createAgentAndRun(
        agentInput: Input,
        id: String?,
        additionalToolRegistry: ToolRegistry,
        agentConfig: AIAgentConfig,
        clock: Clock
    ): Output

    public actual abstract suspend fun removeAgent(agent: TAgent): Boolean
    public actual abstract suspend fun removeAgentWithId(id: String): Boolean
    public actual abstract suspend fun agentById(id: String): TAgent?
    public actual abstract suspend fun listAllAgents(): List<TAgent>
    public actual abstract suspend fun listActiveAgents(): List<TAgent>
    public actual abstract suspend fun listInactiveAgents(): List<TAgent>
    public actual abstract suspend fun listFinishedAgents(): List<TAgent>
    public actual abstract suspend fun closeAll()

    /**
     * Creates a new agent with the specified configuration and settings.
     *
     * @param id An optional unique identifier for the agent. If null, a default identifier may be generated.
     * @param additionalToolRegistry The additional tool registry to be associated with the agent. Defaults to an empty registry.
     * @param agentConfig The configuration to use for the agent. Defaults to the service's agent configuration.
     * @param executorService The executor service to use for asynchronous operations. If null, a default executor may be used.
     * @param clock The clock instance to be used for time-based functionalities. Defaults to the system clock.
     * @return The created agent instance.
     */
    @JavaAPI
    @JvmOverloads
    public fun createAgent(
        id: String? = null,
        additionalToolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        agentConfig: AIAgentConfig = this.agentConfig,
        executorService: ExecutorService? = null,
        clock: Clock = Clock.System
    ): TAgent = agentConfig.runOnStrategyDispatcher(executorService) {
        createAgent(id, additionalToolRegistry, agentConfig, clock)
    }

    /**
     * Creates an AI agent using the specified parameters and immediately runs it with the provided input.
     *
     * @param agentInput The input data to be processed by the agent.
     * @param id An optional identifier for the agent. If null, a default identifier may be used.
     * @param additionalToolRegistry A registry of additional tools available to the agent. Defaults to an empty registry.
     * @param agentConfig Configuration settings for the agent. Defaults to the current agent configuration of the service.
     * @param executorService An optional executor service to be used for running the agent. If null, a default executor may be used.
     * @param clock The clock instance to be used for time-based operations within the agent.
     * @return The output produced by running the agent with the provided input.
     */
    @JavaAPI
    @JvmOverloads
    public fun createAgentAndRun(
        agentInput: Input,
        id: String?,
        additionalToolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        agentConfig: AIAgentConfig = this.agentConfig,
        executorService: ExecutorService? = null,
        clock: Clock
    ): Output = createAgent(id, additionalToolRegistry, agentConfig, executorService, clock)
        .run(agentInput, executorService)

    /**
     * Removes the specified agent from the system.
     *
     * This method uses the provided executor service to execute the removal operation,
     * or defaults to the strategy dispatcher if no executor service is provided.
     *
     * @param agent The agent to be removed.
     * @param executorService Optional executor service to manage the removal operation.
     * @return True if the agent was successfully removed; false otherwise.
     */
    @JavaAPI
    @JvmOverloads
    public fun removeAgent(
        agent: TAgent,
        executorService: ExecutorService? = null
    ): Boolean = agentConfig.runOnStrategyDispatcher(executorService) {
        removeAgent(agent)
    }

    /**
     * Removes an agent identified by the provided ID.
     *
     * @param id The unique identifier of the agent to be removed.
     * @param executorService An optional `ExecutorService` that can be provided to control the execution context.
     * @return `true` if the agent was successfully removed, otherwise `false`.
     */
    @JavaAPI
    @JvmOverloads
    public fun removeAgentWithId(
        id: String,
        executorService: ExecutorService? = null
    ): Boolean = agentConfig.runOnStrategyDispatcher(executorService) {
        removeAgentWithId(id)
    }

    /**
     * Fetches an agent by its unique identifier.
     *
     * This function retrieves an agent using the specified identifier and allows optional execution within a custom
     * executor service. The method leverages the strategy dispatcher to execute the retrieval logic.
     *
     * @param id The unique identifier of the agent to be retrieved.
     * @param executorService An optional executor service to run the task. If not provided, the default dispatcher is used.
     * @return The agent corresponding to the specified identifier, or null if no agent is found.
     */
    @JavaAPI
    @JvmOverloads
    public fun agentById(
        id: String,
        executorService: ExecutorService? = null
    ): TAgent? = agentConfig.runOnStrategyDispatcher(executorService) {
        agentById(id)
    }

    /**
     * Retrieves a list of all agents managed by the system.
     *
     * @param executorService An optional `ExecutorService` to be used for dispatching the operation.
     * If null, a default executor service or dispatcher will be utilized.
     * @return A list of `TAgent` objects representing all agents.
     */
    @JavaAPI
    @JvmOverloads
    public fun listAllAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnStrategyDispatcher(executorService) {
        listAllAgents()
    }

    /**
     * Lists all currently active agents using the configured strategy dispatcher.
     *
     * If an optional `executorService` is provided, it will be used as the execution context
     * for running the operation. Otherwise, a default executor service or dispatcher is utilized.
     *
     * @param executorService The optional `ExecutorService` to use for task execution. Defaults to `null`.
     * @return A list of currently active agents of type `TAgent`.
     */
    @JavaAPI
    @JvmOverloads
    public fun listActiveAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnStrategyDispatcher(executorService) {
        listActiveAgents()
    }

    /**
     * Retrieves a list of all inactive agents.
     *
     * This method utilizes the provided `ExecutorService` for dispatching, if specified.
     * If no `ExecutorService` is provided, a default strategy-specific executor will be used.
     *
     * @param executorService An optional `ExecutorService` to execute the operation.
     *                        If `null`, the default executor service of the agent's configuration is used.
     * @return A list of `TAgent` objects representing the inactive agents.
     */
    @JavaAPI
    @JvmOverloads
    public fun listInactiveAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnStrategyDispatcher(executorService) {
        listInactiveAgents()
    }

    /**
     * Retrieves a list of finished agents.
     *
     * @param executorService The optional executor service to be used for the operation. If no executor service is provided, a default one will be used.
     * @return A list of finished agents.
     */
    @JavaAPI
    @JvmOverloads
    public fun listFinishedAgents(
        executorService: ExecutorService? = null
    ): List<TAgent> = agentConfig.runOnStrategyDispatcher(executorService) {
        listFinishedAgents()
    }

    /**
     * Closes all active agents and releases any resources associated with them.
     *
     * This method ensures that all agents managed by the service are properly terminated. It can be executed
     * either on the default execution environment or a custom one provided via the optional `executorService` parameter.
     *
     * @param executorService An optional `ExecutorService` to execute the method. If provided, the operation
     * will use the specified executor service; otherwise, the default execution mechanism will be used.
     */
    @JavaAPI
    @JvmOverloads
    public fun closeAll(
        executorService: ExecutorService? = null
    ) {
        agentConfig.runOnStrategyDispatcher(executorService) {
            closeAll()
        }
    }

    public actual companion object {
        @JvmStatic
        public actual fun builder(): AIAgentServiceBuilder = AIAgentServiceBuilder()

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual inline fun <reified Input, reified Output> fromAgent(
            agent: GraphAIAgent<Input, Output>
        ): AIAgentService<Input, Output, GraphAIAgent<Input, Output>> = AIAgentServiceHelper.fromAgent(agent)

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual fun <Input, Output> fromAgent(
            agent: FunctionalAIAgent<Input, Output>
        ): AIAgentService<Input, Output, FunctionalAIAgent<Input, Output>> = AIAgentServiceHelper.fromAgent(agent)

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            noinline installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): GraphAIAgentService<Input, Output> =
            AIAgentServiceHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, installFeatures)

        public actual operator fun invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            responseProcessor: ResponseProcessor?,
            strategy: AIAgentGraphStrategy<String, String>,
            toolRegistry: ToolRegistry,
            systemPrompt: String?,
            temperature: Double?,
            numberOfChoices: Int,
            maxIterations: Int,
            installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): GraphAIAgentService<String, String> = AIAgentServiceHelper.invoke(
            promptExecutor,
            llmModel,
            responseProcessor,
            strategy,
            toolRegistry,
            systemPrompt,
            temperature,
            numberOfChoices,
            maxIterations,
            installFeatures
        )

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit
        ): FunctionalAIAgentService<Input, Output> =
            AIAgentServiceHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, installFeatures)
    }
}
