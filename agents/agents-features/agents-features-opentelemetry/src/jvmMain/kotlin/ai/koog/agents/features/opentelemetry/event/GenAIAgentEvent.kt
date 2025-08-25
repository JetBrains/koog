package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import io.github.oshai.kotlinlogging.KotlinLogging

internal abstract class GenAIAgentEvent {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

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
        logger.debug { "Adding attribute to event (name: $name): ${attribute.key}" }
        _attributes.add(attribute)
    }

    fun addAttributes(attributes: List<Attribute>) {
        logger.debug { "Adding ${attributes.size} attributes to event (name: $name):\n${attributes.joinToString("\n") { "- ${it.key}" }}" }
        _attributes.addAll(attributes)
    }

    fun addBodyField(eventField: EventBodyField) {
        logger.debug { "Adding body field to event (name: $name): ${eventField.key}" }
        _bodyFields.add(eventField)
    }

    fun removeBodyField(eventField: EventBodyField): Boolean {
        logger.debug { "Removing body field from event (name: $name): ${eventField.key}" }
        return _bodyFields.remove(eventField)
    }

    fun String.concatName(other: String): String = "$this.$other"
}
