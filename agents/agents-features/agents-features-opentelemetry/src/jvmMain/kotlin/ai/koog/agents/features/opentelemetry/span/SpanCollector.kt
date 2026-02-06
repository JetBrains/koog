package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class SpanCollector {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Tree node representing a span and its children in the trace tree.
     */
    data class SpanNode(
        val path: AgentExecutionInfo,
        val span: GenAIAgentSpan,
        val children: MutableList<SpanNode> = mutableListOf()
    )

    /**
     * A read-write lock to ensure thread-safe access to the span collections.
     */
    private val spansLock = ReentrantReadWriteLock()

    /**
     * Map of path string to a list of SpanNodes for O(1) lookups by execution path.
     * Multiple spans can share the same path but have different span IDs.
     */
    private val pathToNodeMap = mutableMapOf<String, MutableList<SpanNode>>()

    /**
     * Root nodes of the span tree (spans without parent execution info).
     */
    private val rootNodes = mutableListOf<SpanNode>()

    /**
     * The number of active spans in the tree.
     */
    internal val activeSpansCount: Int
        get() = pathToNodeMap.values.sumOf { it.size }

    fun collectSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
    ) {
        logger.debug { "${span.logString} Starting span with path: ${path.path()}" }
        addSpanToTree(span, path)
    }

    fun removeSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
    ) {
        logger.debug { "${span.logString} Finishing the span with path: ${path.path()}" }
        removeSpanFromTree(span, path)
    }

    fun getSpan(
        path: AgentExecutionInfo,
        filter: ((SpanNode) -> Boolean)? = null
    ): GenAIAgentSpan? = spansLock.read get@{
        val spanNodes = pathToNodeMap[path.path()]

        if (spanNodes.isNullOrEmpty()) {
            return@get null
        }

        logger.trace { "Found ${spanNodes.size} span nodes for path: ${path.path()}" }
        val filter = filter ?: { true }
        val filteredNode = spanNodes.firstOrNull(filter)
        return filteredNode?.span
    }

    fun getStartedSpan(
        executionInfo: AgentExecutionInfo,
        eventId: String,
        spanType: SpanType
    ): GenAIAgentSpan? = spansLock.read {
        logger.debug { "Looking for span with parameters (type: ${spanType.name}, path: ${executionInfo.path()}, event id: $eventId)" }

        val spanNodes = pathToNodeMap[executionInfo.path()]

        if (spanNodes.isNullOrEmpty()) {
            return@read null
        }

        logger.trace { "Found <${spanNodes.size}> span node(s) for path: ${executionInfo.path()}. Filter by parameters (type: ${spanType.name}, event id: $eventId)" }
        val filteredNode = spanNodes.firstOrNull { node ->
            node.span.id == eventId && node.span.type == spanType
        }
        return filteredNode?.span
    }

    /**
     * Clears all spans from the collector.
     */
    fun clear() = spansLock.write {
        pathToNodeMap.clear()
        rootNodes.clear()
        logger.debug { "All spans are cleared in span collector" }
    }

    /**
     * Retrieves all spans from the tree in post-order (leaf nodes before parents).
     *
     * @param filter Optional filter for spans to include.
     * @return List of span nodes in post-order traversal.
     */
    fun getActiveSpans(filter: ((GenAIAgentSpan) -> Boolean)? = null): List<SpanNode> = spansLock.read {
        // Traverse tree depth-first (post-order) to get leaf nodes before parents
        val collectedNodes = mutableListOf<SpanNode>()

        fun traversePostOrder(node: SpanNode) {
            // Visit children depth-first
            node.children.forEach { traversePostOrder(it) }

            // Add the node itself
            if (filter == null || filter(node.span)) {
                collectedNodes.add(node)
            }
        }

        // Start traversal from all root nodes
        rootNodes.forEach { traversePostOrder(it) }

        collectedNodes
    }

    //region Private Methods

    /**
     * Adds a span to the tree structure based on its execution path.
     * Automatically links the span to its parent node or adds it as a root.
     * Supports multiple spans with the same path but different span IDs.
     *
     * @param span The span to add.
     * @param path The execution path for this span.
     */
    private fun addSpanToTree(span: GenAIAgentSpan, path: AgentExecutionInfo) = spansLock.write add@{
        val node = SpanNode(path, span)

        // Add to the path map-append to a list for this path
        pathToNodeMap.getOrPut(path.path()) { mutableListOf() }.add(node)

        // Find the parent node from the agent execution path instance
        val parentPath = path.parent

        // Add root node
        if (parentPath == null) {
            rootNodes.add(node)
            logger.debug { "${span.logString} Added as a root span" }
            return@add
        }

        // Add the node as a parent's child
        val parentNodes = pathToNodeMap[parentPath.path()]
            ?: error("Parent span node not found for node path: ${path.path()}")

        val parentNode = span.parentSpan?.let { parentSpan ->
            parentNodes.find { it.span.id == parentSpan.id }
        } ?: parentNodes.first()

        parentNode.children.add(node)
        logger.debug { "Added child span: '${node.span.name}', for parent: '${parentPath.path()}'" }
    }

    /**
     * Removes a span from the tree structure.
     * Verifies that the span has no active children before removal.
     *
     * @param span The span to remove.
     * @param path The execution path used to look up the node.
     * @throws IllegalStateException if the span has active children.
     */
    private fun removeSpanFromTree(span: GenAIAgentSpan, path: AgentExecutionInfo) = spansLock.write remove@{
        // Look for nodes using the path
        val spanNodes = pathToNodeMap[path.path()]
        if (spanNodes.isNullOrEmpty()) {
            logger.warn { "${span.logString} Span node not found for removal at path: ${path.path()}" }
            return@remove
        }

        // Find the node by span id if there are multiple nodes with the same path
        val node = if (spanNodes.size == 1) {
            spanNodes.find { it.span.id == span.id } ?: run {
                logger.warn { "${span.logString} Span node not found for removal. Multiple nodes at path but none match span id." }
                return@remove
            }
        } else {
            spanNodes.first()
        }

        // Check if the node has active children
        if (node.children.isNotEmpty()) {
            error(
                "${span.logString} Error deleting span node from the tree (path: ${path.path()}). " +
                    "Node still have <${node.children.size}> child span(s). Spans:\n" +
                    node.children.joinToString("\n") { node ->
                        " - ${node.span.logString}, active: ${node.span.span.isRecording}"
                    }
            )
        }

        val pathString = node.path.path()

        // Remove from a path map
        spanNodes.removeIf { it.span.id == span.id }
        if (spanNodes.isEmpty()) {
            pathToNodeMap.remove(pathString)
        }

        // Remove from parent's children or from root nodes
        val parentPath = node.path.parent
        if (parentPath == null) {
            rootNodes.removeIf { it.span.id == span.id }
            logger.debug { "Removed root span '${span.name}'" }
        } else {
            val parentNodes = pathToNodeMap[parentPath.path()]
            if (parentNodes != null) {
                val parentNode = span.parentSpan?.let { parentSpan ->
                    parentNodes.find { it.span.id == parentSpan.id }
                } ?: parentNodes.singleOrNull()

                parentNode?.children?.removeIf { it.span.id == span.id }
                logger.debug { "Removed child span '${span.name}' from parent '${parentPath.path()}'" }
            }
        }
    }

    //endregion Private Methods
}
