package ai.grazie.code.agents.core.dsl.builder

import ai.grazie.code.agents.core.agent.entity.FinishAIAgentNode
import ai.grazie.code.agents.core.agent.entity.FinishAIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.StartAIAgentNode
import ai.grazie.code.agents.core.agent.entity.StartAIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentDynamicStage
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStage
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStaticStage
import ai.grazie.code.agents.core.tools.ToolDescriptor
import kotlin.reflect.KProperty

internal class AIAgentStageBuilder(
    private val name: String,
    private val tools: List<ToolDescriptor>?
) : AIAgentSubgraphBuilderBase<Unit, String>(), BaseBuilder<AIAgentStage> {
    override val nodeStart: StartAIAgentNodeBase<Unit> = StartAIAgentNode()
    override val nodeFinish: FinishAIAgentNodeBase<String> = FinishAIAgentNode

    override fun build(): AIAgentStage {
        require(isFinishReachable(nodeStart)) {
            "FinishNode can't be reached from the StartNode of the agent's graph. Please, review how it was defined."
        }

        return if (tools != null) {
            require(tools.isNotEmpty()) { "Tools list can't be empty if defined" }

            AIAgentStaticStage(
                name = name,
                startNode = nodeStart,
                toolsList = tools,
            )
        } else {
            AIAgentDynamicStage(
                name = name,
                startNode = nodeStart,
            )
        }
    }
}

public interface AIAgentNodeDelegateBase<Input, Output> {
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<Input, Output>
}

internal open class AIAgentNodeDelegate<Input, Output> internal constructor(
    private val name: String?,
    private val nodeBuilder: AIAgentNodeBuilder<Input, Output>,
) : AIAgentNodeDelegateBase<Input, Output> {
    private var node: AIAgentNodeBase<Input, Output>? = null

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<Input, Output> {
        if (node == null) {
            // if name is explicitly defined, use it, otherwise use property name as node name
            node = nodeBuilder.also { it.name = name ?: property.name }.build()
        }

        return node!!
    }
}
