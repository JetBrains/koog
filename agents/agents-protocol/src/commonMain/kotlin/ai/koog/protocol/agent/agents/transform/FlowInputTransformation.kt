package ai.koog.protocol.agent.agents.transform

import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowInputTransformation(
    val value: String, // input.data / input.success
)
