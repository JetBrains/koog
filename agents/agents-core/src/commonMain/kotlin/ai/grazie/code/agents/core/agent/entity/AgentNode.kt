package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.agent.entity.stage.AgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi

abstract class AgentNode<Input, Output> internal constructor() {
    abstract val name: String

    var edges: List<AgentEdge<Output, *>> = emptyList()
        private set

    open fun addEdge(edge: AgentEdge<Output, *>) {
        edges = edges + edge
    }


    data class ResolvedEdge(val edge: AgentEdge<*, *>, val output: Any?)
    suspend fun resolveEdge(
        context: AgentStageContext,
        nodeOutput: Output
    ): ResolvedEdge? {
        var edge: AgentEdge<*, *>? = null

        for (currentEdge in edges) {
            val output = currentEdge.forwardOutputUnsafe(nodeOutput, context)

            if (!output.isEmpty) {
                edge = currentEdge
                return ResolvedEdge(currentEdge, output.value)
            }
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun resolveEdgeUnsafe(context: AgentStageContext, nodeOutput: Any?) =
        resolveEdge(context, nodeOutput as Output)

    abstract suspend fun execute(context: AgentStageContext, input: Input): Output

    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun executeUnsafe(context: AgentStageContext, input: Any?): Any? {
        context.pipeline.onBeforeNode(this, context, input)
        val output = execute(context, input as Input)
        context.pipeline.onAfterNode(this, context, input, output)

        return output
    }
}

class SimpleAgentNode<Input, Output> internal constructor(
    override val name: String,
    val execute: suspend AgentStageContext.(input: Input) -> Output
) : AgentNode<Input, Output>() {
    override suspend fun execute(context: AgentStageContext, input: Input): Output = context.execute(input)
}

class StartAgentNode internal constructor() : StartNode<Unit>()

object FinishAgentNode : FinishNode<String>()