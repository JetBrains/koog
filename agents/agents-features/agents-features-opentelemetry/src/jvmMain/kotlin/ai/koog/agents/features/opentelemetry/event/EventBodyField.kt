package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.utils.HiddenString
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Represents an abstract field to be included in an event's body. Each field is characterized
 * by a unique key and an associated value, and optionally marked as verbose for additional significance.
 */
internal abstract class EventBodyField {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    abstract val key: String

    abstract val value: Any

    fun valueString(verbose: Boolean): String {
        return convertValueToString(key, value, verbose)
    }

    //region Private Methods

    private fun convertValueToString(key: Any, value: Any, verbose: Boolean): String {
        return when (value) {
            is HiddenString -> {
                if (verbose) {
                    convertValueToString(key, value.value, true)
                } else {
                    convertValueToString(key, value.toString(), false)
                }
            }
            is CharSequence, is Char -> {
                "\"${value}\""
            }
            is Boolean,
            is Int, is Long,
            is Double, is Float -> {
                value.toString()
            }
            is List<*> -> {
                value.filterNotNull().joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
                    convertValueToString(key, item, verbose)
                }
            }

            is Map<*, *> -> {
                value.entries
                    .filter { it.key != null && it.value != null }
                    .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
                        "\"$key\":${convertValueToString(key!!, value!!, verbose)}"
                    }
            }

            else -> {
                logger.debug { "$key: Custom type for event body: ${value::class.simpleName}. Use toString()" }
                value.toString()
            }
        }
    }

    //endregion Private Methods
}
