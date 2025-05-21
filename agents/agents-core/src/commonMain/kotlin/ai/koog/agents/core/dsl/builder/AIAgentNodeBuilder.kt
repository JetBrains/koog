package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.utils.Some
import kotlin.reflect.KProperty

public open class AIAgentNodeBuilder<Input, Output> internal constructor(
    private val execute: suspend AIAgentContextBase.(Input) -> Output
) : BaseBuilder<AIAgentNodeBase<Input, Output>> {
    public lateinit var name: String

    override fun build(): AIAgentNodeBase<Input, Output> {
        return AIAgentNode(
            name = name,
            execute = execute
        )
    }
}

public infix fun <IncomingOutput, OutgoingInput> AIAgentNodeBase<*, IncomingOutput>.forwardTo(
    otherNode: AIAgentNodeBase<OutgoingInput, *>
): AIAgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return AIAgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}

public interface AIAgentNodeDelegateBase<Input, Output> {
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<Input, Output>
}

public open class AIAgentNodeDelegate<Input, Output> internal constructor(
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