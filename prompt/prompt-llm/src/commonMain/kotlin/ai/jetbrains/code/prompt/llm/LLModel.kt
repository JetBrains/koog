package ai.jetbrains.code.prompt.llm

import kotlinx.serialization.Serializable

@Serializable
public data class LLModel(val provider: LLMProvider, val id: String, val capabilities: List<LLMCapability>)
