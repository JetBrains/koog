package ai.koog.prompt.message

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Log probability information for a single token produced by the LLM.
 *
 * Providers that expose token-level probabilities (e.g. Ollama, OpenAI) populate this
 * structure when log probabilities are requested. The same type is reused for the
 * alternative candidates listed in [topLogprobs], in which case [topLogprobs] is empty.
 *
 * @property token The token text.
 * @property logprob The natural-log probability of [token].
 * @property bytes The UTF-8 byte representation of [token], or null if not provided.
 *   Useful when a character spans multiple tokens and the bytes must be combined to
 *   reconstruct the correct text.
 * @property topLogprobs The most likely alternative tokens at this position with their log
 *   probabilities. Empty when not requested or when this entry is itself an alternative.
 */
@Serializable
public data class LogProb @JvmOverloads constructor(
    public val token: String,
    public val logprob: Double,
    public val bytes: List<Int>? = null,
    public val topLogprobs: List<LogProb> = emptyList(),
)
