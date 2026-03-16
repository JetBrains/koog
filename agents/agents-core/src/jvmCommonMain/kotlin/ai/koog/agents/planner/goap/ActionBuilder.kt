@file:Suppress(
    "MissingKDocForPublicAPI",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT"
)

package ai.koog.agents.planner.goap

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runOnStrategyDispatcher

@OptIn(InternalAgentsApi::class)
public actual class ActionBuilder<State> : ActionBuilderCommon<State, ActionBuilder<State>>() {
    public actual override fun self(): ActionBuilder<State> = this

    /**
     * Sets the synchronous execute function for the action.
     */
    @JavaAPI
    @Deprecated("Use execute(ExecuteSync) instead.", ReplaceWith("execute(execute)"))
    public fun executeSync(execute: ExecuteSync<State>): ActionBuilder<State> =
        execute { context, state ->
            context.config.runOnStrategyDispatcher {
                execute.execute(context, state)
            }
        }

    /**
     * Sets the synchronous execute function for the action.
     */
    @JavaAPI
    @JvmName("execute")
    public fun javaApiExecuteSynchronously(execute: ExecuteSync<State>): ActionBuilder<State> =
        execute { context, state ->
            context.config.runOnStrategyDispatcher {
                execute.execute(context, state)
            }
        }

    /**
     * Synchronous GOAP action execution.
     */
    public fun interface ExecuteSync<State> {
        public fun execute(context: AIAgentPlannerContext, state: State): State
    }
}
