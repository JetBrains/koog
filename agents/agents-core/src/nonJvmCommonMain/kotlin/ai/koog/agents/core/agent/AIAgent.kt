@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.utils.io.Closeable
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual abstract class AIAgent<Input, Output> actual constructor(
    logger: KLogger,
    id: String?
) : Closeable {
    @OptIn(ExperimentalUuidApi::class)
    public actual val id: String by lazy { id ?: Uuid.random().toString() }
    internal actual open val logger = logger
    public actual abstract val agentConfig: AIAgentConfig
    public actual abstract val strategy: AIAgentStrategy<Input, Output, *>
    public actual abstract val pipeline: AIAgentPipeline

    public actual suspend fun run(agentInput: Input, sessionId: String?): Output {
        val session = createSession(sessionId)
        return session.run(agentInput)
    }

    public actual abstract fun createSession(sessionId: String?): AIAgentRunSession<Input, Output, out AIAgentContext>

    actual override suspend fun close() {}

    public actual companion object {
        @OptIn(markerClass = [ExperimentalUuidApi::class])
        public actual inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: Clock,
            noinline installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> =
            AIAgentHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, id, clock, installFeatures)

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        public actual operator fun invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<String, String>,
            toolRegistry: ToolRegistry,
            id: String?,
            installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): GraphAIAgent<String, String> = AIAgentHelper.invoke(
            promptExecutor,
            agentConfig,
            strategy,
            toolRegistry,
            id,
            installFeatures = installFeatures
        )

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: Clock,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit
        ): FunctionalAIAgent<Input, Output> =
            AIAgentHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, id, clock, installFeatures)

        @OptIn(markerClass = [ExperimentalUuidApi::class])
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
        ): AIAgent<String, String> = AIAgentHelper.invoke(
            promptExecutor,
            llmModel,
            responseProcessor,
            strategy,
            toolRegistry,
            id,
            systemPrompt,
            temperature,
            numberOfChoices,
            maxIterations,
            installFeatures
        )

        @OptIn(markerClass = [ExperimentalUuidApi::class])
        public actual inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            strategy: AIAgentGraphStrategy<Input, Output>,
            responseProcessor: ResponseProcessor?,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: Clock,
            systemPrompt: String?,
            temperature: Double?,
            numberOfChoices: Int,
            maxIterations: Int,
            noinline installFeatures: GraphAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> = AIAgentHelper.invoke(
            promptExecutor,
            llmModel,
            strategy,
            responseProcessor,
            toolRegistry,
            id,
            clock,
            systemPrompt,
            temperature,
            numberOfChoices,
            maxIterations,
            installFeatures
        )

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
        ): AIAgent<Input, Output> = AIAgentHelper.invoke(
            promptExecutor,
            llmModel,
            responseProcessor,
            toolRegistry,
            strategy,
            id,
            systemPrompt,
            temperature,
            numberOfChoices,
            maxIterations,
            installFeatures
        )

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
        ): AIAgent<Input, Output> = AIAgentHelper.invoke(
            promptExecutor,
            llmModel,
            responseProcessor,
            toolRegistry,
            strategy,
            id,
            systemPrompt,
            temperature,
            numberOfChoices,
            maxIterations,
            installFeatures
        )

        public actual operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentPlannerStrategy<Input, Output, *>,
            toolRegistry: ToolRegistry,
            id: String?,
            clock: Clock,
            installFeatures: PlannerAIAgent.FeatureContext.() -> Unit
        ): AIAgent<Input, Output> =
            AIAgentHelper.invoke(promptExecutor, agentConfig, strategy, toolRegistry, id, clock, installFeatures)

        public actual fun builder(): AIAgentBuilder = AIAgentBuilder()
    }
}
