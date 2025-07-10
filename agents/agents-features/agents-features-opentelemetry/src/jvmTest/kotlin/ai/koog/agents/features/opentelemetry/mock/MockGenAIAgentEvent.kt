package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent

internal data class MockGenAIAgentEvent(
    override val name: String = "test_event",
    override val attributes: List<Attribute> = listOf(),
    override val bodyFields: List<EventBodyField> = listOf(),
    override val verbose: Boolean = false
) : GenAIAgentEvent
