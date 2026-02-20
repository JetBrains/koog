package ai.koog.agents.planner

import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.agent.context.with
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.planner.goap.GoapAgentState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmStatic

/**
 * A strategy implementation that utilizes a planner to manage and execute AI agent workflows.
 *
 * This class integrates an AI planning component ([AIAgentPlanner]) with a defined initialization and output handling
 * mechanism, creating a customizable strategy for AI agents.
 *
 * @param Input The type of input data required as the starting point for the strategy execution.
 * @param Output The type of output data produced as the result of the strategy execution.
 * @param State The type representing the state managed and transformed by the underlying planner.
 * @property name The name of the strategy, allowing it to be identifiable in execution contexts.
 * @property planner The planner component responsible for creating and managing the execution plan.
 * @property initializeState A function that initializes the planner state based on the given input.
 * @property provideOutput A function that produces the strategy's output based on the final planner state.
 */
public class AIAgentPlannerStrategy<Input, Output, State>(
    override val name: String,
    private val planner: AIAgentPlanner<State, *>,
    private val initializeState: (Input) -> State,
    private val provideOutput: (State) -> Output,
) : AIAgentStrategy<Input, Output, AIAgentPlannerContext> {
    @OptIn(InternalAgentsApi::class)
    override suspend fun execute(
        context: AIAgentPlannerContext,
        input: Input
    ): Output {
        val state = initializeState(input)
        return try {
            context.with(partName = name) { executionInfo, eventId ->
                context.pipeline.onStrategyStarting(eventId, executionInfo, this, context)
                val result = planner.execute(context, initializeState(input))
                context.pipeline.onStrategyCompleted(eventId, executionInfo, this, context, result, planner.stateType)

                provideOutput(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.environment.reportProblem(e)
            throw e
        }
    }

    /**
     * Companion object with factories for [AIAgentPlannerStrategy]
     * */
    public companion object {
        /**
         * Creates an instance of [AIAgentPlannerStrategy] with the specified name and planner.
         *
         * @param name The name of the planner strategy to be created.
         * @param planner The planner instance that defines the specific planning logic.
         * @return A new [AIAgentPlannerStrategy] instance where the input, output, and state types are the same.
         */
        public operator fun <State> invoke(
            name: String,
            planner: AIAgentPlanner<State, *>,
        ): AIAgentPlannerStrategy<State, State, State> = AIAgentPlannerStrategy(name, planner, { it }, { it })

        /**
         * Creates a new instance of AIAgentPlannerStrategyBuilder with the specified name.
         *
         * @param name The name of the AI agent planner strategy.
         * @return AIAgentPlannerStrategyBuilder instance initialized with the given name.
         */
        @JvmStatic
        public fun builder(name: String): AIAgentPlannerStrategyBuilder = AIAgentPlannerStrategyBuilder(name)

        /**
         * Creates a Goal-Oriented Action Planning (GOAP) strategy for an AI agent.
         *
         * @param name The name of the GOAP strategy.
         * @param defineGoap A lambda to define actions, goals, and configuration for the GOAP strategy using a
         *                   `GOAPStrategyBuilder<Input, Output, State>`.
         * @return An instance of `AIAgentPlannerStrategy<Input, Output, State>` which represents the compiled
         *         GOAP strategy ready for execution.
         */
        public fun <Input : Any, Output : Any, State : GoapAgentState<Input, Output>> goap(
            name: String,
            initializeState: (Input) -> State,
            defineGoap: GOAPStrategyBuilder<Input, Output, State>.() -> Unit
        ): AIAgentPlannerStrategy<Input, Output, State> = AIAgentPlannerStrategyBuilder(name).goap(initializeState).apply {
            defineGoap()
        }.build()
    }
}
