package ai.koog.prompt.executor.clients.mistralai.model

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable

@InternalLLMClientApi
@Serializable
public sealed class MistralAIMessage {
    public abstract val role: String
    public abstract val content: List<MistralAIContent>?

    @InternalLLMClientApi
    @Serializable
    public data class MistralAISystemMessage(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val role: String = "system",
        override val content: List<MistralAIContent.TextChunk>
    ) : MistralAIMessage()

    @InternalLLMClientApi
    @Serializable
    public data class MistralAIUserMessage(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val role: String = "user",
        override val content: List<MistralAIContent.ContentChunk>? = null,
    ) : MistralAIMessage()

    @InternalLLMClientApi
    @Serializable
    public data class MistralAIAssistantMessage(
        override val content: List<MistralAIContent.ContentChunk>? = null,
        val toolCalls: List<ToolCall>? = null,
        val prefix: Boolean = false,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val role: String = "assistant"
    ) : MistralAIMessage()

    @InternalLLMClientApi
    @Serializable
    public data class MistralAIToolMessage(
        override val content: List<MistralAIContent.ContentChunk>? = null,
        val toolCallId: String? = null,
        val name: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val role: String = "tool"
    ) : MistralAIMessage()
}


@InternalLLMClientApi
@Serializable
public sealed interface MistralAIContent {
    @Serializable
    public data class TextChunk(val text: String, val type: String = "text") : MistralAIContent

    @InternalLLMClientApi
    @Serializable
    public sealed interface ContentChunk : MistralAIContent {

        @Serializable
        public data class TextChunk(
            val text: String,
            @EncodeDefault(ALWAYS) val type: String = "text"
        ) : ContentChunk

    }
}
