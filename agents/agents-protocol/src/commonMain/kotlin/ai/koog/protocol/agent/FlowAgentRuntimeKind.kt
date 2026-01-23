package ai.koog.protocol.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
public enum class FlowAgentRuntimeKind(public val id: String) {

    @SerialName("koog")
    KOOG("koog"),

    @SerialName("lang_chain")
    LANG_CHAIN("lang_chain"),

    @SerialName("claude")
    CLAUDE_CODE("claude"),

    @SerialName("codex")
    CODEX("codex")
}
