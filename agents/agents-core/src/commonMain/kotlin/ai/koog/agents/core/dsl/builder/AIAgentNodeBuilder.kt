package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.utils.Some
import kotlin.reflect.KProperty
import kotlin.reflect.KType

/**
 * A builder class for constructing instances of [AIAgentNodeBase] with specific behavior and attributes.
 *
 * This class is responsible for managing the configuration of an AI agent node, including its name
 * and execution logic. It provides a mechanism for defining a unit of execution that operates
 * within the context of an AI agent, linking an input type [Input] to an output type [Output].
 *
 * @param Input The type of input data the node will process.
 * @param Output The type of output data the node will produce.
 * @param inputType [KType] of the [Input]
 * @param outputType [KType] of the [Output]
 * @constructor Used internally to create a new builder with the provided execution logic.
 * @param execute A suspending function to define the execution logic of the node. This function will be called
 * in the scope of [AIAgentContextBase], where it has access to the AI agent's context and tools relevant
 * to its operation.
 */
public open class AIAgentNodeBuilder<Input, Output>(
    private val inputType: KType,
    private val outputType: KType,
    private val execute: suspend AIAgentContextBase.(Input) -> Output
) : BaseBuilder<AIAgentNodeBase<Input, Output>> {
    /**
     * The name of the AI agent node being built.
     *
     * This property must be initialized before building the instance, as it serves as the unique identifier
     * for the node within the agent's configuration. The name can be used to reference the node
     * in the context of an AI agent's execution graph or strategy.
     *
     * The `name` value directly influences the identification of the node in the agent's internal architecture
     * and is essential for maintaining clarity and structure in complex configurations.
     */
    public lateinit var name: String

    override fun build(): AIAgentNodeBase<Input, Output> {
        return AIAgentNode(
            name = name,
            inputType = inputType,
            outputType = outputType,
            execute = execute
        )
    }
}

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
 * property delegation to lazily initialize a node instance using a given [AIAgentNodeBuilder].
 * The node's name can either be explicitly provided or derived from the delegated property name.
 *
 * @param Input The type of input data the delegated node will process.
 * @param Output The type of output data the delegated node will produce.
 * @constructor Initializes the delegate with the provided node name and builder.
 * @param name The optional name of the node. If not provided, the name will be derived from the
 * property to which the delegate is applied.
 * @param nodeBuilder The builder used to construct the [AIAgentNodeBase] instance for this delegate.
 */
public open class AIAgentNodeDelegate<Input, Output>(
    private val name: String?,
    private val nodeBuilder: AIAgentNodeBuilder<Input, Output>,
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
            // if name is explicitly defined, use it, otherwise use property name as node name
            node = nodeBuilder.also { it.name = name ?: property.name }.build()
        }

        return node!!
    }
}
