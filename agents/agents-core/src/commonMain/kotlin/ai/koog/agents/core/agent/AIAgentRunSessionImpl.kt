@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentState.NotStarted
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.utils.runCatchingCancellable
import io.github.oshai.kotlinlogging.KLogger
import kotlin.reflect.typeOf

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
        input: Input
    ): Output {
        state = AIAgentState.Starting()
        val context = ctxBuilder(input, id, agent.id)
        ctx = context
        val runResult = withPreparedPipeline(context, agent.id, sessionPipeline) {
            try {
                logger.debug { formatLog(id, id, "Starting agent execution") }
                sessionPipeline.onAgentStarting<Input, Output>(
                    agent.id,
                    context.executionInfo,
                    id,
                    agent,
                    context
                )

                val result = context.with(partName = strategy.name) { executionInfo, eventId ->
                    runCatchingCancellable {
                        state = AIAgentState.Running(context.parentContext ?: context)
                        context.pipeline.onStrategyStarting(eventId, executionInfo, strategy, context)
                        val result = strategy.execute(context = context, input = input)

                        logger.trace { "Finished executing strategy (name: ${strategy.name}) with result: $result" }
                        context.pipeline.onStrategyCompleted(
                            eventId,
                            executionInfo,
                            strategy,
                            context,
                            result,
                            typeOf<Any?>()
                        )

                        result
                    }.onFailure {
                        context.environment.reportProblem(it)
                    }.getOrThrow()
                }

                logger.debug { formatLog(id, id, "Finished agent execution") }
                sessionPipeline.onAgentCompleted(id, context.executionInfo, agent.id, id, result, context)

                result
            } catch (e: Exception) {
                state = AIAgentState.Failed(e)
                logger.error(e) { "Execution exception reported by server!" }
                sessionPipeline.onAgentExecutionFailed(id, context.executionInfo, agent.id, id, e, context)
                throw e
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

    private suspend fun <T> withPreparedPipeline(
        context: AIAgentContext,
        eventId: String,
        pipeline: AIAgentPipeline,
        block: suspend () -> T
    ): T {
        require(context.executionInfo.parent == null) {
            "withPreparedPipeline() should be called from a top level agent context."
        }

        return try {
            pipeline.prepareFeatures()
            block.invoke()
        } finally {
            pipeline.onAgentClosing(
                eventId = eventId,
                executionInfo = context.executionInfo.parent ?: context.executionInfo,
                agentId = agent.id
            )
            pipeline.closeAllFeaturesMessageProcessors()
        }
    }
}
