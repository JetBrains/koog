@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.utils.time.KoogClock

public actual abstract class AIAgentService<Input, Output, TAgent : AIAgent<Input, Output>> actual constructor() {
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

    @Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
    public actual companion object {
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
