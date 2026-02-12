package ai.koog.agents.core.optimization.features

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.optimization.core.OptimizableNode
import ai.koog.agents.core.optimization.core.Demonstration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Configuration for the [TraceCollectionFeature].
 *
 * @property collectOnlyOptimizable If true, only collect traces for [OptimizableNode] instances
 *  (i.e., nodes created with the `optimizableNode` DSL). Default is true.
 * @property maxTracesPerNode Maximum number of traces to collect per node. Once reached, older
 *  traces may be evicted (FIFO). Use 0 or negative for unlimited. Default is 100.
 * @property nodeNameFilter Node name filter. If non-empty, only nodes whose names are in this set
 *  will have traces collected. Empty set means collect all (no filtering).
 */
public class TraceCollectionConfig : FeatureConfig() {
    /**
     * If true, only collect traces for [OptimizableNode] instances.
     */
    public var collectOnlyOptimizable: Boolean = true

    /**
     * Maximum traces to store per node. 0 or negative = unlimited.
     */
    public var maxTracesPerNode: Int = 100

    /**
     * Node name filter. If non-empty, only nodes whose names are in this set will have traces collected.
     * Empty set means collect all (no filtering).
     */
    public var nodeNameFilter: Set<String> = emptySet()
}

/**
 * Collected traces for a single agent run.
 *
 * This class holds the demonstrations (input-output pairs) captured during execution.
 * It is safe for concurrent access from parallel node execution via [Mutex].
 * It also serves as the feature implementation stored in the agent's storage.
 *
 * All mutating and reading methods are suspend-functions to support coroutine-based locking.
 */
public class CollectedTraces(
    private val maxPerNode: Int
) {
    // NodeExecutionCompletedContext.input: Any? and output: Any? therefore here
    // Demonstration<Any?, Any?>
    private val _traces = mutableMapOf<String, MutableList<Demonstration<Any?, Any?>>>()
    private val mutex = Mutex()

    /**
     * Adds a demonstration for a node.
     */
    internal suspend fun addTrace(nodeName: String, input: Any?, output: Any?) {
        mutex.withLock {
            val nodeTraces = _traces.getOrPut(nodeName) { mutableListOf() }

            // Evict the oldest if at capacity
            if (maxPerNode > 0 && nodeTraces.size >= maxPerNode) {
                nodeTraces.removeAt(0)
            }

            nodeTraces.add(Demonstration(input, output, isBootstrapped = true))
        }
    }

    /**
     * Gets traces for a specific node.
     */
    public suspend fun getTracesForNode(nodeName: String): List<Demonstration<Any?, Any?>> {
        return mutex.withLock {
            _traces[nodeName]?.toList() ?: emptyList()
        }
    }
}

/**
 * Pipeline feature that captures inputs/outputs of nodes during execution.
 *
 * This feature is used during MIPRO bootstrapping to collect demonstrations from successful
 * executions. The collected traces can then be used as few-shot examples during optimization.
 *
 * By default, only [OptimizableNode] instances (created via `optimizableNode` DSL) have their I/O captured.
 * This behavior can be configured via [TraceCollectionConfig].
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(strategy = myStrategy) {
 *     collectTraces {
 *         collectOnlyOptimizable = true
 *         maxTracesPerNode = 50
 *     }
 * }
 *
 * // Run agent to collect traces
 * agent.run(input)
 *
 * // Access collected traces
 * val traces = agent.feature(TraceCollectionFeature)
 * val nodeTraces = traces?.getTracesForNode("myNode")
 * ```
 */
public object TraceCollectionFeature : AIAgentGraphFeature<TraceCollectionConfig, CollectedTraces> {

    override val key: AIAgentStorageKey<CollectedTraces> =
        AIAgentStorageKey("optimization-trace-collection")

    override fun createInitialConfig(): TraceCollectionConfig = TraceCollectionConfig()

    override fun install(
        config: TraceCollectionConfig,
        pipeline: AIAgentGraphPipeline
    ): CollectedTraces {
        val collectedTraces = CollectedTraces(config.maxTracesPerNode)

        pipeline.interceptNodeExecutionCompleted(this) { eventContext: NodeExecutionCompletedContext ->
            val node = eventContext.node

            if (node is AIAgentNode<*, *> && shouldCollectForNode(node, config)) {
                collectedTraces.addTrace(
                    nodeName = node.name,
                    input = eventContext.input,
                    output = eventContext.output
                )
            }
        }

        return collectedTraces
    }

    private fun shouldCollectForNode(node: AIAgentNode<*, *>, config: TraceCollectionConfig): Boolean {
        if (config.nodeNameFilter.isNotEmpty() && node.name !in config.nodeNameFilter) {
            return false
        }

        if (config.collectOnlyOptimizable && node !is OptimizableNode<*, *>) {
            return false
        }

        return true
    }
}

/**
 * Installs the TraceCollectionFeature with the given configuration.
 *
 * Example:
 * ```kotlin
 * val agent = AIAgent(strategy = myStrategy) {
 *     collectTraces {
 *         collectOnlyOptimizable = true
 *         maxTracesPerNode = 100
 *     }
 * }
 * ```
 */
public fun FeatureContext.collectTraces(configure: TraceCollectionConfig.() -> Unit = {}) {
    install(TraceCollectionFeature) {
        configure()
    }
}
