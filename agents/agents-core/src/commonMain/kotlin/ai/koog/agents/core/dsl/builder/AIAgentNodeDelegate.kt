package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.utils.Some
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Creates a directed edge from this `AIAgentNodeBase` to another `AIAgentNodeBase`, allowing
 * data to flow from the output of the current node to the input of the specified node.
 *
 * @param otherNode The destination `AIAgentNodeBase` to which the current node's output is forwarded.
 * @return An `AIAgentEdgeBuilderIntermediate` that allows further customization
 * of the edge's data transformation and conditions between the nodes.
 */
@EdgeTransformationDslMarker
public infix fun <IncomingOutput, OutgoingInput> AIAgentNodeBase<*, IncomingOutput>.forwardTo(
    otherNode: AIAgentNodeBase<OutgoingInput, *>
): AIAgentEdgeBuilderIntermediate<IncomingOutput, IncomingOutput, OutgoingInput> {
    return AIAgentEdgeBuilderIntermediate(
        fromNode = this,
        toNode = otherNode,
        forwardOutputComposition = { _, output -> Some(output) }
    )
}

/**
 * A delegate for creating and managing an instance of [AIAgentNodeBase].
 *
 * This class simplifies the instantiation and management of AI agent nodes. It leverages
 * property delegation to lazily initialize a node instance.
 * The node's name can either be explicitly provided or derived from the delegated property name.
 *
 * @param Input The type of input data the delegated node will process.
 * @param Output The type of output data the delegated node will produce.
 * @constructor Initializes the delegate with the provided node name and builder.
 * @param name The optional name of the node. If not provided, the name will be derived from the
 * property to which the delegate is applied.
 */
public open class AIAgentNodeDelegate<Input, Output>(
    public val name: String?,
    public val inputType: KType,
    public val outputType: KType,
    public val execute: suspend AIAgentGraphContextBase.(Input) -> Output
) {
    private var node: AIAgentNodeBase<Input, Output>? = null

    /**
     * Retrieves an instance of [AIAgentNodeBase] associated with the given property.
     * This operator function acts as a delegate to dynamically provide a reference to an AI agent node.
     *
     * @param thisRef The object on which the property is accessed. This parameter can be null.
     * @param property The metadata of the property for which this delegate is being used.
     * @return The instance of [AIAgentNodeBase] corresponding to the property.
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): AIAgentNodeBase<Input, Output> {
        if (node == null) {
            node = AIAgentNode(
                // if name is explicitly defined, use it, otherwise use property name as node name
                name = name ?: property.name,
                inputType = inputType,
                outputType = outputType,
                execute = execute
            )
        }

        return node!!
    }

    /**
     * Creates a transformed version of this node delegate that applies a transformation to the output.
     *
     * @param T The type of the transformed output.
     * @param transformation A function that transforms the original output to the new type.
     * @return A new AIAgentNodeDelegate with the transformed output type.
     */
    public inline fun <reified T> transform(noinline transformation: suspend AIAgentGraphContextBase.(Output) -> T): AIAgentNodeDelegate<Input, T> {
        return AIAgentNodeDelegate(
            name = name,
            inputType = inputType,
            outputType = typeOf<T>(),
            execute = { input ->
                val result = execute.invoke(this, input)
                transformation(result)
            }
        )
    }
}
