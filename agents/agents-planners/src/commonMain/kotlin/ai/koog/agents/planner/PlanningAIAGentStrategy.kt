package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy

/**
 * Represents a planning strategy for an AI agent.
 *
 * @param name The name of the strategy.
 */
@OptIn(InternalAgentsApi::class)
public abstract class PlanningAIAgentStrategy<State : Any, Plan : Any>(
    public val name: String,
) {
    private val stateKey = createStorageKey<State>("state")
    private val planKey = createStorageKey<Plan>("plan")

    @PublishedApi
    internal abstract fun defineBuildPlan(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<State, Plan?>, Plan>

    @PublishedApi
    internal abstract fun defineExecuteStep(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<State, Plan>, State>

    @PublishedApi
    internal abstract fun defineIsPlanCompleted(builder: AIAgentSubgraphBuilderBase<*, *>):
        AIAgentSubgraphDelegate<Pair<State, Plan>, Boolean>


    @PublishedApi
    internal suspend fun AIAgentGraphContextBase.storeState(state: State): Unit =
        storage.set(stateKey, state)

    @PublishedApi
    internal suspend fun AIAgentGraphContextBase.getState(): State =
        storage.getValue(stateKey)

    @PublishedApi
    internal suspend fun AIAgentGraphContextBase.storePlan(plan: Plan): Unit =
        storage.set(planKey, plan)

    @PublishedApi
    internal suspend fun AIAgentGraphContextBase.getPlan(): Plan? =
        storage.get(planKey)
}

/***/
public inline fun <reified State : Any, reified Plan : Any>
    PlanningAIAgentStrategy<State, Plan>.asStrategy(): AIAgentGraphStrategy<State, State> =
    strategy<State, State>(name) {
        val storeState by node<State, Pair<State, Plan?>> { state ->
            storeState(state)
            state to getPlan()
        }
        val buildPlan by defineBuildPlan(this)
        val savePlan by node<Plan, Pair<State, Plan>> { plan ->
            storePlan(plan)
            getState() to plan
        }
        val isPlanCompleted by defineIsPlanCompleted(this)
        val executeStep by defineExecuteStep(this)

        nodeStart then storeState then buildPlan then savePlan then isPlanCompleted
        edge(
            isPlanCompleted forwardTo executeStep
                onCondition { !it }
                transformed { getState() to getPlan()!! }
        )
        edge(
            isPlanCompleted forwardTo nodeFinish
                onCondition { it }
                transformed { getState() }
        )
        executeStep then storeState
    }
