package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute

internal abstract class GenAIAgentEvent {

    open val name: String
        get() = "gen_ai"

    private val _attributes: MutableList<Attribute> = mutableListOf()

    private val _bodyFields: MutableList<EventBodyField> = mutableListOf()

    /**
     * Provides a list of attributes associated with this event. These attributes are typically
     * used to provide metadata or additional contextual information.
     */
    val attributes: List<Attribute>
        get() = _attributes

    /**
     * The body field for the event.
     *
     * Note: Currently, the OpenTelemetry SDK does not support event body fields.
     *       This field is used to store the body fields.
     *       Fields are merged with attributes when creating the event.
     */
    val bodyFields: List<EventBodyField>
        get() = _bodyFields

    fun addAttribute(attribute: Attribute) {
        _attributes.add(attribute)
    }

    fun addAttributes(attributes: List<Attribute>) {
        _attributes.addAll(attributes)
    }

    fun addBodyField(eventField: EventBodyField) {
        _bodyFields.add(eventField)
    }

    fun removeBodyField(eventField: EventBodyField): Boolean {
        return _bodyFields.remove(eventField)
    }

    fun String.concatName(other: String): String = "$this.$other"
}
