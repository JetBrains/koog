package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@InternalLLMClientApi
@Serializable
public data class MistralAITool(
    @EncodeDefault(ALWAYS) val type: String = "function",
    val function: MistralAIFunction
)

@InternalLLMClientApi
@Serializable
public data class MistralAIToolParamsSpecification(
    @EncodeDefault(ALWAYS) val type: String = "object",
    val properties: JsonObject,
    val required: List<String>
)

@InternalLLMClientApi
@Serializable
public data class MistralAIFunction(
    val name: String,
    val description: String,
    @EncodeDefault(ALWAYS) val strict: Boolean = false,
    val parameters: MistralAIToolParamsSpecification
)

