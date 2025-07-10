package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.attribute.Attribute

internal data class MockAttribute(
    override val key: String,
    override val value: Any,
    override val verbose: Boolean = false
) : Attribute
