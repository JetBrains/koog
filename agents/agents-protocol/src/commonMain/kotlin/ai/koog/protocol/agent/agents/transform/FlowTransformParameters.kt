package ai.koog.protocol.agent.agents.transform

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 * Parameters for input transformation agents.
 */
@Serializable
public data class FlowTransformParameters(
    val transformations: List<FlowDataTransformation>
) : FlowAgentParameters
