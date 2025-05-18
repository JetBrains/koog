package ai.koog.prompt.llm

import kotlinx.serialization.Serializable

/**
 * Represents a Large Language Model (LLM) with a specific provider, identifier, and a set of capabilities.
 *
 * @property provider The provider of the LLM, such as Google, OpenAI, or Meta.
 * @property id A unique identifier for the LLM instance. This typically represents the specific model version or name.
 * @property capabilities A list of capabilities supported by the LLM, such as temperature adjustment, tools usage, or schema-based tasks.
 */
@Serializable
public data class LLModel(val provider: LLMProvider, val id: String, val capabilities: List<LLMCapability>)
