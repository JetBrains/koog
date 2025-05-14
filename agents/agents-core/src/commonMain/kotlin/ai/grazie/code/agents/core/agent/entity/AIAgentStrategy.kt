package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.agent.entity.ContextTransitionPolicy.*
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.model.agent.AIAgentStrategy
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.utils.runCatchingCancellable
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentLLMContext
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStage
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContext
import ai.grazie.code.agents.core.dsl.builder.AIAgentStageBuilder
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.extension.clearHistory
import ai.grazie.code.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.grazie.code.agents.core.environment.AIAgentEnvironment
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.PromptExecutorProxy
import ai.grazie.utils.mpp.UUID
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.CompletableDeferred

/**
 * Defines how the LLM conversation history is handled between agent stages.
 *
 * This policy determines what happens to the conversation history with the language model
 * when transitioning from one stage to another in a multi-stage agent execution.
 *
 * Available policies:
 * - [PERSIST_LLM_HISTORY]: Keeps the entire conversation history intact between stages.
 *   Useful when context continuity is important and the history size is manageable.
 * - [COMPRESS_LLM_HISTORY]: Compresses the conversation history between stages to reduce token usage
 *   while preserving essential context. Useful for long-running agents with large conversation histories.
 * - [CLEAR_LLM_HISTORY]: Completely clears the conversation history between stages.
 *   Useful when stages are independent and previous context might confuse the next stage.
 */
public enum class ContextTransitionPolicy {
    PERSIST_LLM_HISTORY, COMPRESS_LLM_HISTORY, CLEAR_LLM_HISTORY
}

/**
 * Implementation of an AI agent that processes user input through a sequence of stages.
 *
 * The AIAgent executes a series of stages in sequence, with each stage receiving the output
 * of the previous stage as its input. The agent manages the LLM conversation history between stages
 * according to the specified [llmHistoryTransitionPolicy].
 *
 * @property name The unique identifier for this agent.
 * @param stages The list of stages that this agent will execute in sequence.
 * @param llmHistoryTransitionPolicy Determines how the LLM conversation history is handled between stages.
 *        - [ContextTransitionPolicy.PERSIST_LLM_HISTORY]: Keeps the entire conversation history intact (default).
 *        - [ContextTransitionPolicy.COMPRESS_LLM_HISTORY]: Compresses the history between stages to reduce token usage.
 *        - [ContextTransitionPolicy.CLEAR_LLM_HISTORY]: Clears the history between stages for independent processing.
 */
public class AIAgentStrategy(
    override val name: String,
    stages: List<AIAgentStage>,
    llmHistoryTransitionPolicy: ContextTransitionPolicy = PERSIST_LLM_HISTORY,
) : AIAgentStrategy<AIAgentConfig>, AIAgentNodeBase<String, String>() {

    /**
     * The list of stages that this agent will execute in sequence.
     *
     * This list may include intermediate stages for LLM history management,
     * depending on the specified [llmHistoryTransitionPolicy].
     */
    internal var stages: List<AIAgentStage> = when (llmHistoryTransitionPolicy) {
        PERSIST_LLM_HISTORY -> stages
        COMPRESS_LLM_HISTORY -> insertIntermediateStage(stages, COMPRESS_HISTORY_STAGE)
        CLEAR_LLM_HISTORY -> insertIntermediateStage(stages, CLEAR_HISTORY_STAGE)
    }

    /**
     * Contains predefined stages and utility functions for the AIAgent.
     */
    internal companion object {
        /**
         * A stage that compresses the LLM conversation history.
         *
         * This stage uses the [nodeLLMCompressHistory] extension to create a node that
         * compresses the conversation history with the language model, reducing token usage
         * while preserving essential context.
         */
        val COMPRESS_HISTORY_STAGE: AIAgentStage = with(AIAgentStageBuilder("compress-history", tools = null)) {
            val compressHistory by nodeLLMCompressHistory<Unit>()

            edge(nodeStart forwardTo compressHistory)
            edge(compressHistory forwardTo nodeFinish transformed { stageInput })

            build()
        }

        /**
         * A stage that clears the LLM conversation history.
         *
         * This stage creates a node that completely clears the conversation history with
         * the language model, allowing the next stage to start with a clean slate.
         */
        val CLEAR_HISTORY_STAGE: AIAgentStage = with(AIAgentStageBuilder("clear-history", tools = null)) {
            val clearHistory by node<Unit, Unit> {
                llm.writeSession { clearHistory() }
            }

            edge(nodeStart forwardTo clearHistory)
            edge(clearHistory forwardTo nodeFinish transformed { stageInput })

            build()
        }

        /**
         * Inserts an intermediate stage between each stage in the provided list of stages.
         *
         * This function is used to add a processing stage (like history compression or clearing)
         * between each of the main agent stages. The intermediate stage is not added after the last stage.
         *
         * Examples:
         * ```
         * // Example 1: Insert a logging stage between processing stages
         * val stages = listOf(parseInputStage, processDataStage, generateOutputStage)
         * val loggingStage = createLoggingStage()
         * val stagesWithLogging = insertIntermediateStage(stages, loggingStage)
         * // Result: [parseInputStage, loggingStage, processDataStage, loggingStage, generateOutputStage]
         *
         * // Example 2: Insert a history compression stage between stages
         * val stages = listOf(userInputStage, reasoningStage, responseStage)
         * val compressHistoryStage = COMPRESS_HISTORY_STAGE
         * val stagesWithCompression = insertIntermediateStage(stages, compressHistoryStage)
         * // Result: [userInputStage, compressHistoryStage, reasoningStage, compressHistoryStage, responseStage]
         * ```
         *
         * @param stages The original list of agent stages
         * @param intermediateStage The stage to insert between each original stage
         * @return A new list with the intermediate stage inserted between each original stage
         */
        fun insertIntermediateStage(
            stages: List<AIAgentStage>,
            intermediateStage: AIAgentStage
        ): List<AIAgentStage> = stages.flatMapIndexed { index, stage ->
            if (index == stages.lastIndex) listOf(stage)
            else listOf(stage, intermediateStage)
        }

    }

    /**
     * Runs the agent with the given input and configuration.
     *
     * This method executes all stages of the agent in sequence, passing the output of each stage
     * as input to the next stage. The final output is sent to the environment as a termination signal.
     * If an error occurs during execution, it is reported to the environment.
     *
     * @param sessionUuid A unique identifier for this agent execution session
     * @param userInput The initial input to the first stage of the agent
     * @param toolRegistry A map of stage names to the tools available for that stage
     * @param promptExecutor LLM API
     * @param environment The environment in which the agent is running
     * @param config The configuration for this agent execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun run(
        sessionUuid: UUID,
        userInput: String,
        toolRegistry: ToolRegistry,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        pipeline: AIAgentPipeline,
    ) {
        val stateManager = AIAgentStateManager()
        val storage = AIAgentStorage()
        var currentStageInput: String = userInput
        var currentPrompt = config.prompt
        var currentModel = config.model

        runCatchingCancellable {

            pipeline.onStrategyStarted(this)

            stages.forEach { stage ->
                val llmContext = AIAgentLLMContext(
                    toolRegistry = toolRegistry,
                    tools = toolRegistry.stagesToolDescriptors[stage.name] ?: emptyList(),
                    prompt = currentPrompt,
                    model = currentModel,
                    promptExecutor = PromptExecutorProxy(promptExecutor, pipeline),
                    environment = environment,
                    config = config,
                )

                val context = AIAgentStageContext(
                    environment = environment,
                    stageInput = currentStageInput,
                    config = config,
                    llm = llmContext,
                    stateManager = stateManager,
                    storage = storage,
                    sessionUuid = sessionUuid,
                    strategyId = name,
                    stageName = stage.name,
                    pipeline = pipeline,
                )

                currentStageInput = stage.execute(context)
                // Passing prompt (full LLM history) to the next stage
                currentPrompt = context.llm.readSession { prompt }
                currentModel = context.llm.readSession { model }
            }

            pipeline.onStrategyFinished(strategyName = name, result = currentStageInput)

            currentStageInput

        }.onSuccess {
            environment.sendTermination(it)
        }.onFailure {
            environment.reportProblem(it)
        }
    }

    /**
     * Executes this agent as a node within another agent's stage.
     *
     * This method implements the [AIAgentNodeBase.execute] method, allowing this agent to be used
     * as a node within another agent's stage. It wraps the provided environment in a [SubAgentEnvironment]
     * to capture the result, runs the agent with the given input, and returns the result.
     *
     * @param context The context of the stage in which this agent is being executed
     * @param input The input to this agent
     * @return The output of this agent's execution
     */
    override suspend fun execute(context: AIAgentStageContextBase, input: String): String {
        val wrappedEnv = SubAgentEnvironment(context.environment)

        @OptIn(InternalAgentsApi::class)
        run(
            sessionUuid = UUID.random(),
            userInput = input,
            toolRegistry = context.llm.toolRegistry,
            promptExecutor = context.llm.promptExecutor,
            environment = wrappedEnv,
            config = context.config,
            pipeline = context.pipeline,
        )

        return wrappedEnv.awaitResult().toString()
    }
}

/**
 * An environment wrapper that captures the termination result of an agent execution.
 *
 * This class is used when an agent is executed as a node within another agent's stage.
 * It delegates all environment operations to the base environment, but captures the
 * termination result so it can be returned to the calling agent.
 *
 * @property baseEnvironment The underlying environment to delegate operations to
 */
public class SubAgentEnvironment(
    private val baseEnvironment: AIAgentEnvironment
) : AIAgentEnvironment by baseEnvironment {
    private val resultDeferred = CompletableDeferred<String?>()

    /**
     * Captures the termination result of the agent execution.
     *
     * This method overrides the base environment's sendTermination method to capture
     * the result for later retrieval.
     *
     * @param result The result of the agent execution
     */
    override suspend fun sendTermination(result: String?) {
        resultDeferred.complete(result)
    }

    /**
     * Waits for and returns the result of the agent execution.
     *
     * @return The result of the agent execution, or null if no result was produced
     */
    public suspend fun awaitResult(): String? = resultDeferred.await()
}
