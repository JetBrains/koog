package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Represents an abstract field to be included in an event's body. Each field is characterized
 * by a unique key and an associated value, and optionally marked as verbose for additional significance.
 */
internal abstract class EventBodyField {

    companion object {
        private val logger = KotlinLogging.logger {  }
    }

    abstract val key: String

    abstract val value: Any

    open val verbose: Boolean
        get() = false

    internal fun toAttribute(): Attribute {
        return CustomAttribute(key, getAttributeValue(key, value), verbose)
    }

    private fun getAttributeValue(key: Any, value: Any): Any {
        return when (value) {
            // Types supported by Attributes
            is CharSequence, is Char -> {
                "\"${value}\""
            }
            is Boolean,
            is Int, is Long,
            is Double, is Float -> {
                value
            }
            is List<*> -> {
                if (value.all { it is CharSequence } || value.all { it is Char } ||
                    value.all { it is Boolean } ||
                    value.all { it is Int } || value.all { it is Long } ||
                    value.all { it is Double } || value.all { it is Float } ) {
                    value
                }
                else {

                    // Not supported by Attributes
                    logger.debug { "${key}: Unsupported type for event body: ${value::class.simpleName}. use toString()" }
                    value.filterNotNull().joinToString(prefix = "[", postfix = "]") { getAttributeValue(key, it).toString() }
                }
            }

            // Types not supported by Attributes
            is Map<*, *> -> {
                value.entries
                    .filter { it.key != null && it.value != null }
                    .joinToString(prefix = "{", postfix = "}") { entry -> "\"${entry.key}\": ${getAttributeValue(entry.key!!, entry.value!!)}" }
            }

            else -> {
                logger.debug { "${key}: Unsupported type for event body: ${value::class.simpleName}. use toString()" }
                value.toString()
            }
        }
    }
}
