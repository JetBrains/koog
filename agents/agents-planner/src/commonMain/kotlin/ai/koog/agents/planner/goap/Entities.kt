package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import kotlin.jvm.JvmStatic

public typealias Condition<State> = (State) -> Boolean
public typealias Belief<State> = (State) -> State
public typealias Cost<State> = (State) -> Double
public typealias Execute<State> = suspend (AIAgentFunctionalContext, State) -> State
public typealias GoalValue = (Double) -> Double

/**
 * Represents an action that can be performed by the agent.
 */
public class Action<State>(
    public val name: String,
    public val description: String?,
    public val precondition: Condition<State>,
    public val belief: Belief<State>,
    public val cost: Cost<State>,
    public val execute: Execute<State>
) {

    /**
     * Companion object for builder api.
     */
    public companion object {
        /**
         * Returns a new [ActionBuilder].
         */
        @JvmStatic
        public fun <State> builder(): ActionBuilder<State> = ActionBuilder()
    }
}

/**
 * Represents a goal that the agent wants to achieve.
 */
public class Goal<State>(
    public val name: String,
    public val description: String?,
    public val value: GoalValue,
    public val cost: Cost<State>,
    public val condition: Condition<State>
) {
    /**
     * Companion object for builder api.
     */
    public companion object {
        /**
         * Returns a new [GoalBuilder].
         */
        @JvmStatic
        public fun <State> builder(): GoalBuilder<State> = GoalBuilder()
    }
}

/**
 * A GOAP plan.
 */
public class GOAPPlan<State>(
    public val goal: Goal<State>,
    public val actions: List<Action<State>>,
    public val value: Double,
)
