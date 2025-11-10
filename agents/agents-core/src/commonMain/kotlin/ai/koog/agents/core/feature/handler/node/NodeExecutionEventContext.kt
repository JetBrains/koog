package ai.koog.agents.core.feature.handler.node

import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import kotlin.reflect.KType

/**
 * Represents the context for handling node-specific events within the framework.
 */
public interface NodeExecutionEventContext : AgentLifecycleEventContext

/**
 * Represents the context for handling a before node execution event.
 *
 * @property node The node that is about to be executed.
 * @property context The stage context in which the node is being executed.
 * @property input The input data for the node execution.
 * @property inputType [KType] representing the type of the [input].
 */
public data class NodeExecutionStartingContext(
    val node: AIAgentNodeBase<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val inputType: KType,
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionStarting
}

/**
 * Represents the context for handling an after node execution event.
 *
 * @property node The node that was executed.
 * @property context The stage context in which the node was executed.
 * @property input The input data that was provided to the node.
 * @property inputType [KType] representing the type of the [input].
 * @property output The output data produced by the node execution.
 * @property outputType [KType] representing the type of the [output].
 */
public data class NodeExecutionCompletedContext(
    val node: AIAgentNodeBase<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val inputType: KType,
    val output: Any?,
    val outputType: KType,
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionCompleted
}

/**
 * Represents the context for handling errors during the execution of an AI agent node.
 *
 * This context is typically used to capture information about the execution state and the
 * error that occurred during the lifecycle of an AI agent node in a strategy graph. It provides
 * details such as the identifier of the current run, the node where the error occurred, the
 * execution context, and the specific error itself.
 *
 * @property node The AI agent node where the error occurred. This is an instance of [AIAgentNodeBase].
 * @property context The execution context, encapsulated by [AIAgentGraphContext], which provides
 * runtime information and utilities for executing the node.
 * @property input The input data provided to the node.
 * @property inputType [KType] representing the type of the [input].
 * @property throwable The exception or error encountered during the node execution.
 */
public data class NodeExecutionFailedContext(
    val node: AIAgentNodeBase<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val inputType: KType,
    val throwable: Throwable
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionFailed
}
