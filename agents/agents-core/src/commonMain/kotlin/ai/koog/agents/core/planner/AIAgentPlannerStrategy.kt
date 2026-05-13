package ai.koog.agents.core.planner

import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmStatic

/**
 * A strategy implementation that utilizes a planner to manage and execute AI agent workflows.
 *
 * This class integrates an AI planning component ([AIAgentPlanner]) with a defined execution
 * mechanism, creating a customizable strategy for AI agents.
 *
 * @param Input The type of input data required as the starting point for the strategy execution.
 * @param Output The type of output data produced as the result of the strategy execution.
 * @property name The name of the strategy, allowing it to be identifiable in execution contexts.
 * @property planner The planner component responsible for creating and managing the execution plan.
 */
public class AIAgentPlannerStrategy<Input, Output>(
    override val name: String,
    public val planner: AIAgentPlanner<Input, Output, *, *>,
) : AIAgentStrategy<Input, Output, AIAgentPlannerContext> {
    override suspend fun execute(
        context: AIAgentPlannerContext,
        input: Input
    ): Output {
        return try {
            planner.execute(context, input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.environment.reportProblem(e)
            throw e
        }
    }

    /**
     * Companion object for [AIAgentPlannerStrategy] with factory method [create].
     */
    public companion object {
        /**
         * Creates a new [AIAgentPlannerStrategy] instance with the specified [name] and [planner].
         */
        @JvmStatic
        public fun <Input, Output> create(
            name: String,
            planner: AIAgentPlanner<Input, Output, *, *>,
        ): AIAgentPlannerStrategy<Input, Output> {
            return AIAgentPlannerStrategy(name, planner)
        }
    }
}
