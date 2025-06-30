package ai.koog.agents.features.opentelemetry.attribute

internal sealed interface GenAIAttribute : Attribute {
    override val key: String
        get() = "gen_ai"
}
