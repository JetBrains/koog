package ai.grazie.code.agents.local.dsl.builders

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.local.agent.stage.LocalAgentDynamicStage
import ai.grazie.code.agents.local.agent.stage.LocalAgentStage
import ai.grazie.code.agents.local.agent.stage.LocalAgentStaticStage
import ai.grazie.code.agents.local.graph.FinishAgentNode
import ai.grazie.code.agents.local.graph.LocalAgentNode
import ai.grazie.code.agents.local.graph.StartAgentNode
import kotlin.reflect.KProperty

class LocalAgentStageBuilder(
    private val name: String,
    private val tools: List<ToolDescriptor>?
) : LocalAgentSubgraphBuilderBase<Unit, String>(), BaseBuilder<LocalAgentStage> {
    override val nodeStart = StartAgentNode()
    override val nodeFinish = FinishAgentNode

    override fun build(): LocalAgentStage {
        require(isFinishReachable(nodeStart)) {
            "FinishNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return if (tools != null) {
            require(tools.isNotEmpty()) { "Tools list can't be empty if defined" }

            LocalAgentStaticStage(
                name = name,
                startNode = nodeStart,
                toolsList = tools,
            )
        } else {
            LocalAgentDynamicStage(
                name = name,
                startNode = nodeStart,
            )
        }
    }
}

interface LocalAgentNodeDelegateBase<Input, Output> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalAgentNode<Input, Output>
}

open class LocalAgentNodeDelegate<Input, Output> internal constructor(
    private val name: String?,
    private val nodeBuilder: LocalAgentNodeBuilder<Input, Output>,
) : LocalAgentNodeDelegateBase<Input, Output> {
    private var node: LocalAgentNode<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalAgentNode<Input, Output> {
        if (node == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            node = nodeBuilder.also { it.name = name ?: property.name }.build()
        }

        return node!!
    }
}