package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
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
public abstract class AIAgentPlanner<State, Plan>(
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
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan?
    ): Plan

    /**
     * Executes a step in the plan.
     */
    protected abstract suspend fun executeStep(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan
    ): State

    /**
     * Checks if the plan is completed.
     */
    protected abstract suspend fun isPlanCompleted(
        context: AIAgentFunctionalContext,
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
    public suspend fun execute(
        context: AIAgentFunctionalContext,
        input: State
    ): State {
        logger.debug { formatLog(context, "Starting planner execution") }
        var state = input
        var plan: Plan = buildPlan(context, state, null)

        while (!isPlanCompleted(context, state, plan)) {
            val iterations = context.stateManager.withStateLock { state ->
                if (++state.iterations > context.config.maxAgentIterations) {
                    logger.error {
                        formatLog(
                            context,
                            "Max iterations limit (${context.config.maxAgentIterations}) reached"
                        )
                    }
                    throw AIAgentMaxNumberOfIterationsReachedException(context.config.maxAgentIterations)
                }

                state.iterations
            }

            logger.debug { formatLog(context, "Executing plan step #$iterations") }
            state = executeStep(context, state, plan)
            plan = buildPlan(context, state, plan)
            logger.debug { formatLog(context, "Finished executing plan step #$iterations") }
        }

        logger.debug { formatLog(context, "Finished planner execution") }
        return state
    }

    private fun formatLog(context: AIAgentContext, message: String): String =
        "$message [${context.strategyName}, ${context.runId}]"
}
