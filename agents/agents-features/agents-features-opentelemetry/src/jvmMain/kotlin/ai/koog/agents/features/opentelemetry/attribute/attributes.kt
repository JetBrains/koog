package ai.koog.agents.features.opentelemetry.attribute

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import java.util.function.BiConsumer

internal fun List<Attribute>.toAttributes() : Attributes {

    return object : Attributes {

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any?> get(key: AttributeKey<T?>): T? {
            return find { it.key == key.key }?.value as T?

        }

        override fun forEach(consumer: BiConsumer<in AttributeKey<*>, in Any>) {
            forEach { attribute ->
                val attributeKey = AttributeKey.stringKey(attribute.key)
                consumer.accept(attributeKey, attribute.value)
            }
        }

        override fun size(): Int = this@toAttributes.size

        override fun isEmpty(): Boolean = this@toAttributes.isEmpty()

        override fun asMap(): Map<AttributeKey<*>?, Any?>? {
            return this@toAttributes.associate { attribute ->
                AttributeKey.stringKey(attribute.key) to attribute.value
            }
        }

        override fun toBuilder(): AttributesBuilder? {
            val builder = Attributes.builder()

            forEach { attribute: Attribute ->
                val key = attribute.key
                val value = attribute.value

                when (value) {
                    is String? -> builder.put(key, value)
                    is Long -> builder.put(key, value)
                    is Double -> builder.put(key, value)
                    is Boolean -> builder.put(key, value)
                    else -> error("Attribute '${key}' has unsupported type for value: ${value::class.simpleName}")
                }
            }

            return builder
        }
    }
}
