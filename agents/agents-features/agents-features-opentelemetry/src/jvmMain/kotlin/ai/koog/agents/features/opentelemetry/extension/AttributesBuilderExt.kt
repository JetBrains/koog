package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder

internal fun AttributesBuilder.put(attribute: Attribute): AttributesBuilder {
    this.put(AttributeKey.stringKey(attribute.key), attribute.value.toString())

    return this
}
