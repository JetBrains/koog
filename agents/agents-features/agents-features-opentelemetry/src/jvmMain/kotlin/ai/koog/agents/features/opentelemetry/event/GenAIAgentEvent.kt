package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute

internal interface GenAIAgentEvent {

    val verbose: Boolean

    val name: String
        get() = "gen_ai"

    val attributes: List<Attribute>

    fun String.concatName(other: String): String = "$this.$other"
}
