package ai.koog.http.client

/**
 * Returns a copy of this multi-valued header map with all keys converted to lowercase.
 *
 * Values for keys that differ only in case are concatenated into a single list so that
 * a response containing e.g. both `Set-Cookie` and `set-cookie` becomes a single lowercase
 * entry with both values. Empty maps are returned as [emptyMap] to avoid allocation.
 *
 * This matches the contract documented on [KoogHttpClientException.headers] and lets
 * individual HTTP client implementations adapt their native header types without each
 * reinventing the normalization.
 */
public fun Map<String, List<String>>.lowercaseHeaderKeys(): Map<String, List<String>> {
    if (isEmpty()) return emptyMap()
    val normalized = LinkedHashMap<String, MutableList<String>>(size)
    for ((key, values) in this) {
        normalized.getOrPut(key.lowercase()) { mutableListOf() }.addAll(values)
    }
    return normalized
}
