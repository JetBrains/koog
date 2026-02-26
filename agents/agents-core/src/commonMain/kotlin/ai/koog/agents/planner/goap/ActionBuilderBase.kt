package ai.koog.agents.planner.goap

import kotlin.reflect.KClass

/**
 * Common api for
 */
public abstract class ActionBuilderBase<State> : ActionBuilderCommon<State, ActionBuilder<State>>() {
    /**
     * Sets action execution function.
     */
    public fun execute(execute: Execute<State>): FunctionalActionBuilder<State> =
        FunctionalActionBuilder(
            name = name,
            description = description,
            precondition = precondition,
            belief = belief,
            cost = cost,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            execute = execute
        )

    /**
     * Sets the task description for a subtask-based action.
     */
    public fun <T : Any> taskDescription(taskDescription: (State) -> String): SubtaskActionBuilder<State, T> =
        SubtaskActionBuilder(
            name = name,
            description = description,
            precondition = precondition,
            belief = belief,
            cost = cost,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            taskDescription = taskDescription
        )

    /**
     * Sets the updateState method for a subtask-based action.
     */
    public fun <T : Any> updateState(updateState: (State, T) -> State): SubtaskActionBuilder<State, T> =
        SubtaskActionBuilder(
            name = name,
            description = description,
            precondition = precondition,
            belief = belief,
            cost = cost,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            updateState = updateState
        )

    /**
     * Sets the subtask output class for a subtask-based action.
     */
    public fun <T : Any> structuredOutputClass(outputClass: KClass<T>): SubtaskActionBuilder<State, T> =
        SubtaskActionBuilder(
            name = name,
            description = description,
            precondition = precondition,
            belief = belief,
            cost = cost,
            tools = tools,
            llmModel = llmModel,
            llmParams = llmParams,
            outputClass = outputClass
        )
}
