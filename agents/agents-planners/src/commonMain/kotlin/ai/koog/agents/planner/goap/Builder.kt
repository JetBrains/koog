package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlin.math.exp

/**
 * DSL for defining actions.
 */
public class GOAPPlannerBuilder<State> @PublishedApi internal constructor(
    @PublishedApi
    internal val name: String
) {
    @PublishedApi
    internal val actions: MutableList<Action<State>> = mutableListOf()

    @PublishedApi
    internal val goals: MutableList<Goal<State>> = mutableListOf()

    /**
     * Defines a goal for the GOAP agent.
     *
     * @param name The name of the goal.
     * @param description Optional description of the goal.
     * @param value Goal value depending on the cost of reaching the goal. Default is `exp(-cost)`.
     * @param cost Heuristic estimate for the cost of reaching the goal. Default is 1.0.
     * @param condition Condition determining when the goal is achieved.
     */
    public fun goal(
        name: String,
        description: String? = null,
        value: (Double) -> Double = { cost -> exp(-cost) },
        cost: State.() -> Double = { 1.0 },
        condition: State.() -> Boolean,
    ) {
        goals.add(Goal(name, description, value, cost, condition))
    }
}

/**
 * Defines an action available to the GOAP agent.
 *
 * @param name The name of the action.
 * @param description Optional description of the action.
 * @param precondition Condition determining if the action can be performed.
 * @param belief Optimistic belief of the state after performing the action.
 * @param cost Heuristic estimate for the cost of performing the action. Default is 1.0.
 * @param define Subgraph defining how the action is performed.
 */
public inline fun <reified State> GOAPPlannerBuilder<State>.action(
    name: String,
    description: String? = null,
    noinline precondition: State.() -> Boolean,
    noinline belief: State.() -> State,
    noinline cost: (State) -> Double = { 1.0 },
    noinline define: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<State, State>,
) {
    actions.add(Action(name, description, precondition, belief, cost, define))
}

/**
 * Defines an action available to the GOAP agent.
 *
 * @param name The name of the action.
 * @param description Optional description of the action.
 * @param precondition Condition determining if the action can be performed.
 * @param belief Optimistic belief of the state after performing the action.
 * @param cost Heuristic estimate for the cost of performing the action. Default is 1.0.
 * @param toolSelectionStrategy Optional strategy for selecting tools for the action.
 * Defaults to [ToolSelectionStrategy.ALL], which corresponds to selecting all tools available to the agent.
 * @param llModel LLM model for generating the action description.
 * Defaults to null, which corresponds to using the agent's LLM model.
 * @param llmParams Optional parameters for the LLM model.
 * Defaults to null, which corresponds to using the agent's LLM parameters.
 * @param execute Function defining how the action is performed.
 */
public inline fun <reified State> GOAPPlannerBuilder<State>.action(
    name: String,
    description: String? = null,
    noinline precondition: State.() -> Boolean,
    noinline belief: State.() -> State,
    noinline cost: (State) -> Double = { 1.0 },
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline execute: suspend AIAgentGraphContextBase.(State) -> State,
) {
    action(name, description, precondition, belief, cost, define = {
        singleNodeSubgraph(
            toolSelectionStrategy = toolSelectionStrategy,
            llmModel = llModel,
            llmParams = llmParams,
            execute = execute
        )
    })
}

@PublishedApi
internal inline fun <reified State : Any> GOAPPlannerBuilder<State>.build(): GOAPPlanner<State> {
    val defineBuildPlan: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>?>, GOAPPlan<State>> =
        {
            singleNodeSubgraph { (state, _) ->
                goals
                    .mapNotNull { goal -> buildPlanForGoal(state, goal, actions) }
                    .minByOrNull { plan -> plan.value }
                    ?: error("No valid plan found for state: $state")
            }
        }

    val defineExecuteStep: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>>, State> =
        {
            subgraph {
                for (action in actions) {
                    val actionSubgraph by action.define(this)

                    edge(
                        nodeStart forwardTo actionSubgraph
                            onCondition { (_, plan) -> action == plan.actions.first() }
                            transformed { (state, _) -> state }
                    )

                    actionSubgraph then nodeFinish
                }
            }
        }

    val defineIsPlanCompleted: AIAgentSubgraphBuilderBase<*, *>.() -> AIAgentSubgraphDelegate<Pair<State, GOAPPlan<State>>, Boolean> =
        {
            singleNodeSubgraph { (state, plan) ->
                plan.goal.condition(state)
            }
        }

    return GOAPPlanner(name, defineBuildPlan, defineExecuteStep, defineIsPlanCompleted)
}
