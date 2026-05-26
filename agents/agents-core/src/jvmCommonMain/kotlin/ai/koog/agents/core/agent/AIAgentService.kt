@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runBlockingOnStrategyDispatcher
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.utils.time.KoogClock

@OptIn(InternalAgentsApi::class)
public actual abstract class AIAgentService<Input, Output, TAgent : AIAgent<Input, Output>> {
    public actual abstract val promptExecutor: PromptExecutor
    public actual abstract val agentConfig: AIAgentConfig
    public actual abstract val toolRegistry: ToolRegistry
    public actual abstract suspend fun createAgent(
        id: String?,
        additionalToolRegistry: ToolRegistry,
        agentConfig: AIAgentConfig,
        clock: KoogClock
    ): TAgent

    public actual abstract suspend fun createAgentAndRun(
        agentInput: Input,
        id: String?,
        additionalToolRegistry: ToolRegistry,
        agentConfig: AIAgentConfig,
        clock: KoogClock
    ): Output

    public actual abstract suspend fun removeAgent(agent: TAgent): Boolean
    public actual abstract suspend fun removeAgentWithId(id: String): Boolean
    public actual abstract suspend fun agentById(id: String): TAgent?

    /**
     * Creates a new agent with the specified configuration and settings.
     *
     * @param id An optional unique identifier for the agent. If null, a default identifier may be generated.
     * @param additionalToolRegistry The additional tool registry to be associated with the agent. Defaults to an empty registry.
     * @param agentConfig The configuration to use for the agent. Defaults to the service's agent configuration.
     * @param clock The clock instance to be used for time-based functionalities. Defaults to the system clock.
     * @return The created agent instance.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("createAgent")
    public fun createAgentBlocking(
        id: String? = null,
        additionalToolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        agentConfig: AIAgentConfig = this.agentConfig,
        clock: KoogClock = KoogClock.System
    ): TAgent = agentConfig.runBlockingOnStrategyDispatcher {
        createAgent(id, additionalToolRegistry, agentConfig, clock)
    }

    /**
     * Creates an AI agent using the specified parameters and immediately runs it with the provided input.
     *
     * @param agentInput The input data to be processed by the agent.
     * @param id An optional identifier for the agent. If null, a default identifier may be used.
     * @param additionalToolRegistry A registry of additional tools available to the agent. Defaults to an empty registry.
     * @param agentConfig Configuration settings for the agent. Defaults to the current agent configuration of the service.
     * @param clock The clock instance to be used for time-based operations within the agent.
     * @return The output produced by running the agent with the provided input.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("createAgentAndRun")
    public fun createAgentAndRunBlocking(
        agentInput: Input,
        id: String? = null,
        additionalToolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        agentConfig: AIAgentConfig = this.agentConfig,
        clock: KoogClock = KoogClock.System
    ): Output = agentConfig.runBlockingOnStrategyDispatcher {
        val agent = createAgent(id, additionalToolRegistry, agentConfig, clock)
        agent.run(agentInput, null)
    }

    /**
     * Removes the specified agent from the system.
     *
     * @param agent The agent to be removed.
     * @return True if the agent was successfully removed; false otherwise.
     */
    @JavaAPI
    @JvmName("removeAgent")
    public fun removeAgentBlocking(
        agent: TAgent
    ): Boolean = agentConfig.runBlockingOnStrategyDispatcher {
        removeAgent(agent)
    }

    /**
     * Removes an agent identified by the provided ID.
     *
     * @param id The unique identifier of the agent to be removed.
     * @return `true` if the agent was successfully removed, otherwise `false`.
     */
    @JavaAPI
    @JvmName("removeAgentWithId")
    public fun removeAgentWithIdBlocking(
        id: String
    ): Boolean = agentConfig.runBlockingOnStrategyDispatcher {
        removeAgentWithId(id)
    }

    /**
     * Fetches an agent by its unique identifier.
     *
     * @param id The unique identifier of the agent to be retrieved.
     * @return The agent corresponding to the specified identifier, or null if no agent is found.
     */
    @JavaAPI
    @JvmName("agentById")
    public fun agentByIdBlocking(
        id: String
    ): TAgent? = agentConfig.runBlockingOnStrategyDispatcher {
        agentById(id)
    }

    public actual companion object {
        @JvmStatic
        public actual fun builder(): AIAgentServiceBuilder = AIAgentServiceBuilder()

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual inline fun <reified Input, reified Output> fromAgent(
            agent: GraphAIAgent<Input, Output>
        ): AIAgentService<Input, Output, GraphAIAgent<Input, Output>> = AIAgentService(
            promptExecutor = agent.promptExecutor,
            agentConfig = agent.agentConfig,
            strategy = agent.strategy,
            toolRegistry = agent.toolRegistry,
            installFeatures = agent.installFeatures
        )

        @OptIn(markerClass = [InternalAgentsApi::class])
        public actual fun <Input, Output> fromAgent(
            agent: FunctionalAIAgent<Input, Output>
        ): AIAgentService<Input, Output, FunctionalAIAgent<Input, Output>> = AIAgentService(
            promptExecutor = agent.promptExecutor,
            agentConfig = agent.agentConfig,
            strategy = agent.strategy,
            toolRegistry = agent.toolRegistry,
            installFeatures = agent.installFeatures
        )
    }
}
