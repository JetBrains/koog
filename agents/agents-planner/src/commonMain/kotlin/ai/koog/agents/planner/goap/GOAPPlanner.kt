package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.planner.AIAgentPlanningStrategy

/**
 * Goal-Oriented Action Planning (GOAP) implementation for AI agents.
 *
 * GOAP is an AI planning system that uses goals, actions with preconditions and effects,
 * and a search algorithm to find the optimal sequence of actions to achieve a goal.
 *
 * @property name The name of the strategy.
 */
public open class GOAPPlanner<State> internal constructor(
    name: String,
    private val actions: List<Action<State>>,
    private val goals: List<Goal<State>>
) : AIAgentPlanningStrategy<State, GOAPPlan<State>>(name) {

    /**
     * Companion object containing the factory method for creating a GOAPPlanner instance.
     */
    public companion object {
        /**
         * Creates a GOAPPlanner using a DSL for defining actions.
         *
         * @param name The name of the strategy.
         * @param init The initialization block for the builder.
         * @return A new GOAPPlanner instance with the defined actions.
         */
        public fun <State> create(
            name: String,
            init: GOAPPlannerBuilder<State>.() -> Unit
        ): GOAPPlanner<State> {
            val builder = GOAPPlannerBuilder<State>(name)
            builder.init()
            return builder.build()
        }
    }

    override suspend fun buildPlan(
        context: AIAgentFunctionalContext,
        state: State,
        plan: GOAPPlan<State>?
    ): GOAPPlan<State> = goals
        .mapNotNull { goal -> buildPlanForGoal(state, goal, actions) }
        .minByOrNull { plan -> plan.value }
        ?: error("No valid plan found for state: $state")

    override suspend fun executeStep(
        context: AIAgentFunctionalContext,
        state: State,
        plan: GOAPPlan<State>
    ): State {
        for (availableAction in actions) {
            if (plan.actions.first() == availableAction) return availableAction.execute(context, state)
        }
        error("Action is not available: ${plan.actions.first()}")
    }

    override suspend fun isPlanCompleted(
        context: AIAgentFunctionalContext,
        state: State,
        plan: GOAPPlan<State>
    ): Boolean = plan.goal.condition(state)
}
