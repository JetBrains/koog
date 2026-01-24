package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.planner.AIAgentPlanner
import kotlin.reflect.KType

/**
 * Goal-Oriented Action Planning (GOAP) implementation for AI agents.
 *
 * GOAP is a planning system that uses goals, actions with preconditions and effects,
 * and a search algorithm to find the optimal sequence of actions to achieve a goal.
 *
 * @param State The type of the state.
 * @param actions The list of defined actions.
 * @param goals The list of defined goals.
 */
public open class GOAPPlanner<State> internal constructor(
    private val actions: List<Action<State>>,
    private val goals: List<Goal<State>>,
    stateType: KType,
) : AIAgentPlanner<State, GOAPPlan<State>>(
    stateType = stateType,
) {
    override suspend fun buildPlan(
        context: AIAgentFunctionalContext,
        state: State,
        plan: GOAPPlan<State>?
    ): GOAPPlan<State> = goals
        .mapNotNull { goal -> buildPlanForGoal(state, goal, actions) }
        .minByOrNull { plan -> plan.value }
        ?: throw IllegalStateException("No valid plan found for state: $state")

    override suspend fun executeStep(
        context: AIAgentFunctionalContext,
        state: State,
        plan: GOAPPlan<State>
    ): State {
        for (availableAction in actions) {
            if (plan.actions.first() == availableAction) return availableAction.execute(context, state)
        }
        throw IllegalStateException("Action is not available: ${plan.actions.first()}")
    }

    override suspend fun isPlanCompleted(
        context: AIAgentFunctionalContext,
        state: State,
        plan: GOAPPlan<State>
    ): Boolean = plan.goal.condition(state)

    //region A-star path search
    private class AStarStep<State>(
        val from: State,
        val action: Action<State>,
        val cost: Double
    )

    /**
     * Implements A-star search algorithm to find a plan for a given goal.
     */
    private fun <State> buildPlanForGoal(
        state: State,
        goal: Goal<State>,
        actions: List<Action<State>>,
    ): GOAPPlan<State>? {
        val gScore = mutableMapOf<State, Double>().withDefault { Double.MAX_VALUE }
        val fScore = mutableMapOf<State, Double>().withDefault { Double.MAX_VALUE }

        val incomingStep = mutableMapOf<State, AStarStep<State>>()

        val openSet = mutableSetOf<State>()

        gScore[state] = 0.0
        fScore[state] = goal.cost(state)
        openSet.add(state)

        while (openSet.isNotEmpty()) {
            val currentState = openSet.minBy { fScore.getValue(it) }
            openSet.remove(currentState)

            if (goal.condition(currentState)) {
                val plannedActions = mutableListOf<Action<State>>()
                var step = incomingStep[currentState]
                var cost = 0.0
                while (step != null) {
                    plannedActions.add(step.action)
                    cost += step.cost
                    step = incomingStep[step.from]
                }
                return GOAPPlan(goal, plannedActions.reversed(), goal.value(cost))
            }

            for (action in actions.filter { it.precondition(currentState) }) {
                val newState = action.belief(currentState)

                val stepCost = action.cost(currentState)
                val newGScore = gScore.getValue(currentState) + stepCost

                if (newGScore < gScore.getValue(newState)) {
                    gScore[newState] = newGScore
                    fScore[newState] = newGScore + goal.cost(newState)
                    incomingStep[newState] = AStarStep(currentState, action, stepCost)
                    openSet.add(newState)
                }
            }
        }

        // If we get here, no plan was found
        return null
    }
    //endregion
}
