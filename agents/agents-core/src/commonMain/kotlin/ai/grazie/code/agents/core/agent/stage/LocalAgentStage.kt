package ai.grazie.code.agents.core.agent.stage

import ai.grazie.code.agents.core.agent.graph.FinishAgentNode
import ai.grazie.code.agents.core.agent.graph.LocalAgentSubgraph
import ai.grazie.code.agents.core.agent.graph.StartAgentNode
import ai.grazie.code.agents.core.agent.graph.ToolSelectionStrategy
import ai.grazie.code.agents.core.tools.ToolDescriptor

sealed class LocalAgentStage(name: String, start: StartAgentNode) : LocalAgentSubgraph<Unit, String>(
    name, start, FinishAgentNode, ToolSelectionStrategy.ALL
) {
    suspend fun execute(context: LocalAgentStageContext): String = execute(context, Unit)
}

/**
 * Stage that expects a set of pre-defined tools.
 */
// TODO since tools are mutable now, seems like there's no purpose in having this stage type with tools checks?
class LocalAgentStaticStage internal constructor(
    name: String,
    startNode: StartAgentNode,
    private val toolsList: List<ToolDescriptor>
) : LocalAgentStage(name, startNode) {
    override suspend fun execute(context: LocalAgentStageContext, input: Unit): String {
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
class LocalAgentDynamicStage internal constructor(
    name: String,
    startNode: StartAgentNode,
) : LocalAgentStage(name, startNode) {
    override suspend fun execute(context: LocalAgentStageContext, input: Unit): String {
        return doExecute(context, Unit)
    }
}
