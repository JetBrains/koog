package ai.koog.agents.features.opentelemetry.attribute

/**
 * Marker for OpenTelemetry GenAI semantic-convention attributes - root key `gen_ai`.
 * Concrete subtypes live in [GenAIAttributes].
 */
public sealed interface GenAIAttribute : Attribute {
    override val key: String
        get() = "gen_ai"
}
