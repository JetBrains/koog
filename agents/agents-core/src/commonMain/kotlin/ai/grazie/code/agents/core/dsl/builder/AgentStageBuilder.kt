package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.agent.entity.FinishAgentNode
import ai.grazie.code.agents.core.agent.entity.AgentNode
import ai.grazie.code.agents.core.agent.entity.StartAgentNode
import ai.grazie.code.agents.core.agent.entity.stage.AgentDynamicStage
import ai.grazie.code.agents.core.agent.entity.stage.AgentStage
import ai.grazie.code.agents.core.agent.entity.stage.AgentStaticStage
import ai.grazie.code.agents.core.tools.ToolDescriptor
import kotlin.reflect.KProperty

class AgentStageBuilder(
    private val name: String,
    private val tools: List<ToolDescriptor>?
) : AgentSubgraphBuilderBase<Unit, String>(), BaseBuilder<AgentStage> {
    override val nodeStart = StartAgentNode()
    override val nodeFinish = FinishAgentNode

    override fun build(): AgentStage {
        require(isFinishReachable(nodeStart)) {
            "FinishNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return if (tools != null) {
            require(tools.isNotEmpty()) { "Tools list can't be empty if defined" }

            AgentStaticStage(
                name = name,
                startNode = nodeStart,
                toolsList = tools,
            )
        } else {
            AgentDynamicStage(
                name = name,
                startNode = nodeStart,
            )
        }
    }
}

interface AgentNodeDelegateBase<Input, Output> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): AgentNode<Input, Output>
}

open class AgentNodeDelegate<Input, Output> internal constructor(
    private val name: String?,
    private val nodeBuilder: AgentNodeBuilder<Input, Output>,
) : AgentNodeDelegateBase<Input, Output> {
    private var node: AgentNode<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): AgentNode<Input, Output> {
        if (node == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            node = nodeBuilder.also { it.name = name ?: property.name }.build()
        }

        return node!!
    }
}