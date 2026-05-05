@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.utils.io.Closeable
import ai.koog.utils.time.KoogClock
import kotlin.uuid.ExperimentalUuidApi

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual abstract class AIAgent<Input, Output> : Closeable {
    public actual abstract val id: String
    public actual abstract val agentConfig: AIAgentConfig

    public actual abstract suspend fun run(agentInput: Input, sessionId: String?): Output

    public actual abstract fun createSession(sessionId: String?): AIAgentRunSession<Input, Output, out AIAgentContext>

    public actual companion object {
        @OptIn(markerClass = [ExperimentalUuidApi::class])
        @Deprecated("Use AIAgent(...)")
        public actual inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: KoogClock,
            noinline installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> =
            AIAgent(promptExecutor, agentConfig, strategy, toolRegistry, id, clock, installFeatures)

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        @Deprecated("Use AIAgent(...)")
        public actual operator fun invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<String, String>,
            toolRegistry: ToolRegistry,
            id: String?,
            installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): GraphAIAgent<String, String> = AIAgent(
            promptExecutor,
            agentConfig,
            strategy,
            toolRegistry,
            id,
            installFeatures = installFeatures
        )

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        @Deprecated("Use AIAgent(...)")
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: KoogClock,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit
        ): FunctionalAIAgent<Input, Output> =
            AIAgent(promptExecutor, agentConfig, strategy, toolRegistry, id, clock, installFeatures)

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        @Deprecated("Use AIAgent(...)")
        public actual operator fun invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            responseProcessor: ResponseProcessor?,
            strategy: AIAgentGraphStrategy<String, String>,
            toolRegistry: ToolRegistry,
            id: String?,
            systemPrompt: String?,
            temperature: Double?,
            numberOfChoices: Int,
            maxIterations: Int,
            installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): AIAgent<String, String> = AIAgent(
            promptExecutor,
            llmModel,
            strategy,
            toolRegistry,
            systemPrompt,
            temperature,
            maxIterations,
            responseProcessor,
            id,
            Clock.System,
            installFeatures
        )

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        @Deprecated("Use AIAgent(...)")
        public actual inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            strategy: AIAgentGraphStrategy<Input, Output>,
            responseProcessor: ResponseProcessor?,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: KoogClock,
            systemPrompt: String?,
            temperature: Double?,
            numberOfChoices: Int,
            maxIterations: Int,
            noinline installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> = AIAgent(
            promptExecutor,
            llmModel,
            strategy,
            toolRegistry,
            systemPrompt,
            temperature,
            maxIterations,
            responseProcessor,
            id,
            Clock.System,
            installFeatures
        )

        @Deprecated("Use AIAgent(...)")
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            responseProcessor: ResponseProcessor?,
            toolRegistry: ToolRegistry,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            id: String?,
            systemPrompt: String?,
            temperature: Double?,
            numberOfChoices: Int,
            maxIterations: Int,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> = AIAgent(
            promptExecutor,
            llmModel,
            strategy,
            toolRegistry,
            systemPrompt,
            temperature,
            maxIterations,
            responseProcessor,
            id,
            Clock.System,
            installFeatures
        )

        @Deprecated("Use AIAgent(...)")
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            responseProcessor: ResponseProcessor?,
            toolRegistry: ToolRegistry,
            strategy: AIAgentPlannerStrategy<Input, Output, *>,
            id: String?,
            systemPrompt: String?,
            temperature: Double?,
            numberOfChoices: Int,
            maxIterations: Int,
            installFeatures: PlannerAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> = AIAgent(
            promptExecutor,
            llmModel,
            strategy,
            toolRegistry,
            systemPrompt,
            temperature,
            maxIterations,
            responseProcessor,
            id,
            Clock.System,
            installFeatures
        )

        @Deprecated("Use AIAgent(...)")
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentPlannerStrategy<Input, Output, *>,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: KoogClock,
            installFeatures: PlannerAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> =
            AIAgent(promptExecutor, agentConfig, strategy, toolRegistry, id, clock, installFeatures)

        public actual fun builder(): AIAgentBuilder = AIAgentBuilder()
    }
}
