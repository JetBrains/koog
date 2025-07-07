package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute

internal interface GenAIAgentEvent {

    val verbose: Boolean

    val name: String
        get() = "gen_ai"

    val attributes: List<Attribute>

    /**
     * The body field for the event.
     *
     * Note: Currently, the OpenTelemetry SDK does not support event body fields.
     *       This field is used to store the body fields.
     *       Fields are merged with attributes when creating the event.
     */
    val bodyFields: List<EventBodyField>

    fun String.concatName(other: String): String = "$this.$other"
}
