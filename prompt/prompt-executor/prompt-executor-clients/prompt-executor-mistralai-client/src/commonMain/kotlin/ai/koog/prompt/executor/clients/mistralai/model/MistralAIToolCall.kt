package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.mistralai.serialization.FunctionCallArgumentsSerializer
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
public data class MistralAIToolCall(
    val id: String? = null,
    val type: String = "function",
    val function: FunctionCall,
    val index: Int = 0
)

@InternalLLMClientApi
@Serializable
public data class FunctionCall(
    val name: String,

    @Serializable(with = FunctionCallArgumentsSerializer::class)
    val arguments: FunctionCallArguments
)

@Serializable
@InternalLLMClientApi
public sealed class FunctionCallArguments {

    @InternalLLMClientApi
    @Serializable
    public data class StringFunctionCallArguments(val args: String) : FunctionCallArguments()

    @InternalLLMClientApi
    @Serializable
    public data object NullFunctionCallArguments : FunctionCallArguments()
}

internal fun FunctionCallArguments.asString(): String {
    return when(this) {
        is FunctionCallArguments.StringFunctionCallArguments -> this.args
        else -> ""
    }
}
