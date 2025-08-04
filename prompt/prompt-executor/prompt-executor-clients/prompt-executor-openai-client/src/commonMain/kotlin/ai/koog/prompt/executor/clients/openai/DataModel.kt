package ai.koog.prompt.executor.clients.openai

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmInline

@Serializable
internal data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double? = null,
    @SerialName("n")
    val numberOfChoices: Int? = null,
    val tools: List<OpenAITool>? = null,
    val modalities: List<OpenAIModalities>? = null,
    val audio: OpenAIAudioConfig? = null,
    val stream: Boolean = false,
    val toolChoice: OpenAIToolChoice? = null,
    val user: String? = null,
)

@Serializable
internal enum class OpenAIModalities {
    @SerialName("text")
    Text,

    @SerialName("audio")
    Audio,
}

@Serializable
internal data class OpenAIMessage(
    val role: String,
    @Serializable(with = ContentSerializer::class)
    val content: Content? = null,
    val audio: OpenAIAudio? = null,
    val toolCalls: List<OpenAIToolCall>? = null,
    val name: String? = null,
    val toolCallId: String? = null
)

@Serializable
internal sealed interface Content {
    fun text(): String

    @JvmInline
    value class Text(val value: String) : Content {
        override fun text(): String = value
    }

    @JvmInline
    value class Parts(val value: List<ContentPart>) : Content {
        override fun text(): String = value
            .filterIsInstance<ContentPart.Text>()
            .joinToString("\n") { it.text }
    }
}

@Serializable
internal sealed interface ContentPart {
    val type: String

    @Serializable
    data class Text(val text: String, override val type: String = "text") : ContentPart

    @Serializable
    data class Image(val imageUrl: ImageUrl, override val type: String = "image_url") : ContentPart

    @Serializable
    data class ImageUrl(val url: String)

    @Serializable
    data class Audio(val inputAudio: InputAudio, override val type: String = "input_audio") : ContentPart

    /**
     * @property data Base64 encoded audio data
     * @property format The format of the encoded audio data. Currently, it supports "wav" and "mp3"
     */
    @Serializable
    data class InputAudio(val data: String, val format: String)

    @Serializable
    data class File(val file: FileData, override val type: String = "file") : ContentPart

    @Serializable
    data class FileData(val fileData: String?, val fileId: String? = null, val filename: String? = null)
}

@Serializable
internal data class OpenAIToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAIFunction
)

@Serializable
internal data class OpenAIFunction(
    val name: String,
    val arguments: String
)

@Serializable
internal data class OpenAITool(
    val type: String = "function",
    val function: OpenAIToolFunction
)

@Serializable
internal data class OpenAIToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
internal data class OpenAIResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
internal data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    val finishReason: String? = null
)

@Serializable
internal data class OpenAIUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int
)

@Serializable
internal data class OpenAIEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
internal data class OpenAIEmbeddingResponse(
    val data: List<OpenAIEmbeddingData>,
    val model: String,
    val usage: OpenAIUsage? = null
)

@Serializable
internal data class OpenAIEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)

@Serializable
internal data class OpenAIStreamResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIStreamChoice>
)

@Serializable
internal data class OpenAIStreamChoice(
    val index: Int,
    val delta: OpenAIStreamDelta,
    val finishReason: String? = null
)

@Serializable
internal data class OpenAIStreamDelta(
    val role: String? = null,
    val content: String? = null,
    val toolCalls: List<OpenAIToolCall>? = null
)

@Serializable
internal sealed interface OpenAIToolChoice {
    @JvmInline
    @Serializable
    value class Choice internal constructor(val value: String) : OpenAIToolChoice

    @Serializable
    data class FunctionName(val name: String)

    @Serializable
    data class Function(val function: FunctionName) : OpenAIToolChoice {
        val type: String = "function"
    }

    companion object {
        // OpenAI api is too "dynamic", have to inline value here, so alas, no proper classes hierarchy, creating "objects" instead
        val Auto = Choice("auto")
        val Required = Choice("required")
        val None = Choice("none")
    }
}

@Serializable
internal data class OpenAIAudioConfig(
    val format: OpenAIAudioFormat,
    val voice: OpenAIAudioVoice,
)

@Serializable
internal enum class OpenAIAudioFormat {
    @SerialName("wav")
    WAV,

    @SerialName("pcm16")
    PCM16,
}

@Serializable
internal enum class OpenAIAudioVoice {
    @SerialName("alloy")
    Alloy,
}

@Serializable
internal data class OpenAIAudio(
    val data: String,
    val transcript: String? = null,
)

internal object ContentSerializer : KSerializer<Content?> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Content", PolymorphicKind.SEALED)

    override fun serialize(encoder: Encoder, value: Content?) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            null -> jsonEncoder.encodeNull()
            is Content.Text -> jsonEncoder.encodeString(value.value)
            is Content.Parts -> jsonEncoder.encodeSerializableValue(
                ListSerializer(ContentPart.serializer()),
                value.value
            )
        }
    }

    override fun deserialize(decoder: Decoder): Content? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> Content.Text(element.content)
            is JsonArray -> Content.Parts(
                jsonDecoder.json.decodeFromJsonElement(
                    ListSerializer(ContentPart.serializer()),
                    element
                )
            )

            else -> throw SerializationException("Content must be either a string or an array")
        }
    }
}
