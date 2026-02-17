package ai.koog.agents.features.opentelemetry.metrics

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import io.opentelemetry.api.common.Attributes

internal fun Attributes.contains(searchableAttribute: Attribute): Boolean {
    var isFound = false

    this.forEach { attrKey, attrValue ->
        if (searchableAttribute.key == attrKey.toString() && searchableAttribute.value == attrValue) {
            isFound = true
        }
    }

    return isFound
}
