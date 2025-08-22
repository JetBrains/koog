package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
internal sealed class MistralAIMessage {
    internal abstract val role: String
    internal abstract val content: List<MistralAIContent>?

    @InternalLLMClientApi
    @Serializable
    internal data class MistralAISystemMessage(
        @EncodeDefault(ALWAYS) override val role: String = "system",
        override val content: List<MistralAIContent.TextChunk>
    ) : MistralAIMessage()

    @InternalLLMClientApi
    @Serializable
    internal data class MistralAIUserMessage(
        @EncodeDefault(ALWAYS) override val role: String = "user",
        override val content: List<MistralAIContent.ContentChunk>? = null,
    ) : MistralAIMessage()

    @InternalLLMClientApi
    @Serializable
    internal data class MistralAIAssistantMessage(
        override val content: List<MistralAIContent.ContentChunk>? = null,
        val toolCalls: List<MistralAIToolCall>? = null,
        val prefix: Boolean = false,
        @EncodeDefault(ALWAYS) override val role: String = "assistant"
    ) : MistralAIMessage()

    @InternalLLMClientApi
    @Serializable
    internal data class MistralAIToolMessage(
        override val content: List<MistralAIContent.ContentChunk>? = null,
        val toolCallId: String? = null,
        val name: String? = null,
        @EncodeDefault(ALWAYS) override val role: String = "tool"
    ) : MistralAIMessage()
}

@InternalLLMClientApi
@Serializable
internal sealed interface MistralAIContent {
    @Serializable
    data class TextChunk(val text: String, val type: String = "text") : MistralAIContent

    @InternalLLMClientApi
    @Serializable
    sealed interface ContentChunk : MistralAIContent {

        @Serializable
        data class TextChunk(
            val text: String,
            @EncodeDefault(ALWAYS) val type: String = "text"
        ) : ContentChunk
    }
}
