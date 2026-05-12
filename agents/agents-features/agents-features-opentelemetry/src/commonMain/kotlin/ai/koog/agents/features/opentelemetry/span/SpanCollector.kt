package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     * Guards access to the span tree. Critical sections are small map/list mutations and lookups,
     * so a plain mutex is a better fit than a read-write lock - writes happen on every span start
     * and end, so reader overlap is rare, and the mutex avoids the reader-count bookkeeping
     * overhead and writer-starvation risk of a read-write lock.
     */
    private val spansLock = Mutex()

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

    suspend fun collectSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
    ) {
        logger.debug { "${span.logString} Starting span with path: ${path.path()}" }
        addSpanToTree(span, path)
    }

    suspend fun removeSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
    ) {
        logger.debug { "${span.logString} Finishing the span with path: ${path.path()}" }
        removeSpanFromTree(span, path)
    }

    suspend fun getSpan(
        path: AgentExecutionInfo,
        filter: ((SpanNode) -> Boolean)? = null
    ): GenAIAgentSpan? = spansLock.withLock get@{
        val spanNodes = pathToNodeMap[path.path()]

        if (spanNodes.isNullOrEmpty()) {
            return@get null
        }

        logger.trace { "Found ${spanNodes.size} span nodes for path: ${path.path()}" }
        val filter = filter ?: { true }
        val filteredNode = spanNodes.firstOrNull(filter)

        filteredNode?.span
    }

    suspend fun getStartedSpan(
        executionInfo: AgentExecutionInfo,
        eventId: String,
        spanType: SpanType
    ): GenAIAgentSpan? = spansLock.withLock read@{
        logger.debug { "Looking for span with parameters (type: ${spanType.name}, path: ${executionInfo.path()}, event id: $eventId)" }

        val spanNodes = pathToNodeMap[executionInfo.path()]

        if (spanNodes.isNullOrEmpty()) {
            return@read null
        }

        logger.trace { "Found <${spanNodes.size}> span node(s) for path: ${executionInfo.path()}. Filter by parameters (type: ${spanType.name}, event id: $eventId)" }
        val filteredNode = spanNodes.firstOrNull { node ->
            node.span.id == eventId && node.span.type == spanType
        }

        filteredNode?.span
    }

    /**
     * Clears all spans from the collector.
     */
    suspend fun clear() = spansLock.withLock {
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
    suspend fun getActiveSpans(filter: ((GenAIAgentSpan) -> Boolean)? = null): List<SpanNode> = spansLock.withLock {
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
    private suspend fun addSpanToTree(span: GenAIAgentSpan, path: AgentExecutionInfo) = spansLock.withLock add@{
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
    private suspend fun removeSpanFromTree(span: GenAIAgentSpan, path: AgentExecutionInfo) = spansLock.withLock remove@{
        // Look for nodes using the path
        val spanNodes = pathToNodeMap[path.path()]
        if (spanNodes.isNullOrEmpty()) {
            logger.warn { "${span.logString} Span node not found for removal at path: ${path.path()}" }
            return@remove
        }

        // Find the node by span id
        val node = spanNodes.find { it.span.id == span.id } ?: run {
            logger.warn { "${span.logString} Span node not found for removal at path: ${path.path()}." }
            return@remove
        }

        // Check if the node has active children
        if (node.children.isNotEmpty()) {
            error(
                "${span.logString} Error deleting span node from the tree (path: ${path.path()}). " +
                    "Node still have <${node.children.size}> child span(s). Spans:\n" +
                    node.children.joinToString("\n") { childNode ->
                        " - ${childNode.span.logString}, active: ${childNode.span.span.isRecording()}"
                    }
            )
        }

        val pathString = node.path.path()

        // Remove from a path map
        spanNodes.removeAll { it.span.id == span.id }
        if (spanNodes.isEmpty()) {
            pathToNodeMap.remove(pathString)
        }

        // Remove from parent's children or from root nodes
        val parentPath = node.path.parent
        if (parentPath == null) {
            rootNodes.removeAll { it.span.id == span.id }
            logger.debug { "Removed root span '${span.name}'" }
        } else {
            val parentNodes = pathToNodeMap[parentPath.path()]
            if (parentNodes != null) {
                val parentNode = span.parentSpan?.let { parentSpan ->
                    parentNodes.find { it.span.id == parentSpan.id }
                } ?: parentNodes.singleOrNull()

                parentNode?.children?.removeAll { it.span.id == span.id }
                logger.debug { "Removed child span '${span.name}' from parent '${parentPath.path()}'" }
            }
        }
    }

    //endregion Private Methods
}
