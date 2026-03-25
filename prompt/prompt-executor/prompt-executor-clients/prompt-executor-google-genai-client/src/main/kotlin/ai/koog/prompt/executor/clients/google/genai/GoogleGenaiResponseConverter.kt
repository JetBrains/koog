package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.genai.types.Candidate
import com.google.genai.types.GenerateContentResponse
import io.github.oshai.kotlinlogging.KLogger
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
    private val conversionUtils: GoogleGenaiConversionUtils
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
                ?.let { conversionUtils.signatureFromBytes(it) }
            val isThought = part.thought().orElse(false)

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
                        ?.let { conversionUtils.convertMapToJsonObject(it).toString() } ?: "{}"
                    responses.add(
                        Message.Tool.Call(
                            id = Uuid.random().toString(),
                            tool = functionCall.name().orElse(""),
                            content = args,
                            metaInfo = metaInfo
                        )
                    )
                }

                inlineData != null -> {
                    val mimeType = inlineData.mimeType().orElse("application/octet-stream")
                    val data = inlineData.data().orElse(ByteArray(0))
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
}
