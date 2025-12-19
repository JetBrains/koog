package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setEvents
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class SpanCollector(
    private val tracer: Tracer,
    private val verbose: Boolean = false
) {

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

    /**
     * Starts a new span with the given details and adds it to the tree structure.
     *
     * @param span The span to start.
     * @param path The execution path for this span.
     * @param instant The start timestamp for the span. Defaults to the current time if null.
     */
    fun startSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
        instant: Instant? = null,
    ) {
        logger.debug { "Starting span (name: ${span.name}, id: ${span.id}, path: ${path.path()})" }

        val spanKind = span.kind
        val parentContext = span.parentSpan?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(span.name)
            .setStartTimestamp(instant ?: Instant.now())
            .setSpanKind(spanKind)
            .setParent(parentContext)

        spanBuilder.setAttributes(span.attributes, verbose)

        val startedSpan = spanBuilder.startSpan()

        // Update span context and span properties
        span.span = startedSpan
        span.context = startedSpan.storeInContext(parentContext)

        // Add to the tree structure
        addSpanToTree(span, path)
        logger.debug { "Span has been started (id: ${span.id}, name: ${span.name})" }
    }

    /**
     * Ends a span with the given status and removes it from the tree.
     *
     * @param span The span to end.
     * @param spanEndStatus Optional status to set when ending the span.
     * @throws IllegalStateException if the span has active children.
     */
    fun endSpan(
        span: GenAIAgentSpan,
        path: AgentExecutionInfo,
        spanEndStatus: SpanEndStatus? = null
    ) {
        logger.debug { "${span.logString} Finishing the span." }

        val spanToFinish = span.span

        spanToFinish.setAttributes(span.attributes, verbose)
        spanToFinish.setEvents(span.events, verbose)
        spanToFinish.setSpanStatus(spanEndStatus)
        spanToFinish.end()

        // Remove the span from the tree structure
        removeSpanFromTree(span, path)
        logger.debug { "${span.logString} Span has been finished." }
    }

    fun getSpan(path: AgentExecutionInfo, filter: ((SpanNode) -> Boolean)? = null): GenAIAgentSpan? = spansLock.read get@{
        val spanNodes = pathToNodeMap[path.path()]

        if (spanNodes.isNullOrEmpty()) {
            return@get null
        }

        logger.trace { "Found ${spanNodes.size} span nodes for path: ${path.path()}" }
        val filter = filter ?: { true }
        val filteredNode = spanNodes.firstOrNull(filter)
        return filteredNode?.span
    }

    /**
     * Retrieves a span by its ID and casts it to the specified type.
     * Returns null if the span is not found or cannot be cast to the specified type.
     *
     * @param T The type to cast the span to.
     * @param path The execution path for the span.
     * @param filter Optional filter for spans to search.
     * @return The span cast to type T if found and castable, null otherwise.
     */
    inline fun <reified T : GenAIAgentSpan> getSpanCatching(
        path: AgentExecutionInfo,
        noinline filter: ((SpanNode) -> Boolean)? = null,
    ): T? = try {
        getSpan(path, filter) as? T
    } catch (e: Exception) {
        logger.warn(e) { "Failed to get span with path: ${path.path()}" }
        null
    }

    /**
     * Ends all unfinished spans that match the given predicate.
     * If no predicate is provided, ends all spans.
     * Spans are closed from leaf nodes up to parent nodes to maintain a proper hierarchy.
     *
     * @param filter Optional filter for spans to end.
     */
    fun endUnfinishedSpans(filter: ((GenAIAgentSpan) -> Boolean)? = null) {
        val spansToEnd = spansLock.read {
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

        spansToEnd.forEach { spanNode ->
            try {
                endSpan(spanNode.span, spanNode.path)
            } catch (e: Exception) {
                logger.warn(e) { "${spanNode.span.logString} Failed to end span due to the error: ${e.message}" }
            }
        }
    }

    /**
     * Clears all spans from the collector.
     */
    fun clear() = spansLock.write {
        pathToNodeMap.clear()
        rootNodes.clear()
        logger.debug { "All spans are cleared in span collector" }
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
