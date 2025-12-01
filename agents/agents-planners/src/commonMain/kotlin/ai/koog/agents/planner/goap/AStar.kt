package ai.koog.agents.planner.goap

private class AStarStep<State>(
    val from: State,
    val action: Action<State>,
    val cost: Double
)

@PublishedApi
internal fun <State> buildPlanForGoal(
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
