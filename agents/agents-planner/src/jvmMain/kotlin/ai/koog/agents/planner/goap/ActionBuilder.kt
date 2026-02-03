@file:Suppress(
    "MissingKDocForPublicAPI",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT"
)

package ai.koog.agents.planner.goap

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runOnStrategyDispatcher

@OptIn(InternalAgentsApi::class)
public actual class ActionBuilder<State> : ActionBuilderApi<State> {
    private val delegate = ActionBuilderImpl<State>()
    public actual override fun name(name: String): ActionBuilder<State> = apply { delegate.name(name) }
    public actual override fun description(description: String?): ActionBuilder<State> =
        apply { delegate.description(description) }

    public actual override fun precondition(precondition: Condition<State>): ActionBuilder<State> =
        apply { delegate.precondition(precondition) }

    public actual override fun belief(belief: Belief<State>): ActionBuilder<State> = apply { delegate.belief(belief) }
    public actual override fun cost(cost: Cost<State>): ActionBuilder<State> = apply { delegate.cost(cost) }
    public actual override fun executeAsync(execute: Execute<State>): ActionBuilder<State> =
        apply { delegate.executeAsync(execute) }

    /**
     * Sets the synchronous execute function for the action.
     */
    @JavaAPI
    public fun execute(execute: ExecuteSync<State>): ActionBuilder<State> =
        executeAsync { context, state ->
            context.config.runOnStrategyDispatcher {
                execute.execute(context, state)
            }
        }

    public actual override fun build(): Action<State> = delegate.build()

    /**
     * Synchronous GOAP action execution.
     */
    public fun interface ExecuteSync<State> {
        public fun execute(context: AIAgentFunctionalContext, state: State): State
    }
}
