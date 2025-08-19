package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
public data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall,
    val index: Int? = null
)

@InternalLLMClientApi
@Serializable
public data class FunctionCall(
    val name: String,
    val arguments: Map<String, @Contextual Any> = emptyMap()
)

