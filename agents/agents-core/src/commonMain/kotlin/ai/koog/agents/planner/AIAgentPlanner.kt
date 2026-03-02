package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.annotation.InternalAgentsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * An abstract base planner component, which can be used to implement different types of AI agent planner execution flows.
 *
 * An entry point is an [execute] method, which accepts an initial arbitrary [State] and returns the final [State] after the execution.
 *
 * Planner flow works as follows:
 * 1. Build a plan: [buildPlan]
 * 2. Execute a step in the plan: [executeStep]
 * 3. Repeat steps 1 and 2 until the plan is considered completed. Then the final [State] is returned.
 *
 * @param stateType [KType] of the [State].
 */
public abstract class AIAgentPlanner<State : Any, Plan : Any>(
    // FIXME: require the type explicitly when we decide, what to do with it in Java API
    stateType: KType? = null,
) {
    /**
     * [KType] of the [State]
     */
    public val stateType: KType = stateType ?: typeOf<Any?>().also {
        logger.warn { "State type is not specified, some agent features might behave unexpectedly." }
    }

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Builds a plan
     */
    protected abstract suspend fun buildPlan(
        context: AIAgentPlannerContext,
        state: State,
        plan: Plan?
    ): Plan

    /**
     * Executes a step in the plan.
     */
    protected abstract suspend fun executeStep(
        context: AIAgentPlannerContext,
        state: State,
        plan: Plan
    ): State

    /**
     * Checks if the plan is completed.
     */
    protected abstract suspend fun isPlanCompleted(
        context: AIAgentPlannerContext,
        state: State,
        plan: Plan
    ): Boolean

    /**
     * Executes the main loop for the planner, which involves building and executing plans iteratively until
     * the plan is considered successfully completed or a max number of iterations is reached.
     *
     * @param context AI Agent's context
     * @param input The initial state to be used as the starting point for the execution process.
     * @return The final state after the execution of the plans.
     * @throws AIAgentMaxNumberOfIterationsReachedException If the maximum number of iterations defined in the agent's
     * configuration is exceeded.
     */
    @OptIn(InternalAgentsApi::class)
    internal suspend fun execute(
        context: AIAgentPlannerContext,
        input: State
    ): State {
        logger.debug { formatLog(context, "Starting planner execution") }
        var state = input
        var previousPlan: Plan? = null

        while (true) {
            val stepIndex = context.stateManager.withStateLock { state ->
                state.iterations
            }

            val plan = context.with(partName = "buildPlan-${stepIndex + 1}") { executionInfo, eventId ->
                context.pipeline.onPlanCreationStarting(eventId, executionInfo, context, state, previousPlan, stepIndex + 1)
                val newPlan = buildPlan(context, state, previousPlan)
                context.pipeline.onPlanCreationCompleted(eventId, executionInfo, context, state, newPlan, stepIndex + 1)
                newPlan
            }

            logger.debug { formatLog(context, "Executing plan step #${stepIndex + 1}") }

            // Execute step
            context.with(partName = "executeStep-${stepIndex + 1}") { stepExecutionInfo, stepEventId ->
                context.pipeline.onStepExecutionStarting(stepEventId, stepExecutionInfo, context, state, plan, stepIndex + 1)
                state = executeStep(context, state, plan)
                context.pipeline.onStepExecutionCompleted(stepEventId, stepExecutionInfo, context, state, plan, stepIndex + 1)
            }

            logger.debug { formatLog(context, "Finished executing plan step #${stepIndex + 1}") }

            // Check if plan is completed
            val isCompleted = context.with(partName = "isPlanCompleted-${stepIndex + 1}") { executionInfo, eventId ->
                context.pipeline.onPlanCompletionEvaluationStarting(eventId, executionInfo, context, state, plan, stepIndex + 1)
                val completed = isPlanCompleted(context, state, plan)
                context.pipeline.onPlanCompletionEvaluationCompleted(eventId, executionInfo, context, state, plan, completed, stepIndex + 1)
                completed
            }

            context.stateManager.withStateLock { state ->
                if (++state.iterations > context.config.maxAgentIterations) {
                    logger.error {
                        formatLog(
                            context,
                            "Max iterations limit (${context.config.maxAgentIterations}) reached"
                        )
                    }
                    throw AIAgentMaxNumberOfIterationsReachedException(context.config.maxAgentIterations)
                }
            }

            if (isCompleted) break

            previousPlan = plan
        }

        logger.debug { formatLog(context, "Finished planner execution") }
        return state
    }

    private fun formatLog(context: AIAgentContext, message: String): String =
        "$message [${context.strategyName}, ${context.runId}]"
}
