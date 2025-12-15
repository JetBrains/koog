package ai.koog.agents.core.feature.handler.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
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
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property node The node that is about to be executed.
 * @property context The stage context in which the node is being executed.
 * @property input The input data for the node execution.
 * @property inputType [KType] representing the type of the [input].
 */
public data class NodeExecutionStartingContext(
    override val executionInfo: AgentExecutionInfo,
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
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property node The node that was executed.
 * @property context The stage context in which the node was executed.
 * @property input The input data that was provided to the node.
 * @property inputType [KType] representing the type of the [input].
 * @property output The output data produced by the node execution.
 * @property outputType [KType] representing the type of the [output].
 */
public data class NodeExecutionCompletedContext(
    override val executionInfo: AgentExecutionInfo,
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
 * Represents the context for handling errors during the execution of a node.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property node The node where the error occurred.
 * @property context The stage context in which the node experienced the error.
 * @property input The input data for the node execution.
 * @property inputType [KType] representing the type of the [input].
 * @property throwable The exception or error that occurred during node execution.
 */
public data class NodeExecutionFailedContext(
    override val executionInfo: AgentExecutionInfo,
    val node: AIAgentNodeBase<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val inputType: KType,
    val throwable: Throwable
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionFailed
}
