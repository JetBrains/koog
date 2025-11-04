@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.datetime.Clock

public actual abstract class AIAgentService<Input, Output, TAgent : AIAgent<Input, Output>> actual constructor() {
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
        agentInput: Input, id: String?,
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

    @Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
    public actual companion object {
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
