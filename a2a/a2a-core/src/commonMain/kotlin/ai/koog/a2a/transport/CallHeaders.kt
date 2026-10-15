package ai.koog.a2a.transport

/**
 * Returns a view of these headers with case-insensitive lookup by name, as HTTP requires (HTTP/2 lowercases names, so
 * `A2A-Version` arrives as `a2a-version`).
 *
 * Names are kept as they are when iterating. If several names differ only in case, a lookup returns the values of all
 * of them, in iteration order.
 */
public fun Map<String, List<String>>.withCaseInsensitiveNames(): Map<String, List<String>> =
    if (this is CaseInsensitiveHeaders) this else CaseInsensitiveHeaders(this)

private class CaseInsensitiveHeaders(
    private val source: Map<String, List<String>>
) : Map<String, List<String>> by source {
    private val valuesByLowercaseName: Map<String, List<String>> = buildMap {
        source.forEach { (name, values) ->
            val key = name.lowercase()
            put(key, get(key).orEmpty() + values)
        }
    }

    override fun get(key: String): List<String>? = valuesByLowercaseName[key.lowercase()]

    override fun containsKey(key: String): Boolean = key.lowercase() in valuesByLowercaseName

    override fun equals(other: Any?): Boolean = source == other

    override fun hashCode(): Int = source.hashCode()

    override fun toString(): String = source.toString()
}
