package ai.koog.agents.planner.goap

import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.planner.PlanningAIAgentStrategy

/**
 * Goal-Oriented Action Planning (GOAP) implementation for AI agents.
 *
 * GOAP is an AI planning system that uses goals, actions with preconditions and effects,
 * and a search algorithm to find the optimal sequence of actions to achieve a goal.
 *
 * @property name The name of the strategy.
 */
public open class GOAPPlanner<State : Any> @PublishedApi internal constructor(
    name: String,
    private val defineBuildPlan: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>?>, GOAPPlan<State>>,
    private val defineExecuteStep: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>>, State>,
    private val defineIsPlanCompleted: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>>, Boolean>,
) : PlanningAIAgentStrategy<State, GOAPPlan<State>>(name) {

    /***/
    public companion object {
        /**
         * Creates a GOAPPlanner using a DSL for defining actions.
         *
         * @param name The name of the strategy.
         * @param init The initialization block for the builder.
         * @return A new GOAPPlanner instance with the defined actions.
         */
        public inline fun <reified State : Any> create(
            name: String,
            noinline init: GOAPPlannerBuilder<State>.() -> Unit
        ): GOAPPlanner<State> {
            val builder = GOAPPlannerBuilder<State>(name)
            builder.init()
            return builder.build()
        }
    }

    override fun defineBuildPlan(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>?>, GOAPPlan<State>> =
        builder.defineBuildPlan()

    override fun defineExecuteStep(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>>, State> =
        builder.defineExecuteStep()

    override fun defineIsPlanCompleted(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>>, Boolean> =
        builder.defineIsPlanCompleted()
}
