package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Represents a planning strategy for an AI agent.
 * @param name The name of the strategy.
 */
public abstract class AIAgentPlanningStrategy<State, Plan>(
    override val name: String,
) : AIAgentStrategy<State, State, AIAgentFunctionalContext> {
    @Suppress("MissingKDocForPublicAPI")
    public companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Builds a plan
     */
    public abstract suspend fun buildPlan(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan?
    ): Plan

    /**
     * Executes a step in the plan.
     */
    public abstract suspend fun executeStep(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan
    ): State

    /**
     * Checks if the plan is completed.
     */
    public abstract suspend fun isPlanCompleted(
        context: AIAgentFunctionalContext,
        state: State,
        plan: Plan
    ): Boolean

    final override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: State
    ): State? {
        var state = input
        var plan: Plan = buildPlan(context, state, null)

        while (!isPlanCompleted(context, state, plan)) {
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

            state = executeStep(context, state, plan)
            plan = buildPlan(context, state, plan)
        }

        return state
    }

    private fun formatLog(context: AIAgentContext, message: String): String =
        "$message [$name, ${context.runId}]"
}
