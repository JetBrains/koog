package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent

internal data class MockGenAIAgentEvent(
    override val name: String = "test_event",
    override val attributes: List<Attribute> = listOf(),
    private val fields: List<EventBodyField> = emptyList(),
    override val verbose: Boolean = false
) : GenAIAgentEvent {


    override val bodyFields: List<EventBodyField> = buildList {
        fields.forEach { field ->
            if (!verbose && field.key.contains("content", ignoreCase = true)) {
                return@forEach
            }
            add(field)
        }
    }
}
