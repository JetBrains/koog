package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.core.agent.entity.ContextTransitionPolicy
import ai.grazie.code.agents.core.agent.entity.AgentStrategy

class AgentStrategyBuilder(
    private val name: String,
    private val llmHistoryTransitionPolicy: ContextTransitionPolicy,
) : BaseBuilder<AgentStrategy> {

    private var stageBuilders = mutableListOf<AgentStageBuilder>()

    fun stage(
        name: String = ToolStage.DEFAULT_STAGE_NAME,
        requiredTools: List<ToolDescriptor>? = null,
        init: AgentStageBuilder.() -> Unit
    ) {
        stageBuilders += AgentStageBuilder(name, requiredTools).apply(init)
    }

    override fun build(): AgentStrategy {
        val stages = stageBuilders.map { it.build() }

        require(stages.isNotEmpty()) { "Agent must have at least one stage" }

        return AgentStrategy(
            name = name,
            stages = stages,
            llmHistoryTransitionPolicy = llmHistoryTransitionPolicy
        )
    }
}

/**
 * Builds an AI agent that processes user input through a sequence of stages.
 *
 * The agent executes a series of stages in sequence, with each stage receiving the output
 * of the previous stage as its input. The agent manages the LLM conversation history between stages
 * according to the specified [llmHistoryTransitionPolicy].
 *
 * @property name The unique identifier for this agent.
 * @param llmHistoryTransitionPolicy Determines how the LLM conversation history is handled between stages.
 *        - [ContextTransitionPolicy.PERSIST_LLM_HISTORY]: Keeps the entire conversation history intact (default).
 *        - [ContextTransitionPolicy.COMPRESS_LLM_HISTORY]: Compresses the history between stages to reduce token usage.
 *        - [ContextTransitionPolicy.CLEAR_LLM_HISTORY]: Clears the history between stages for independent processing.
 * @param init Lambda that defines stages and nodes of this agent
 */
fun strategy(
    name: String,
    llmHistoryTransitionPolicy: ContextTransitionPolicy = ContextTransitionPolicy.PERSIST_LLM_HISTORY,
    init: AgentStrategyBuilder.() -> Unit,
): AgentStrategy {
    return AgentStrategyBuilder(name, llmHistoryTransitionPolicy).apply(init).build()
}

/**
 * Builds a simple AI agent that processes user input through a sequence of stages.
 *
 * The agent executes a single stage and returns a String result
 */
fun simpleStrategy(
    name: String,
    init: AgentStageBuilder.() -> Unit,
): AgentStrategy {

    return strategy(name) {
        stage {
            init()
        }
    }
}