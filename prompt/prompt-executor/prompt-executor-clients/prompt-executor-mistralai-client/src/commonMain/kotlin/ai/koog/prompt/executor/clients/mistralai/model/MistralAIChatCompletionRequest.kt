package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.mistralai.serialization.StopSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
public data class MistralAIChatCompletionRequest(
    val model: String,
    val temperature: Double? = null,
    val topP: Double? = 1.0,
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    @Serializable(with = StopSerializer::class)
    val stop: Stop? = null,
    val messages: List<MistralAIMessage>,
    val tools: List<MistralAITool>? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val toolChoice: String = "auto", // TODO: MAKE IT AN OBJECT
) {
    init {
        if (maxTokens != null) {
            require(maxTokens >= 0) { "maxTokens must be greater or equal to 0, but was $maxTokens" }
        }
        if (temperature != null) {
            require(temperature >= 0) { "temperature must be greater than 0, but was $temperature" }
        }
    }
}

@InternalLLMClientApi
@Serializable
public sealed class Stop {

    @InternalLLMClientApi
    @Serializable
    public data class Single(val value: String) : Stop()

    @InternalLLMClientApi
    @Serializable
    public data class Multiple(val values: List<String>) : Stop()

}
