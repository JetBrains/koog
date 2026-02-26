package ai.koog.agents.planner.goap

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

/**
 * Common functionality for building [Action] instances.
 */
public abstract class ActionBuilderCommon<State, TBuilder : ActionBuilderCommon<State, TBuilder>> internal constructor(
    protected var name: String? = null,
    protected var description: String? = null,
    protected var precondition: Condition<State>? = null,
    protected var belief: Belief<State>? = null,
    protected var cost: Cost<State> = { 1.0 },
    protected var tools: List<Tool<*, *>>? = null,
    protected var llmModel: LLModel? = null,
    protected var llmParams: LLMParams? = null
) {

    protected val nameNotNull: String get() = requireNotNull(name) { "Action name is required" }
    protected val beliefNotNull: Belief<State> get() = requireNotNull(belief) { "Belief is required" }
    protected val preconditionNotNull: Condition<State> get() = requireNotNull(precondition) { "Precondition is required" }

    protected abstract fun self(): TBuilder

    /**
     * Sets the name of the action.
     */
    public fun name(name: String): TBuilder = self().apply { this.name = name }

    /**
     * Sets the description of the action.
     */
    public fun description(description: String?): TBuilder = self().apply { this.description = description }

    /**
     * Sets the precondition for the action.
     */
    public fun precondition(precondition: Condition<State>): TBuilder = self().apply { this.precondition = precondition }

    /**
     * Sets the belief for the action.
     */
    public fun belief(belief: Belief<State>): TBuilder = self().apply { this.belief = belief }

    /**
     * Sets the cost function for the action.
     */
    public fun cost(cost: Cost<State>): TBuilder = self().apply { this.cost = cost }

    /**
     * Sets the tools available during action execution.
     */
    public fun tools(tools: List<Tool<*, *>>?): TBuilder = self().apply { this.tools = tools }

    /**
     * Sets the llm model to use for the action.
     */
    public fun llmModel(llmModel: LLModel?): TBuilder = self().apply { this.llmModel = llmModel }

    /**
     * Sets the llm parameters for the action.
     */
    public fun llmParams(llmParams: LLMParams?): TBuilder = self().apply { this.llmParams = llmParams }
}
