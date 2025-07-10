package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.event.EventBodyField

internal class MockEventBodyField(
    override val key: String,
    override val value: Any
) : EventBodyField()

