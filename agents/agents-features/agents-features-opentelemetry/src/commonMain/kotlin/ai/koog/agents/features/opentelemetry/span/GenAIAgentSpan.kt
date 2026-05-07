package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData

/**
 * A GenAI agent span emitted by the OpenTelemetry feature.
 *
 * @property type Span category tag.
 * @property id Stable identifier within the agent run.
 * @property name Human-readable span name.
 * @property kind OpenTelemetry [SpanKind].
 */
public class GenAIAgentSpan internal constructor(
    public val type: SpanType,
    internal val parentSpan: GenAIAgentSpan?,
    public val id: String,
    public val name: String,
    internal val span: Span,
    internal val context: Context,
    public val kind: SpanKind,
    attributes: List<Attribute>,
) {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val _attributes: MutableList<Attribute> = attributes.toMutableList()

    /**
     * Attributes attached to this span.
     */
    public val attributes: List<Attribute>
        get() = _attributes

    internal val logString: String
        get() = "${this::class.simpleName ?: "GenAIAgentSpan"} (name: $name, id: $id)"

    /**
     * Adds [attribute] to the span. Existing attribute with the same key is replaced.
     *
     * @param attribute Attribute to add.
     */
    public fun addAttribute(attribute: Attribute) {
        logger.debug { "$logString Adding attribute to the span: ${attribute.key}" }

        val existingAttribute = attributes.find { it.key == attribute.key }
        if (existingAttribute != null) {
            logger.debug { "$logString Attribute with key '${attribute.key}' already exists. Overwriting existing attribute value." }
            removeAttribute(existingAttribute)
        }
        _attributes.add(attribute)
    }

    /**
     * Adds all [attributes] to the span.
     *
     * @param attributes Attributes to add.
     */
    public fun addAttributes(attributes: List<Attribute>) {
        logger.debug { "$logString Adding <${attributes.size}> attribute(s) to the span. Attributes:\n${attributes.joinToString("\n") { "- ${it.key}" }}" }
        attributes.forEach { addAttribute(it) }
    }

    /**
     * Removes [attribute] from the span. Returns `true` if it was present.
     *
     * @param attribute Attribute to remove.
     */
    public fun removeAttribute(attribute: Attribute): Boolean {
        logger.debug { "$logString Removing attribute from span: ${attribute.key}" }
        return _attributes.remove(attribute)
    }

    internal fun end(
        spanEndStatus: StatusData? = null,
        verbose: Boolean = false,
    ) {
        logger.debug { "$logString Finishing the span." }

        span.setAttributes(attributes, verbose)
        span.setSpanStatus(spanEndStatus)
        span.end()

        logger.debug { "$logString Span has been finished." }
    }
}
