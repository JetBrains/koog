package ai.koog.agents.core.tools

/**
 * Immutable, caller-contributed context threaded into [Tool.execute] alongside the typed arguments.
 *
 * This is a strictly additive side channel: values are not part of the tool's argument schema, are not
 * serialized to or from the LLM, and must not be relied on for routing or tool selection. Typical use
 * cases are cross-cutting concerns such as a distributed-tracing span identifier, a run-scoped correlation
 * id, or a per-call feature flag contributed by an installed feature.
 *
 * Instances can be constructed from a `Map`, built via [of], or combined with [plus]. The [EMPTY] singleton
 * represents the absence of metadata and is the default passed through the framework.
 *
 * @property values The underlying key-value map. Null values are permitted and retained as-is.
 */
public class ToolCallMetadata(
    private val values: Map<String, Any?>,
) {
    /**
     * Returns the value associated with [key], or `null` if no entry exists for that key.
     *
     * Note: a `null` return is ambiguous. The key may be absent, or the stored value may be `null`.
     * Use [contains] to disambiguate.
     */
    public operator fun get(key: String): Any? = values[key]

    /**
     * Returns `true` if this metadata contains an entry for [key], even if its value is `null`.
     */
    public operator fun contains(key: String): Boolean = key in values

    /**
     * Returns `true` when this metadata carries no entries.
     */
    public fun isEmpty(): Boolean = values.isEmpty()

    /**
     * Returns `true` when this metadata carries at least one entry.
     */
    public fun isNotEmpty(): Boolean = values.isNotEmpty()

    /**
     * Returns the set of keys present in this metadata.
     */
    public val keys: Set<String> get() = values.keys

    /**
     * Returns a read-only view of the underlying map.
     */
    public fun asMap(): Map<String, Any?> = values

    /**
     * Returns a new [ToolCallMetadata] containing entries from this instance plus [other]. Entries in
     * [other] overwrite entries with the same key in this instance.
     */
    public operator fun plus(other: ToolCallMetadata): ToolCallMetadata {
        if (other.isEmpty()) return this
        if (this.isEmpty()) return other
        return ToolCallMetadata(values + other.values)
    }

    /**
     * Returns a new [ToolCallMetadata] containing entries from this instance plus [other]. Entries in
     * [other] overwrite entries with the same key in this instance.
     */
    public operator fun plus(other: Map<String, Any?>): ToolCallMetadata {
        if (other.isEmpty()) return this
        return ToolCallMetadata(values + other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToolCallMetadata) return false
        return values == other.values
    }

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "ToolCallMetadata($values)"

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
