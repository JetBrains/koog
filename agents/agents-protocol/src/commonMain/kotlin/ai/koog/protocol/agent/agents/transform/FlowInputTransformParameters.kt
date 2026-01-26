package ai.koog.protocol.agent.agents.transform

import ai.koog.protocol.agent.FlowAgentParameters
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public data class FlowInputTransformParameters(
    val transformations: List<FlowInputTransformation>
) : FlowAgentParameters
