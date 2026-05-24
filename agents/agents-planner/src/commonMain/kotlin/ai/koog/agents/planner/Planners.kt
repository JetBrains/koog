package ai.koog.agents.planner

import ai.koog.agents.core.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.goap.GOAPPlannerBuilder
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.agents.planner.llm.SimpleLLMPlannerBuilder
import ai.koog.serialization.typeToken
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Primary entry point for creating planner strategies.
 *
 * Provides factory methods for all built-in planner implementations.
 * This object is the recommended entry point for both Java and Kotlin callers.
 *
 * Java example:
 * ```java
 * AIAgentPlannerStrategy<String, String> strategy =
 *     Planners.goap("myStrategy", input -> new MyState(input))
 *         .action("pickup", null, s -> !s.hasItem(), s -> s.withItem(), c -> 1.0, (ctx, s) -> s.withItem())
 *         .goal("haveItem", null, c -> 1/c, c -> 1.0, s -> s.hasItem())
 *         .build();
 * ```
 *
 * Kotlin example:
 * ```kotlin
 * val strategy = Planners.goap("myStrategy", ::MyState) {
 *     action("pickup", precondition = { !it.hasItem }, belief = { it.copy(hasItem = true) }) { _, s -> s.copy(hasItem = true) }
 *     goal("haveItem", condition = { it.hasItem })
 * }
 * ```
 */
public object Planners {

    /**
     * Returns a [GOAPPlannerBuilder] for a Goal-Oriented Action Planning strategy.
     *
     * Call `.action` to add an action
     * Call `.goal` to add a goal
     */
    @JvmStatic
    public fun <Input, Output, State : GoapAgentState<Input, Output>> goap(
        name: String,
        initializeState: (Input) -> State,
    ): GOAPPlannerBuilder<Input, Output, State> = GOAPPlannerBuilder(name, initializeState)

    /**
     * Returns a [SimpleLLMPlannerBuilder] for an LLM-based string-in/string-out planner.
     */
    @JvmStatic
    public fun llmBased(name: String): SimpleLLMPlannerBuilder = SimpleLLMPlannerBuilder(name)

    /**
     * Returns a [SimpleLLMPlannerBuilder] pre-configured with the LLM critic enabled.
     */
    @JvmStatic
    public fun llmBasedWithCritic(name: String): SimpleLLMPlannerBuilder =
        SimpleLLMPlannerBuilder(name).withCritic()
}

/**
 * Creates a GOAP strategy using a Kotlin DSL block.
 */
@JvmSynthetic
public inline fun <Input, Output, reified State : GoapAgentState<Input, Output>> goap(
    name: String,
    noinline initializeState: (Input) -> State,
    configure: GOAPPlannerBuilder<Input, Output, State>.() -> Unit
): AIAgentPlannerStrategy<Input, Output> =
    GOAPPlannerBuilder(name, initializeState).apply(configure).apply { stateType(typeToken<State>()) }.build()
