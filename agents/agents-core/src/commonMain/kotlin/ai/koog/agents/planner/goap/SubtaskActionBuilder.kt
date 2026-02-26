package ai.koog.agents.planner.goap

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlin.reflect.KClass

/**
 * Builder for creating GOAP actions that use structured subtasks with LLM output.
 *
 * This builder constructs actions where the execute function automatically runs a subtask
 *
 * @param State The type of the GOAP agent state.
 * @param T The type of structured output expected from the LLM subtask.
 */
public class SubtaskActionBuilder<State, T : Any>(
    name: String? = null,
    description: String?,
    precondition: Condition<State>?,
    belief: Belief<State>?,
    cost: Cost<State>,
    tools: List<Tool<*, *>>?,
    llmModel: LLModel?,
    llmParams: LLMParams?,
    private var outputClass: KClass<T>? = null,
    private var taskDescription: ((State) -> String)? = null,
    private var updateState: ((State, T) -> State)? = null,
) : ActionTerminalBuilder<State>, ActionBuilderCommon<State, SubtaskActionBuilder<State, T>>(
    name = name,
    description = description,
    precondition = precondition,
    belief = belief,
    cost = cost,
    tools = tools,
    llmModel = llmModel,
    llmParams = llmParams,
) {

    /**
     * Sets the task description function that generates the task description from the current state.
     */
    public fun taskDescription(taskDescription: (State) -> String): SubtaskActionBuilder<State, T> =
        apply { this.taskDescription = taskDescription }

    /**
     * Sets the state update function that applies the structured result [T] to update the state.
     */
    public fun updateState(updateState: (State, T) -> State): SubtaskActionBuilder<State, T> =
        apply { this.updateState = updateState }

    /**
     * Sets the output class for the structured subtask result.
     */
    public fun outputClass(outputClass: KClass<T>): SubtaskActionBuilder<State, T> =
        apply { this.outputClass = outputClass }

    private val taskDescriptionNotNull: (State) -> String get() = requireNotNull(taskDescription) { "Task description is required" }
    private val updateStateNotNull: (State, T) -> State get() = requireNotNull(updateState) { "Update state is required" }
    private val outputClassNotNull: KClass<T> get() = requireNotNull(outputClass) { "Output class is required" }

    override fun build(): Action<State> {
        return Action(
            name = nameNotNull,
            description = description,
            precondition = preconditionNotNull,
            belief = beliefNotNull,
            cost = cost,
        ) { context, state ->
            val result = context.subtask(
                taskDescription = taskDescriptionNotNull(state),
                input = state,
                outputClass = outputClassNotNull,
                tools = tools,
                llmModel = llmModel,
                llmParams = llmParams
            )
            updateStateNotNull(state, result)
        }
    }

    override fun self(): SubtaskActionBuilder<State, T> = this
}
