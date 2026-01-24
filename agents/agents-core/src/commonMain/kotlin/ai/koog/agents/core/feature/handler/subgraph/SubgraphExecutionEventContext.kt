package ai.koog.agents.core.feature.handler.subgraph

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import kotlin.reflect.KType

/**
 * Represents the context for handling subgraph-specific events for graph strategies within the framework.
 */
public interface SubgraphExecutionEventContext : AgentLifecycleEventContext

/**
 * The context for handling a subgraph execution starting event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property subgraph The subgraph instance that is about to be executed.
 * @property context The context in which the subgraph is being executed.
 * @property input The input data for the subgraph execution.
 * @property inputType The type of the input data for the subgraph execution.
 */
public data class SubgraphExecutionStartingContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val subgraph: AIAgentSubgraph<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val inputType: KType,
) : SubgraphExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.SubgraphExecutionStarting
}

/**
 * The context for handling a subgraph execution completed event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property subgraph The subgraph instance that was executed.
 * @property context The context in which the subgraph was executed.
 * @property input The input data for the subgraph execution.
 * @property output The output data from the subgraph execution.
 * @property inputType The type of the input data for the subgraph execution.
 * @property outputType The type of the output data for the subgraph execution.
 */
public data class SubgraphExecutionCompletedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val subgraph: AIAgentSubgraph<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val output: Any?,
    val inputType: KType,
    val outputType: KType,
) : SubgraphExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.SubgraphExecutionCompleted
}

/**
 * The context for handling a subgraph execution failed event.
 *
 * @property executionInfo The execution information containing parentId and current execution path;
 * @property subgraph The subgraph instance that failed to execute.
 * @property context The context in which the subgraph failed to execute.
 * @property input The input data for the subgraph execution.
 * @property inputType The type of the input data for the subgraph execution.
 * @property throwable The exception that caused the subgraph execution to fail.
 */
public data class SubgraphExecutionFailedContext(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val subgraph: AIAgentSubgraph<*, *>,
    val context: AIAgentGraphContextBase,
    val input: Any?,
    val inputType: KType,
    val throwable: Throwable
) : SubgraphExecutionEventContext {
    override val eventType: AgentLifecycleEventType = AgentLifecycleEventType.SubgraphExecutionFailed
}
