package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.event.EventBodyField
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent

internal data class MockGenAIAgentEvent(
    override val name: String = "test_event",
    private val fields: List<EventBodyField> = emptyList()
) : GenAIAgentEvent() {

    init {
        fields.forEach { field ->
            addBodyField(field)
        }
    }
}
