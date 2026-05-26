package ai.koog.agents.core.feature.handler.node

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.serialization.TypeToken

/**
 * Represents the context for handling node-specific events within the framework.
 */
public interface NodeExecutionEventContext : AgentLifecycleEventContext {
    /**
     * The AI agent context.
     */
    public val context: AIAgentGraphContextBase

    /**
     * The AI Agent node instance.
     */
    public val node: AIAgentNodeBase<*, *>
}

/**
 * Represents the context for handling a before node execution event.
 *
 * @property input The node input data;
 * @property inputType [TypeToken] representing the type of the [input].
 */
public data class NodeExecutionStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentGraphContextBase,
    override val node: AIAgentNodeBase<*, *>,
    public val input: Any?,
    public val inputType: TypeToken,
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionStarting
}

/**
 * Represents the context for handling an after node execution event.
 *
 * @property input The node input data;
 * @property inputType [TypeToken] representing the type of the [input].
 * @property output The output data produced by the node execution.
 * @property outputType [TypeToken] representing the type of the [output].
 */
public data class NodeExecutionCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentGraphContextBase,
    override val node: AIAgentNodeBase<*, *>,
    public val input: Any?,
    public val inputType: TypeToken,
    public val output: Any?,
    public val outputType: TypeToken,
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionCompleted
}

/**
 * Represents the context for handling errors during the execution of a node.
 *
 * @property input The node input data;
 * @property inputType [TypeToken] representing the type of the [input].
 * @property error The exception or error that occurred during node execution.
 */
public data class NodeExecutionFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    override val context: AIAgentGraphContextBase,
    override val node: AIAgentNodeBase<*, *>,
    public val input: Any?,
    public val inputType: TypeToken,
    public val error: Throwable
) : NodeExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.NodeExecutionFailed
}
