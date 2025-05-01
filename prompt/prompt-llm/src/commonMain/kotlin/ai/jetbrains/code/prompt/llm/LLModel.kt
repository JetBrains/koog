package ai.jetbrains.code.prompt.llm

import kotlinx.serialization.Serializable

@Serializable
data class LLModel(val provider: LLMProvider, val id: String, val capabilities: List<LLMCapability>)