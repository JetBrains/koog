package ai.koog.protocol.agent.agents.transform

import kotlinx.serialization.Serializable

/**
 * Defines a single input transformation rule.
 */
@Serializable
public data class FlowDataTransformation(
    val value: String, // input.data / input.success
)
