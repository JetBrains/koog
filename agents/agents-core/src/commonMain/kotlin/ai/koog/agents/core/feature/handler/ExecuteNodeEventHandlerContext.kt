package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import kotlin.reflect.KType

/**
 * Represents the context for handling node-specific events within the framework.
 */
public interface NodeEventHandlerContext : EventHandlerContext

/**
 * Represents the context for handling a before node execution event.
 *
 * @property context The stage context in which the node is being executed.
 * @property node The node that is about to be executed.
 * @property input The input data for the node execution.
 * @property inputType [KType] representing the type of the [input].
 */
public data class NodeBeforeExecuteContext(
    val context: AIAgentContextBase,
    val node: AIAgentNodeBase<*, *>,
    val input: Any?,
    val inputType: KType,
) : NodeEventHandlerContext

/**
 * Represents the context for handling an after node execution event.
 *
 * @property context The stage context in which the node was executed.
 * @property node The node that was executed.
 * @property input The input data that was provided to the node.
 * @property output The output data produced by the node execution.
 * @property inputType [KType] representing the type of the [input].
 * @property outputType [KType] representing the type of the [output].
 */
public data class NodeAfterExecuteContext(
    val context: AIAgentContextBase,
    val node: AIAgentNodeBase<*, *>,
    val input: Any?,
    val output: Any?,
    val inputType: KType,
    val outputType: KType,
) : NodeEventHandlerContext
