package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.planner.AIAgentPlanner
import ai.koog.serialization.TypeToken
import ai.koog.serialization.typeToken

/**
 * Goal-Oriented Action Planning (GOAP) implementation for AI agents.
 *
 * GOAP is a planning system that uses goals, actions with preconditions and effects,
 * and a search algorithm to find the optimal sequence of actions to achieve a goal.
 *
 * The plan is represented as a list of action names, which makes it serializable and
 * suitable for persistence. Actions are resolved by name from the planner's action list at runtime.
 *
 * @param State The type of the state.
 * @param actions The list of defined actions.
 * @param goals The list of defined goals.
 */
@Deprecated("Use AIAgentStrategy.builder(name).goap() DSL instead.")
public open class GOAPPlanner<State : Any> internal constructor(
    private val actions: List<Action<State>>,
    private val goals: List<Goal<State>>,
    stateType: TypeToken? = null,
) : AIAgentPlanner<State, List<String>>(
    stateType = stateType,
    planType = typeToken<List<String>>(),
) {
    override suspend fun buildPlan(
        context: AIAgentPlannerContext,
        state: State,
        plan: List<String>?
    ): List<String> = goals
        .mapNotNull { goal -> buildPlanForGoal(state, goal) }
        .minByOrNull { it.value }
        ?.actions?.map { it.name }
        ?: throw IllegalStateException("No valid plan found for state: $state")

    override suspend fun executeStep(
        context: AIAgentPlannerContext,
        state: State,
        plan: List<String>
    ): State {
        val actionName = plan.firstOrNull() ?: return state
        val action = actions.firstOrNull { it.name == actionName }
            ?: throw IllegalStateException("Action is not available: $actionName")
        return action.execute(context, state)
    }

    override suspend fun isPlanCompleted(
        context: AIAgentPlannerContext,
        state: State,
        plan: List<String>
    ): Boolean = goals.any { it.condition(state) }

    //region A-star path search
    private class AStarStep<State>(
        val from: State,
        val action: Action<State>,
        val cost: Double
    )

    /**
     * Implements A-star search algorithm to find a plan for a given goal.
     */
    private fun buildPlanForGoal(
        state: State,
        goal: Goal<State>,
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
