package ai.koog.agents.features.opentelemetry.attribute

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import java.util.function.BiConsumer

internal fun AttributesBuilder.addAttributes(attributes: Map<AttributeKey<*>, Any>): AttributesBuilder {
    attributes.forEach { (key, value) ->
        @Suppress("UNCHECKED_CAST")
        put(key as AttributeKey<Any>, value)
    }
    return this
}

internal fun List<Attribute>.toSdkAttributes(): Attributes {
    val sdkAttributesMap = this.associate { it.toSdkAttribute() }

    return object : Attributes {

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any?> get(key: AttributeKey<T?>): T? = sdkAttributesMap[key] as T?

        override fun forEach(consumer: BiConsumer<in AttributeKey<*>, in Any>) {
            sdkAttributesMap.forEach { attribute ->
                consumer.accept(attribute.key, attribute.value)
            }
        }

        override fun size(): Int = sdkAttributesMap.size

        override fun isEmpty(): Boolean = sdkAttributesMap.isEmpty()

        override fun asMap(): Map<AttributeKey<*>, Any> = sdkAttributesMap

        override fun toBuilder(): AttributesBuilder = Attributes.builder().addAttributes(sdkAttributesMap)
    }
}

private fun Attribute.toSdkAttribute(): Pair<AttributeKey<*>, Any> {
    val key = this.key
    val value = this.value

    return when (value) {
        is CharSequence,
        is Char -> {
            Pair(AttributeKey.stringKey(key), value)
        }
        is Boolean -> {
            Pair(AttributeKey.booleanKey(key), value)
        }
        is Int -> {
            Pair(AttributeKey.longKey(key), value.toLong())
        }
        is Long -> {
            Pair(AttributeKey.longKey(key), value)
        }
        is Double -> {
            Pair(AttributeKey.doubleKey(key), value)
        }
        is Float -> {
            Pair(AttributeKey.doubleKey(key), value.toDouble())
        }
        is List<*> -> {
            if (value.all { it is CharSequence || it is Char }) {
                Pair(AttributeKey.stringArrayKey(key), value)
            } else if (value.all { it is Boolean }) {
                Pair(AttributeKey.booleanArrayKey(key), value)
            } else if (value.all { it is Int }) {
                Pair(AttributeKey.longArrayKey(key), value.map { (it as Int).toLong() })
            } else if (value.all { it is Long }) {
                Pair(AttributeKey.longArrayKey(key), value)
            } else if (value.all { it is Double }) {
                Pair(AttributeKey.doubleArrayKey(key), value)
            } else if (value.all { it is Float }) {
                Pair(AttributeKey.doubleArrayKey(key), value.map { (it as Float).toDouble() })
            } else {
                error(
                    "Attribute '$key' has unsupported type for List values: ${value.firstOrNull()?.let {
                        it::class.simpleName
                    } }"
                )
            }
        }
        else -> {
            error("Attribute '$key' has unsupported type for value: ${value::class.simpleName}")
        }
    }
}
