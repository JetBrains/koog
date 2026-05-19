@file:Suppress(
    "MissingKDocForPublicAPI",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
)

package ai.koog.agents.planner.goap

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.utils.annotations.InternalKoogUtils
import ai.koog.utils.concurrency.withContextReentrant

@OptIn(InternalKoogUtils::class, InternalAgentsApi::class)
public actual class ActionBuilder<State> : ActionBuilderCommon<State, ActionBuilder<State>>() {
    actual override fun self(): ActionBuilder<State> = this

    /**
     * Synchronous GOAP action execution.
     */
    @JavaAPI
    @JvmName("execute")
    public fun executeBlocking(execute: ExecuteSync<State>): ActionBuilder<State> =
        execute { context, state ->
            withContextReentrant(context.config.strategyDispatcher) {
                execute.execute(context, state)
            }
        }

    /**
     * Synchronous GOAP action execution for Java interop.
     */
    public fun interface ExecuteSync<State> {
        public fun execute(context: AIAgentPlannerContext, state: State): State
    }
}
