package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.agents.utils.HiddenString
import io.opentelemetry.kotlin.attributes.AttributesMutator

internal fun AttributesMutator.applyAttributes(attributes: List<Attribute>, verbose: Boolean) {
    attributes.forEach { attribute ->
        applyAttribute(attribute, verbose)
    }
}

private fun AttributesMutator.applyAttribute(attribute: Attribute, verbose: Boolean) {
    val key = attribute.key
    val value = attribute.value

    when (value) {
        is HiddenString -> {
            val unwrapped = if (verbose) value.value else value.toString()
            setStringAttribute(key, unwrapped)
        }
        is String -> setStringAttribute(key, value)
        is CharSequence -> setStringAttribute(key, value.toString())
        is Char -> setStringAttribute(key, value.toString())
        is Boolean -> setBooleanAttribute(key, value)
        is Int -> setLongAttribute(key, value.toLong())
        is Long -> setLongAttribute(key, value)
        is Float -> setDoubleAttribute(key, value.toDouble())
        is Double -> setDoubleAttribute(key, value)
        is List<*> -> applyListAttribute(key, value, verbose)
        else -> error("Attribute '$key' has unsupported type: ${value::class.simpleName}")
    }
}

@Suppress("UNCHECKED_CAST")
private fun AttributesMutator.applyListAttribute(key: String, value: List<*>, verbose: Boolean) {
    when {
        value.all { it is HiddenString } -> {
            val unwrapped = value.map {
                val hs = it as HiddenString
                if (verbose) hs.value else hs.toString()
            }
            setStringListAttribute(key, unwrapped)
        }
        value.all { it is CharSequence || it is Char } -> {
            setStringListAttribute(key, value.map { it.toString() })
        }
        value.all { it is Boolean } -> {
            setBooleanListAttribute(key, value as List<Boolean>)
        }
        value.all { it is Int } -> {
            setLongListAttribute(key, value.map { (it as Int).toLong() })
        }
        value.all { it is Long } -> {
            setLongListAttribute(key, value as List<Long>)
        }
        value.all { it is Double } -> {
            setDoubleListAttribute(key, value as List<Double>)
        }
        value.all { it is Float } -> {
            setDoubleListAttribute(key, value.map { (it as Float).toDouble() })
        }
        else -> {
            error(
                "Attribute '$key' has unsupported type for List values: ${value.firstOrNull()?.let {
                    it::class.simpleName
                }}"
            )
        }
    }
}
