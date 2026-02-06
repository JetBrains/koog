package ai.koog.agents.tools.test

import kotlinx.serialization.Serializable

@Serializable
data class Payload(
    val id: Int,
    val name: String
)
