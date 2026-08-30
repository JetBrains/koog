package ai.koog.skills.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an available agent skill.
 *
 * @property name The unique skill name.
 * @property description A short description of what the skill does and when it should be used.
 * @property location Absolute path to the skill definition (`SKILL.md`) file.
 * @property license Optional license name or a reference to a bundled license file.
 * @property compatibility Optional environment compatibility requirements.
 * @property metadata Optional additional metadata as string key-value pairs.
 * @property allowedTools Optional space-separated list of pre-approved tools.
 */
@Serializable
public data class Skill(
    public val name: String,
    public val description: String,
    public val location: String,
    public val license: String? = null,
    public val compatibility: String? = null,
    public val metadata: Map<String, String>? = null,
    @SerialName("allowed-tools")
    public val allowedTools: String? = null
)
