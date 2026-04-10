package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Base class for events recorded on a GenAI agent span (system / user / assistant / tool messages,
 * inference choices, moderation outcomes, etc.).
 */
public abstract class GenAIAgentEvent {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Event name as it appears on the exported span. Subclasses extend this with a dotted suffix
     * (e.g., `gen_ai.user.message`).
     */
    public open val name: String
        get() = "gen_ai"

    private val _attributes: MutableList<Attribute> = mutableListOf()

    private val _bodyFields: MutableList<EventBodyField> = mutableListOf()

    /**
     * Provides a list of attributes associated with this event.
     */
    public val attributes: List<Attribute>
        get() = _attributes

    /**
     * The body field for the event.
     *
     * Note: Currently, the OpenTelemetry SDK does not support event body fields.
     *       This field is used to store the body fields.
     *       Fields are merged with attributes when creating the event.
     */
    public val bodyFields: List<EventBodyField>
        get() = _bodyFields

    /**
     * Attaches [attribute] to this event.
     *
     * @param attribute Attribute to add.
     */
    public fun addAttribute(attribute: Attribute) {
        logger.debug { "Adding attribute to event (name: $name): ${attribute.key}" }
        _attributes.add(attribute)
    }

    /**
     * Attaches all [attributes] to this event.
     *
     * @param attributes Attributes to add.
     */
    public fun addAttributes(attributes: List<Attribute>) {
        logger.debug { "Adding ${attributes.size} attributes to event (name: $name):\n${attributes.joinToString("\n") { "- ${it.key}" }}" }
        _attributes.addAll(attributes)
    }

    /**
     * Adds [eventField] to the event body.
     *
     * @param eventField Body field to add.
     */
    public fun addBodyField(eventField: EventBodyField) {
        logger.debug { "Adding body field to event (name: $name): ${eventField.key}" }
        _bodyFields.add(eventField)
    }

    /**
     * Removes [eventField] from the event body. Returns `true` if it was present.
     *
     * @param eventField Body field to remove.
     */
    public fun removeBodyField(eventField: EventBodyField): Boolean {
        logger.debug { "Removing body field from event (name: $name): ${eventField.key}" }
        return _bodyFields.remove(eventField)
    }

    internal fun String.concatName(other: String): String = "$this.$other"
}
