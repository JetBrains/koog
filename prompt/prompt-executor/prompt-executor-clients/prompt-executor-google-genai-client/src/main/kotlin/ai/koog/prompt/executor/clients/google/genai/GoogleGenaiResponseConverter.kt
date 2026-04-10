package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.executor.clients.google.genai.GoogleGenaiConversionUtils.convertMapToJsonObject
import ai.koog.prompt.executor.clients.google.genai.GoogleGenaiConversionUtils.signatureFromBytes
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.genai.types.Candidate
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Model
import io.github.oshai.kotlinlogging.KLogger
import kotlin.jvm.optionals.getOrDefault
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Converts Google GenAI SDK response types into Koog internal message format.
 *
 * @property logger Logger for warnings on unhandled parts.
 * @property clock Clock instance used for tracking response metadata timestamps.
 * @property conversionUtils Shared utilities for JSON conversion and signature decoding.
 */
internal class GoogleGenaiResponseConverter(
    private val logger: KLogger,
    private val clock: Clock,
) {

    /**
     * Extracts [ResponseMetaInfo] from a [GenerateContentResponse].
     */
    fun extractResponseMetaInfo(response: GenerateContentResponse): ResponseMetaInfo {
        val usageMetadata = response.usageMetadata().orElse(null)
        return ResponseMetaInfo.create(
            clock,
            totalTokensCount = usageMetadata?.totalTokenCount()?.orElse(null),
            inputTokensCount = usageMetadata?.promptTokenCount()?.orElse(null),
            outputTokensCount = usageMetadata?.candidatesTokenCount()?.orElse(null),
        )
    }

    /**
     * Processes a single [Candidate] into internal message format.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun processCandidate(
        candidate: Candidate,
        metaInfo: ResponseMetaInfo
    ): List<Message.Response> {
        val parts = candidate.content().orElse(null)?.parts()?.orElse(null).orEmpty()
        val finishReason = candidate.finishReason().orElse(null)?.toString()
        val responses = mutableListOf<Message.Response>()

        for (part in parts) {
            val signature = part.thoughtSignature().orElse(null)
                ?.let { signatureFromBytes(it) }
            val isThought = part.thought().getOrDefault(false)

            // Non-thought parts with a signature need a Reasoning carrier (unless already added)
            val needsSignatureCarrier =
                signature != null &&
                    !isThought &&
                    responses.none { it is Message.Reasoning && it.encrypted == signature }

            if (needsSignatureCarrier) {
                responses.add(Message.Reasoning(encrypted = signature, content = "", metaInfo = metaInfo))
            }

            val functionCall = part.functionCall().orElse(null)
            val text = part.text().orElse(null)
            val inlineData = part.inlineData().orElse(null)

            when {
                text != null -> {
                    if (isThought) {
                        val existing = if (signature != null) {
                            responses.filterIsInstance<Message.Reasoning>().find { it.encrypted == signature }
                        } else {
                            null
                        }

                        if (existing != null && existing.content.isEmpty()) {
                            val index = responses.indexOf(existing)
                            check(index >= 0) { "Reasoning message not found in responses" }
                            responses[index] =
                                existing.copy(parts = listOf(ContentPart.Text(text)))
                        } else {
                            responses.add(
                                Message.Reasoning(
                                    content = text,
                                    encrypted = signature,
                                    metaInfo = metaInfo
                                )
                            )
                        }
                    } else {
                        responses.add(
                            Message.Assistant(
                                content = text,
                                finishReason = finishReason,
                                metaInfo = metaInfo
                            )
                        )
                    }
                }

                functionCall != null -> {
                    val args = functionCall.args().orElse(null)
                        ?.let { convertMapToJsonObject(it).toString() } ?: "{}"
                    responses.add(
                        Message.Tool.Call(
                            id = Uuid.random().toString(),
                            tool = functionCall.name().getOrDefault(""),
                            content = args,
                            metaInfo = metaInfo
                        )
                    )
                }

                inlineData != null -> {
                    val mimeType = inlineData.mimeType().getOrDefault("application/octet-stream")
                    val data = inlineData.data().getOrDefault(ByteArray(0))
                    val contentPart = inlineDataToContentPart(mimeType, data)
                    responses.add(
                        Message.Assistant(
                            parts = listOf(contentPart),
                            finishReason = finishReason,
                            metaInfo = metaInfo
                        )
                    )
                }

                else -> {
                    logger.warn { "Unhandled part type in response: $part" }
                }
            }
        }

        return when {
            responses.any { it is Message.Tool.Call } ->
                responses.filter { it is Message.Reasoning || it is Message.Tool.Call }

            responses.isEmpty() -> listOf(
                Message.Assistant(content = "", finishReason = finishReason, metaInfo = metaInfo)
            )

            else -> responses
        }
    }

    /**
     * Maps an inline data blob from the SDK response to the appropriate [ContentPart] subtype.
     */
    private fun inlineDataToContentPart(mimeType: String, data: ByteArray): ContentPart = when {
        mimeType.startsWith("image/") -> ContentPart.Image(
            content = AttachmentContent.Binary.Bytes(data),
            format = mimeType.substringAfter("image/"),
            mimeType = mimeType,
        )

        mimeType.startsWith("audio/") -> ContentPart.Audio(
            content = AttachmentContent.Binary.Bytes(data),
            format = mimeType.substringAfter("audio/"),
            mimeType = mimeType,
        )

        mimeType.startsWith("video/") -> ContentPart.Video(
            content = AttachmentContent.Binary.Bytes(data),
            format = mimeType.substringAfter("video/"),
            mimeType = mimeType,
        )

        else -> ContentPart.File(
            content = AttachmentContent.Binary.Bytes(data),
            mimeType = mimeType,
            format = mimeType.substringAfterLast('/'),
        )
    }

    /**
     * Converts a Google GenAI SDK [Model] to a Koog [LLModel].
     *
     * If the model's name matches a known model in [knownModelsById], the known definition
     * (with pre-configured capabilities) is returned. Otherwise, a new [LLModel] is built
     * from the SDK metadata with capabilities inferred from [Model.supportedActions] and [Model.thinking].
     *
     * **NB! This method tries its best to infer capabilities from the model's metadata or name, but it's not
     * perfect. It's possible that the metadata does not accurately represent the model's capabilities.**
     *
     * @param model The SDK model descriptor returned by the list-models API.
     * @param provider The [LLMProvider] to assign (Google or Vertex).
     * @param knownModelsById Pre-defined models keyed by id, used for capability lookup.
     */
    fun convertModel(
        model: Model,
        provider: LLMProvider,
        knownModelsById: Map<String, LLModel> = emptyMap()
    ): LLModel {
        val name = model.name().orElse(null) ?: return LLModel(provider = provider, id = "unknown")

        // Strip the "models/" prefix that the API returns (e.g. "models/gemini-2.5-flash" → "gemini-2.5-flash")
        val id = name.removePrefix("models/")

        // Return the known model if we have one — it has hand-curated capabilities
        knownModelsById[id]?.let { return it }

        val actions = model.supportedActions().getOrDefault(emptyList())
        val supportsGeneration = "generateContent" in actions
        val supportsEmbedding = "embedContent" in actions || id.contains("embedding", ignoreCase = true)
        val supportsAudio = id.contains("audio", ignoreCase = true)
        val supportsImage = id.contains("image", ignoreCase = true)
        val supportsVideo = id.contains("veo", ignoreCase = true)

        // Build capabilities from SDK metadata for unknown models.
        // Modality capabilities (Vision, Audio, Video, Document) are NOT inferred here —
        // the API does not expose supported input modalities. Those are only set on
        // known models with hand-curated definitions in GoogleModels.
        val capabilities = buildList {
            if (supportsGeneration) {
                add(LLMCapability.Completion)
                add(LLMCapability.Temperature)
                add(LLMCapability.Tools)
                add(LLMCapability.ToolChoice)
                add(LLMCapability.MultipleChoices)
            }

            if (supportsEmbedding) {
                add(LLMCapability.Embed)
            }

            if (supportsImage) {
                add(LLMCapability.Vision.Image)
            }

            if (supportsAudio) {
                add(LLMCapability.Audio)
            }

            if (supportsVideo) {
                add(LLMCapability.Vision.Video)
            }

            if (model.thinking().getOrDefault(false)) {
                add(LLMCapability.Thinking)
            }
        }

        return LLModel(
            provider = provider,
            id = id,
            capabilities = capabilities,
            contextLength = model.inputTokenLimit().orElse(null)?.toLong(),
            maxOutputTokens = model.outputTokenLimit().orElse(null)?.toLong(),
        )
    }
}
