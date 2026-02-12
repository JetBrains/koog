@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.runOnStrategyDispatcher
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.utils.io.Closeable
import kotlinx.datetime.Clock
import java.util.concurrent.ExecutorService
import kotlin.uuid.ExperimentalUuidApi

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
public actual abstract class AIAgent<Input, Output> : Closeable {
    public actual abstract val id: String
    public actual abstract val agentConfig: AIAgentConfig

    // JAVA Unique methods:

    /**
     * Executes the AI agent task based on the provided input and an optional executor service.
     *
     * @param agentInput The input data required for the AI agent to perform its task.
     * @param sessionId An optional session ID for the agent run.
     * @param executorService An optional [ExecutorService] to provide a coroutine context for execution.
     *                        If not provided, the default coroutine context is used.
     * @return The output resulting from the execution of the AI agent with the given input.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("run")
    public final fun javaRun(
        agentInput: Input,
        sessionId: String? = null,
        executorService: ExecutorService? = null
    ): Output = agentConfig.runOnStrategyDispatcher(executorService) { run(agentInput, sessionId) }

    // Common (multiplatform) methods:
    public actual abstract suspend fun run(agentInput: Input, sessionId: String?): Output

    public actual abstract fun createSession(sessionId: String?): AIAgentRunSession<Input, Output, out AIAgentContext>

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

        @JvmStatic
        public actual fun builder(): AIAgentBuilder = AIAgentBuilder()
    }
}
