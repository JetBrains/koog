@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.planner

import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.utils.ConfigureAction
import ai.koog.agents.planner.goap.Action
import ai.koog.agents.planner.goap.ActionBuilder
import ai.koog.agents.planner.goap.Belief
import ai.koog.agents.planner.goap.Condition
import ai.koog.agents.planner.goap.Cost
import ai.koog.agents.planner.goap.Execute
import ai.koog.agents.planner.goap.GOAPPlanner
import ai.koog.agents.planner.goap.Goal
import ai.koog.agents.planner.goap.GoalBuilder
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.agents.planner.llm.SimpleLLMPlanner
import ai.koog.agents.planner.llm.SimpleLLMWithCriticPlanner
import kotlin.jvm.JvmOverloads
import kotlin.math.exp

public class AIAgentPlannerStrategyBuilder(private val name: String) {
    public fun <Input : Any, Output : Any, State : GoapAgentState<Input, Output>> goap(
        initializeState: (Input) -> State
    ): GOAPStrategyBuilder<Input, Output, State> = GOAPStrategyBuilder(name, initializeState)

    public fun llmBasedPlanner(): SimpleLLMPLannerBuilder = SimpleLLMPLannerBuilder(name)

    public fun <State : Any> withPlanner(
        planner: AIAgentPlanner<State, *>
    ): CustomPlannerStrategyBuilder<State> = CustomPlannerStrategyBuilder(name, planner)
}

public class GOAPStrategyBuilder<Input : Any, Output : Any, State : GoapAgentState<Input, Output>>(
    private val name: String,
    private val initializeState: (Input) -> State
) : TypedAgentPlannerStrategyBuilder<Input, Output> {
    private val actions: MutableList<Action<State>> = mutableListOf()
    private val goals: MutableList<Goal<State>> = mutableListOf()

    /**
     * Defines an action available to the GOAP agent.
     *
     * Returns the builder instance for chained calls.
     */
    public fun action(action: Action<State>): GOAPStrategyBuilder<Input, Output, State> = apply { actions.add(action) }

    /**
     * Defines and configures an action available to the GOAP agent using the provided builder.
     *
     * @param name The name of the action.
     * @param configure A configuration function used to customize the action builder.
     */
    public fun action(
        name: String,
        configure: ConfigureAction<ActionBuilder<State>>
    ): GOAPStrategyBuilder<Input, Output, State> = apply {
        action(ActionBuilder<State>().apply { name(name) }.apply(configure::configure).build())
    }

    /**
     * Defines an action available to the GOAP agent.
     *
     * @param name The name of the action.
     * @param description Optional description of the action.
     * @param precondition Condition determining if the action can be performed.
     * @param belief Optimistic belief of the state after performing the action.
     * @param cost Heuristic estimate for the cost of performing the action. Default is 1.0.
     * @param execute Subgraph defining how the action is performed.
     */
    @JvmOverloads
    public fun action(
        name: String,
        description: String? = null,
        precondition: Condition<State>,
        belief: Belief<State>,
        cost: Cost<State> = { 1.0 },
        execute: Execute<State>,
    ): GOAPStrategyBuilder<Input, Output, State> = apply {
        action(Action(name, description, precondition, belief, cost, execute))
    }

    /**
     * Defines a goal for the GOAP agent.
     *
     * Returns the builder instance for chained calls.
     */
    public fun goal(goal: Goal<State>): GOAPStrategyBuilder<Input, Output, State> = apply { goals.add(goal) }

    /**
     * Defines and configures a goal for the GOAP agent using the provided builder.
     *
     * @param name The name of the goal.
     * @param configure A configuration function used to customize the goal builder.
     */
    public fun goal(
        name: String,
        configure: ConfigureAction<GoalBuilder<State>>
    ): GOAPStrategyBuilder<Input, Output, State> = apply {
        goal(GoalBuilder<State>().apply { name(name) }.apply(configure::configure).build())
    }

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
        cost: Cost<State> = { 1.0 },
        condition: Condition<State>,
    ): GOAPStrategyBuilder<Input, Output, State> = apply {
        goal(Goal(name, description, value, cost, condition))
    }

    override fun build(): AIAgentPlannerStrategy<Input, Output, State> = AIAgentPlannerStrategy(
        name = name,
        planner = GOAPPlanner(
            actions = actions,
            goals = goals
        ),
        initializeState = { input: Input -> initializeState(input) },
        provideOutput = { state: State -> state.provideOutput() }
    )
}

public interface TypedAgentPlannerStrategyBuilder<Input : Any, Output : Any> {
    public fun build(): AIAgentPlannerStrategy<Input, Output, *>
}

public class SimpleLLMPLannerBuilder(private val name: String) : TypedAgentPlannerStrategyBuilder<String, String> {
    private var compressionStrategy: HistoryCompressionStrategy = HistoryCompressionStrategy.NoCompression
    private var useCritic: Boolean = false

    public fun withHistoryCompression(
        historyCompressionStrategy: HistoryCompressionStrategy
    ): SimpleLLMPLannerBuilder = apply {
        compressionStrategy = historyCompressionStrategy
    }

    public fun withCritic(): SimpleLLMPLannerBuilder = apply { useCritic = true }

    public override fun build(): AIAgentPlannerStrategy<String, String, *> = AIAgentPlannerStrategy(
        name = name,
        planner = if (useCritic) {
            SimpleLLMWithCriticPlanner(compressionStrategy)
        } else {
            SimpleLLMPlanner(compressionStrategy)
        }
    )
}

public class CustomPlannerStrategyBuilder<State : Any>(
    private val name: String,
    private val planner: AIAgentPlanner<State, *>
) : TypedAgentPlannerStrategyBuilder<State, State> {
    public fun <Input : Any> withInput(
        provideInput: (Input) -> State
    ): CustomPlannerStrategyBuilderWithInput<Input, State> = CustomPlannerStrategyBuilderWithInput(
        name = name,
        planner = planner,
        provideInput = provideInput,
    )

    public override fun build(): AIAgentPlannerStrategy<State, State, State> = AIAgentPlannerStrategy(
        name = name,
        planner = planner,
        initializeState = { it },
        provideOutput = { it }
    )
}

public class CustomPlannerStrategyBuilderWithInput<Input : Any, State : Any>(
    private val name: String,
    private val planner: AIAgentPlanner<State, *>,
    private val provideInput: (Input) -> State
) : TypedAgentPlannerStrategyBuilder<Input, State> {

    public fun <Output : Any> withOutput(
        provideOutput: (State) -> Output
    ): TypedCustomPlannerStrategyBuilder<Input, Output, State> = TypedCustomPlannerStrategyBuilder(
        name = name,
        planner = planner,
        provideInput = provideInput,
        provideOutput = provideOutput
    )

    public override fun build(): AIAgentPlannerStrategy<Input, State, State> = AIAgentPlannerStrategy(
        name = name,
        planner = planner,
        initializeState = provideInput,
        provideOutput = { it }
    )
}

public class TypedCustomPlannerStrategyBuilder<Input : Any, Output : Any, State : Any>(
    private val name: String,
    private val planner: AIAgentPlanner<State, *>,
    private val provideInput: (Input) -> State,
    private val provideOutput: (State) -> Output
) : TypedAgentPlannerStrategyBuilder<Input, Output> {

    public override fun build(): AIAgentPlannerStrategy<Input, Output, State> = AIAgentPlannerStrategy(
        name = name,
        planner = planner,
        initializeState = provideInput,
        provideOutput = provideOutput
    )
}
