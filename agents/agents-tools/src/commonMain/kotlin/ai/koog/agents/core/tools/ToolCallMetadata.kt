package ai.koog.agents.core.tools

/**
 * Immutable, caller-contributed context threaded into [Tool.execute] alongside the typed arguments.
 *
 * This is a strictly additive side channel: values are not part of the tool's argument schema, are not
 * serialized to or from the LLM, and must not be relied on for routing or tool selection. Typical use
 * cases are cross-cutting concerns such as a distributed-tracing span identifier, a run-scoped correlation
 * id, or a per-call feature flag contributed by an installed feature.
 *
 * The class implements [Map] over [String] keys to [Any]`?` values by delegating to the underlying
 * map, so all standard read-only map operations ([get], [containsKey], [isEmpty], [keys], the `in`
 * operator, and so on) are available directly on the instance without bespoke wrappers.
 *
 * Instances can be constructed from a [Map], built via [of], or combined with [plus]. The [EMPTY]
 * singleton represents the absence of metadata and is the default passed through the framework.
 */
public class ToolCallMetadata(
    private val content: Map<String, Any?>,
) : Map<String, Any?> by content {

    /**
     * Returns a new [ToolCallMetadata] containing entries from this instance plus [other]. Entries in
     * [other] overwrite entries with the same key in this instance.
     */
    public operator fun plus(other: ToolCallMetadata): ToolCallMetadata {
        if (other.isEmpty()) return this
        if (this.isEmpty()) return other
        return ToolCallMetadata(content + other.content)
    }

    /**
     * Returns a new [ToolCallMetadata] containing entries from this instance plus [other]. Entries in
     * [other] overwrite entries with the same key in this instance.
     */
    public operator fun plus(other: Map<String, Any?>): ToolCallMetadata {
        if (other.isEmpty()) return this
        return ToolCallMetadata(content + other)
    }

    override fun equals(other: Any?): Boolean = content == other

    override fun hashCode(): Int = content.hashCode()

    override fun toString(): String = "ToolCallMetadata($content)"

    public companion object {
        /**
         * A shared empty [ToolCallMetadata] instance used as the default throughout the framework.
         */
        public val EMPTY: ToolCallMetadata = ToolCallMetadata(emptyMap())

        /**
         * Creates a [ToolCallMetadata] from the given [pairs]. Returns [EMPTY] if no pairs are supplied.
         */
        public fun of(vararg pairs: Pair<String, Any?>): ToolCallMetadata =
            if (pairs.isEmpty()) EMPTY else ToolCallMetadata(pairs.toMap())
    }
}
