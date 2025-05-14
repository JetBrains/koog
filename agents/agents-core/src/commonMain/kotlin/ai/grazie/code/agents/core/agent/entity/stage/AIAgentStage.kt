package ai.grazie.code.agents.core.agent.entity.stage

import ai.grazie.code.agents.core.agent.entity.FinishAIAgentNode
import ai.grazie.code.agents.core.agent.entity.AIAgentSubgraph
import ai.grazie.code.agents.core.agent.entity.StartAIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.grazie.code.agents.core.tools.ToolDescriptor

public sealed class AIAgentStage(name: String, start: StartAIAgentNodeBase<Unit>) : AIAgentSubgraph<Unit, String>(
    name, start, FinishAIAgentNode, ToolSelectionStrategy.ALL
) {
    public suspend fun execute(context: AIAgentStageContextBase): String = execute(context, Unit)
}

/**
 * Stage that expects a set of pre-defined tools.
 */
// TODO since tools are mutable now, seems like there's no purpose in having this stage type with tools checks?
public class AIAgentStaticStage internal constructor(
    name: String,
    startNode: StartAIAgentNodeBase<Unit>,
    private val toolsList: List<ToolDescriptor>
) : AIAgentStage(name, startNode) {
    override suspend fun execute(context: AIAgentStageContextBase, input: Unit): String {
        context.llm.readSession {
            tools.filterNot { it in toolsList }.let { unexpectedTools ->
                check(unexpectedTools.isEmpty()) {
                    "Following implemented tools for stage $name were not expected: " +
                            unexpectedTools.joinToString(", ")
                }
            }
            toolsList.filterNot { it in tools }.let { missingTools ->
                check(missingTools.isEmpty()) {
                    "Following tool implementations for stage $name are missing: " +
                            missingTools.joinToString(", ")
                }
            }
        }

        return doExecute(context, Unit)
    }
}

/**
 * Stage that does not expect any pre-defined tools, relying on the tools defined in the graph.
 */
public class AIAgentDynamicStage internal constructor(
    name: String,
    startNode: StartAIAgentNodeBase<Unit>,
) : AIAgentStage(name, startNode) {
    override suspend fun execute(context: AIAgentStageContextBase, input: Unit): String {
        return doExecute(context, Unit)
    }
}
