package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents a planner strategy for an AI agent.
 * @param name The name of the strategy.
 */
public class AIAgentPlannerStrategy<State, Plan>(
    override val name: String,
    private val planner: AIAgentPlanner<State, Plan>,
) : AIAgentStrategy<State, State, AIAgentFunctionalContext> { // TODO uses functional context for now, create its own
    override suspend fun execute(
        context: AIAgentFunctionalContext,
        input: State
    ): State {
        return try {
            context.pipeline.onStrategyStarting(this, context)
            val result = planner.execute(context, input)
            context.pipeline.onStrategyCompleted(this, context, result, planner.stateType)

            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.environment.reportProblem(e)
            throw e
        }
    }
}
