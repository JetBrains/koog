package ai.koog.agents.planner.goap

import ai.koog.agents.core.agent.context.withUpdatedContext
import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

/**
 * Builder for creating [Action] instances using functional approach with execute.
 */
public class FunctionalActionBuilder<State> internal constructor(
    name: String?,
    description: String?,
    precondition: Condition<State>?,
    belief: Belief<State>?,
    cost: Cost<State>,
    tools: List<Tool<*, *>>?,
    llmModel: LLModel?,
    llmParams: LLMParams?,
    private val execute: Execute<State>
) : ActionTerminalBuilder<State>, ActionBuilderCommonBase<State, FunctionalActionBuilder<State>>(
    name = name,
    description = description,
    precondition = precondition,
    belief = belief,
    cost = cost,
    tools = tools,
    llmModel = llmModel,
    llmParams = llmParams
) {

    override fun build(): Action<State> = Action(
        name = nameNotNull,
        description = description,
        precondition = preconditionNotNull,
        belief = beliefNotNull,
        cost = cost,
    ) { context, state ->
        withUpdatedContext(
            context = context,
            tools = tools?.map { it.descriptor },
            llmModel = llmModel,
            llmParams = llmParams,
        ) {
            execute(context, state)
        }
    }

    override fun self(): FunctionalActionBuilder<State> = this
}
