@file:JvmName("Utils")

package ai.koog.http.client

import kotlin.jvm.JvmName

/**
 * Merges header groups into a single map.
 *
 * Header names are matched case-insensitively. If the same header name appears more than once,
 * the value from the later group replaces the earlier one while unrelated headers are preserved.
 *
 * Pass groups in ascending precedence order, for example:
 * `mergeHeaders(defaultHeaders, inferredHeaders, requestHeaders)`.
 *
 * @param headerGroups Header maps ordered from lowest to highest precedence.
 */
public fun mergeHeaders(vararg headerGroups: Map<String, String>): Map<String, String> {
    return headerGroups.fold(mutableMapOf()) { finalHeaders, currentHeaders ->
        currentHeaders.forEach { (key, value) -> finalHeaders.putIgnoringCase(key, value) }
        finalHeaders
    }
}

private fun MutableMap<String, String>.putIgnoringCase(key: String, value: String) {
    val existingKey = keys.firstOrNull { it.equals(key, ignoreCase = true) }
    if (existingKey != null) {
        remove(existingKey)
    }
    put(key, value)
}
