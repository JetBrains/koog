package ai.koog.agents.core.optimization.features

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.entity.AIAgentNode
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.optimization.core.Demonstration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Configuration for the [TraceCollectionFeature].
 *
 * @property collectOnlyOptimizable If true, only collect traces for nodes that have a non-null
 *  instruction (i.e., nodes marked for optimization). Default is true.
 * @property maxTracesPerNode Maximum number of traces to collect per node. Once reached, older
 *  traces may be evicted (FIFO). Use 0 or negative for unlimited. Default is 100.
 */
public class TraceCollectionConfig : FeatureConfig() {
    /**
     * If true, only collect traces for nodes with optimization metadata (instruction != null).
     */
    public var collectOnlyOptimizable: Boolean = true

    /**
     * Maximum traces to store per node. 0 or negative = unlimited.
     */
    public var maxTracesPerNode: Int = 100

    /**
     * Node name filter. If non-null, only nodes whose names are in this set will have traces collected.
     */
    public var nodeNameFilter: Set<String>? = null
}

/**
 * Collected traces for a single agent run.
 *
 * This class holds the demonstrations (input-output pairs) captured during execution.
 * It is safe for concurrent access from parallel node execution via [Mutex].
 *
 * All mutating and reading methods are suspend functions to support coroutine-based locking.
 */
public class CollectedTraces {
    private val _traces = mutableMapOf<String, MutableList<Demonstration<Any?, Any?>>>()
    private val mutex = Mutex()

    /**
     * Gets all collected traces as an immutable snapshot.
     */
    public suspend fun getTraces(): Map<String, List<Demonstration<Any?, Any?>>> = mutex.withLock {
        _traces.mapValues { it.value.toList() }
    }

    /**
     * Adds a demonstration for a node.
     */
    internal suspend fun addTrace(nodeName: String, input: Any?, output: Any?, maxPerNode: Int) {
        mutex.withLock {
            val nodeTraces = _traces.getOrPut(nodeName) { mutableListOf() }

            // Evict oldest if at capacity
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

    /**
     * Gets typed traces for a specific node.
     *
     * Performs unchecked casts - caller must ensure type compatibility.
     */
    @Suppress("UNCHECKED_CAST")
    public suspend fun <TInput, TOutput> getTypedTracesForNode(
        nodeName: String
    ): List<Demonstration<TInput, TOutput>> {
        return getTracesForNode(nodeName) as List<Demonstration<TInput, TOutput>>
    }

    /**
     * Clears all collected traces.
     */
    public suspend fun clear() {
        mutex.withLock {
            _traces.clear()
        }
    }

    /**
     * Gets the total number of traces collected across all nodes.
     */
    public suspend fun getTotalTraceCount(): Int = mutex.withLock {
        _traces.values.sumOf { it.size }
    }

    /**
     * Gets the names of nodes that have collected traces.
     */
    public suspend fun getNodeNames(): Set<String> = mutex.withLock {
        _traces.keys.toSet()
    }
}

/**
 * Feature implementation holder.
 *
 * This class is stored in the agent's storage and provides access to collected traces.
 */
public class TraceCollectionFeatureImpl(
    private val config: TraceCollectionConfig
) {
    /**
     * The collected traces from agent execution.
     */
    public val collectedTraces: CollectedTraces = CollectedTraces()

    internal fun shouldCollectForNode(node: AIAgentNode<*, *>): Boolean {
        // Check node name filter
        val nameFilter = config.nodeNameFilter
        if (nameFilter != null && node.name !in nameFilter) {
            return false
        }

        // Check optimizable filter
        if (config.collectOnlyOptimizable && node.instruction == null) {
            return false
        }

        return true
    }

    internal suspend fun addTrace(nodeName: String, input: Any?, output: Any?) {
        collectedTraces.addTrace(nodeName, input, output, config.maxTracesPerNode)
    }
}

/**
 * Pipeline feature that captures inputs/outputs of nodes during execution.
 *
 * This feature is used during MIPRO bootstrapping to collect demonstrations from successful
 * executions. The collected traces can then be used as few-shot examples during optimization.
 *
 * By default, only nodes with optimization metadata (instruction != null) have their I/O captured.
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
 * val traces = agent.feature(TraceCollectionFeature)?.collectedTraces
 * val nodeTraces = traces?.getTracesForNode("myNode")
 * ```
 */
public object TraceCollectionFeature : AIAgentGraphFeature<TraceCollectionConfig, TraceCollectionFeatureImpl> {

    override val key: AIAgentStorageKey<TraceCollectionFeatureImpl> =
        AIAgentStorageKey("optimization-trace-collection")

    override fun createInitialConfig(): TraceCollectionConfig = TraceCollectionConfig()

    override fun install(
        config: TraceCollectionConfig,
        pipeline: AIAgentGraphPipeline
    ): TraceCollectionFeatureImpl {
        val featureImpl = TraceCollectionFeatureImpl(config)

        pipeline.interceptNodeExecutionCompleted(this) { eventContext: NodeExecutionCompletedContext ->
            val node = eventContext.node

            // Only collect for AIAgentNode instances (not start/finish nodes, etc.)
            if (node is AIAgentNode<*, *> && featureImpl.shouldCollectForNode(node)) {
                featureImpl.addTrace(
                    nodeName = node.name,
                    input = eventContext.input,
                    output = eventContext.output
                )
            }
        }

        return featureImpl
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
