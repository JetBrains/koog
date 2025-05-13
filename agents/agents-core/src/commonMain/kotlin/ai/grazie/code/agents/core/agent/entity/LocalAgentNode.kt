package ai.grazie.code.agents.core.agent.entity

import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi

abstract class LocalAgentNode<Input, Output> internal constructor() {
    abstract val name: String

    var edges: List<LocalAgentEdge<Output, *>> = emptyList()
        private set

    open fun addEdge(edge: LocalAgentEdge<Output, *>) {
        edges = edges + edge
    }


    data class ResolvedEdge(val edge: LocalAgentEdge<*, *>, val output: Any?)
    suspend fun resolveEdge(
        context: LocalAgentStageContext,
        nodeOutput: Output
    ): ResolvedEdge? {
        var edge: LocalAgentEdge<*, *>? = null

        for (currentEdge in edges) {
            val output = currentEdge.forwardOutputUnsafe(nodeOutput, context)

            if (!output.isEmpty) {
                edge = currentEdge
                return ResolvedEdge(currentEdge, output.value)
            }
        }

        return null
    }

    /**
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun resolveEdgeUnsafe(context: LocalAgentStageContext, nodeOutput: Any?) =
        resolveEdge(context, nodeOutput as Output)

    abstract suspend fun execute(context: LocalAgentStageContext, input: Input): Output

    /**
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    suspend fun executeUnsafe(context: LocalAgentStageContext, input: Any?): Any? {
        context.pipeline.onBeforeNode(this, context, input)
        val output = execute(context, input as Input)
        context.pipeline.onAfterNode(this, context, input, output)

        return output
    }
}

class SimpleLocalAgentNode<Input, Output> internal constructor(
    override val name: String,
    val execute: suspend LocalAgentStageContext.(input: Input) -> Output
) : LocalAgentNode<Input, Output>() {
    override suspend fun execute(context: LocalAgentStageContext, input: Input): Output = context.execute(input)
}

class StartAgentNode internal constructor() : StartNode<Unit>()

object FinishAgentNode : FinishNode<String>()
