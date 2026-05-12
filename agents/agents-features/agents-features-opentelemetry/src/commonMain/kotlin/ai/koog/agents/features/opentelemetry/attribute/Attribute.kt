package ai.koog.agents.features.opentelemetry.attribute

/**
 * A key/value pair attached to a [GenAIAgentSpan][ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan]
 * or an event [ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent].
 */
public interface Attribute {

    /**
     * Attribute key
     */
    public val key: String

    /**
     * Attribute value.
     */
    public val value: Any

    /**
     * Joins two attribute-key segments with a `.` separator.
     *
     * @param other Segment to append after the receiver.
     */
    public fun String.concatKey(other: String): String = this.plus(".$other")
}
