package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentState.NotStarted
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.agent.session.AdditionalInputs
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.utils.runCatchingCancellable
import ai.koog.serialization.typeToken
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * Internal implementation of [AIAgentRunSession] that manages the execution lifecycle of an AI agent.
 *
 * This class handles the complete execution flow of an agent run, including:
 * - State management throughout the agent's lifecycle
 * - Pipeline preparation and cleanup
 * - Strategy execution with proper error handling
 * - Event notifications to the pipeline at each stage
 *
 * The session maintains internal state tracking the progress of the agent execution from
 * [AIAgentState.NotStarted] through [AIAgentState.Starting], [AIAgentState.Running],
 * and finally to either [AIAgentState.Finished] or [AIAgentState.Failed].
 *
 * @param Input the type of input data required by the agent's strategy.
 * @param Output the type of output data produced by the agent's strategy.
 * @param TContext the type of context used during execution, extending [AIAgentContext].
 * @property id the unique identifier of the agent this session belongs to.
 * @property logger the logger instance used for logging execution details and errors.
 * @property agent the AI agent instance being executed in this session.
 * @property strategy the execution strategy that defines how the agent processes input and produces output.
 */
internal class AIAgentRunSessionImpl<Input, Output, TContext : AIAgentContext>(
    private val id: String,
    private val logger: KLogger,
    private val agent: AIAgent<Input, Output>,
    private val strategy: AIAgentStrategy<Input, Output, TContext>,
    private val sessionPipeline: AIAgentPipeline,
    private val ctxBuilder: suspend (Input, String, String) -> TContext
) : AIAgentRunSession<Input, Output, TContext> {
    private var state: AIAgentState<Output> = NotStarted()

    override fun pipeline(): AIAgentPipeline = sessionPipeline

    private var ctx: TContext? = null

    override fun context(): TContext = ctx
        ?: error("Context is not available before running the session. Call run() to start the session and initialize the context.")

    override suspend fun run(
        input: Input,
        sessionInputs: AdditionalInputs,
    ): Output {
        state = AIAgentState.Starting()
        val context = ctxBuilder(input, id, agent.id)
        ctx = context

        when (sessionInputs) {
            is AdditionalInputs.None -> {}
            is AdditionalInputs.Storage -> context.storage.putAll(sessionInputs.storage)
        }

        var runStartMark: TimeSource.Monotonic.ValueTimeMark? = null

        val runResult = try {
            withPreparedPipeline(context, agent.id, sessionPipeline) {
                try {
                    logger.debug { formatLog(id, id, "Starting agent execution") }

                    @OptIn(InternalAgentsApi::class)
                    sessionPipeline.onAgentStarting<Input, Output>(
                        agent.id,
                        context.executionInfo,
                        agent,
                        context,
                        id,
                    )

                    runStartMark = TimeSource.Monotonic.markNow()

                    val result = context.with(partName = strategy.name) { executionInfo, eventId ->
                        runCatchingCancellable {
                            @OptIn(InternalAgentsApi::class)
                            state = AIAgentState.Running(context.parentContext ?: context)

                            @OptIn(InternalAgentsApi::class)
                            context.pipeline.onStrategyStarting(eventId, executionInfo, context, strategy)

                            var result: Output?
                            val strategyDuration = measureTime {
                                result = strategy.execute(context = context, input = input)
                            }

                            logger.trace { "Finished executing strategy (name: ${strategy.name}) with result: $result" }

                            // FIXME!
                            //  resultType will break serialization, need to add outputType to the AIAgentStrategy!
                            @OptIn(InternalAgentsApi::class)
                            context.pipeline.onStrategyCompleted(eventId, executionInfo, context, strategy, result, typeToken<Any?>(), strategyDuration)

                            result
                        }.onFailure {
                            context.environment.reportProblem(it)
                        }.getOrThrow()
                    }

                    logger.debug { formatLog(id, id, "Finished agent execution") }

                    @OptIn(InternalAgentsApi::class)
                    sessionPipeline.onAgentCompleted(
                        eventId = id,
                        executionInfo = context.executionInfo,
                        agent,
                        context,
                        runId = id,
                        result, duration = runStartMark.elapsedNow()
                    )

                    result
                } catch (e: Exception) {
                    state = AIAgentState.Failed(e)
                    logger.error(e) { "Execution exception reported by server!" }

                    @OptIn(InternalAgentsApi::class)
                    // runStartMark is null if the failure happened before strategy execution began
                    // (e.g., onAgentStarting handler threw). We forward null to signal "execution never began" —
                    // see AgentExecutionFailedContext.duration KDoc.
                    sessionPipeline.onAgentExecutionFailed(
                        eventId = id,
                        executionInfo = context.executionInfo,
                        agent,
                        context,
                        runId = id,
                        error = e,
                        duration = runStartMark?.elapsedNow()
                    )

                    throw e
                }
            }
        } finally {
            when (sessionInputs) {
                is AdditionalInputs.None -> {}
                is AdditionalInputs.Storage -> {
                    sessionInputs.storage.clear()
                    sessionInputs.storage.putAll(context.storage)
                }
            }
        }

        if (runResult == null) {
            state = AIAgentState.Failed(Exception("runResult is null"))
            error("runResult is null")
        } else {
            state = AIAgentState.Finished(runResult)
        }

        return runResult
    }

    private fun formatLog(agentId: String, runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"

    @OptIn(InternalAgentsApi::class)
    private suspend fun <T> withPreparedPipeline(
        context: AIAgentContext,
        eventId: String,
        pipeline: AIAgentPipeline,
        block: suspend () -> T
    ): T {
        require(context.executionInfo.parent == null) {
            "withPreparedPipeline() should be called from a top level agent context."
        }

        val agentStartMark = TimeSource.Monotonic.markNow()

        return try {
            pipeline.prepareFeatures()
            block.invoke()
        } finally {
            pipeline.onAgentClosing(
                eventId = eventId,
                executionInfo = context.executionInfo.parent ?: context.executionInfo,
                agent = agent,
                duration = agentStartMark.elapsedNow(),
            )
            pipeline.closeAllFeaturesMessageProcessors()
        }
    }
}
