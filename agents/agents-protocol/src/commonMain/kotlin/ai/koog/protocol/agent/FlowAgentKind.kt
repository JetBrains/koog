package ai.koog.protocol.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Types of agents available in a flow.
 */
@Serializable
public enum class FlowAgentKind {
    @SerialName("task")
    TASK,

    @SerialName("verify")
    VERIFY,

    @SerialName("transform")
    TRANSFORM,

    @SerialName("react")
    REACT,

    @SerialName("parallel")
    PARALLEL
}
