package ai.koog.protocol.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public enum class FlowAgentKind {
    @SerialName("task")
    TASK,

    @SerialName("verify")
    VERIFY,

    @SerialName("transform")
    TRANSFORM,

    @SerialName("parallel")
    PARALLEL
}
