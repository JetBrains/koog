package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents a planner strategy for an AI agent.
 * @param State The type of the state.
 * @param Plan The type of the plan.
 * @param name The name of the strategy.
 * @param planner The instance of the planner defining the exact planner strategy.
 */
public class AIAgentPlannerStrategy<State, Plan>(
    override val name: String,
    private val planner: AIAgentPlanner<State, Plan>,
) : AIAgentStrategy<State, State, AIAgentFunctionalContext> {
    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: State
    ): State {
        return try {
            context.with(partName = name) { executionInfo, eventId ->
                context.pipeline.onStrategyStarting(eventId, executionInfo, this, context)
                val result = planner.execute(context, input)
                context.pipeline.onStrategyCompleted(eventId, executionInfo, this, context, result, planner.stateType)

                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.environment.reportProblem(e)
            throw e
        }
    }
}
