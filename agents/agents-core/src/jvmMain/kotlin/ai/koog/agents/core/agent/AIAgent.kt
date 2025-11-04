@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
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
     * @param executorService An optional [ExecutorService] to provide a coroutine context for execution.
     *                        If not provided, the default coroutine context is used.
     * @return The output resulting from the execution of the AI agent with the given input.
     */
    @JavaAPI
    @JvmOverloads
    public final fun run(
        agentInput: Input,
        executorService: ExecutorService? = null
    ): Output = agentConfig.runOnStrategyDispatcher(executorService) { run(agentInput) }

    /**
     * Retrieves the current state of the AI agent asynchronously.
     *
     * This method is designed for Java interoperability and returns a `CompletableFuture`
     * that asynchronously provides the agent's current state.
     *
     * If an `ExecutorService` is provided, it will be used as the coroutine context
     * for this operation. Otherwise, the default coroutine dispatcher is used.
     *
     * @param executorService The `ExecutorService` to use as the coroutine context for this operation.
     *                        If null, a default coroutine dispatcher will be used.
     * @return A `CompletableFuture` containing the current state of the AI agent, represented
     *         as an instance of `AIAgentState<Output>`.
     */
    @JavaAPI
    @JvmOverloads
    public final fun getState(
        executorService: ExecutorService? = null,
    ): AIAgentState<Output> = agentConfig.runOnStrategyDispatcher(executorService) { getState() }

    // Common (multiplatform) methods:

    public actual abstract suspend fun getState(): AIAgentState<Output>

    public actual open suspend fun result(): Output = AIAgentHelper.result(this)

    public actual abstract suspend fun run(agentInput: Input): Output

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
