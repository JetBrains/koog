package ai.koog.agentcore.runtime

import aws.sdk.kotlin.services.bedrockruntime.model.AudioFormat
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.DocumentFormat
import aws.sdk.kotlin.services.bedrockruntime.model.ImageFormat
import aws.sdk.kotlin.services.bedrockruntime.model.VideoFormat
import io.ktor.http.ContentType

/** Maps a Bedrock [ImageFormat] to its corresponding [ContentType] (e.g. `png` -> `image/png`). */
internal fun ImageFormat.toContentType(): ContentType = ContentType("image", value)

/** Maps a Bedrock [AudioFormat] to its corresponding [ContentType] (e.g. `mp3` -> `audio/mpeg`). */
internal fun AudioFormat.toContentType(): ContentType = when (this) {
    is AudioFormat.Mp3 -> ContentType("audio", "mpeg")
    else -> ContentType("audio", value)
}

/** Maps a Bedrock [VideoFormat] to its corresponding [ContentType] (e.g. `mp4` -> `video/mp4`). */
internal fun VideoFormat.toContentType(): ContentType = ContentType("video", value)

/** Maps a Bedrock [DocumentFormat] to its corresponding [ContentType] (e.g. `pdf` -> `application/pdf`). */
internal fun DocumentFormat.toContentType(): ContentType = when (this) {
    is DocumentFormat.Txt -> ContentType.Text.Plain
    is DocumentFormat.Html -> ContentType.Text.Html
    is DocumentFormat.Csv -> ContentType("text", "csv")
    is DocumentFormat.Md -> ContentType("text", "markdown")
    is DocumentFormat.Pdf -> ContentType.Application.Pdf
    else -> ContentType.Application.OctetStream
}

/**
 * Converts a Bedrock [ContentBlock] (typically from a `Converse`/`ConverseStream` output message)
 * into an [InvocationResult] suitable for an HTTP response.
 *
 * - [ContentBlock.Text] becomes [InvocationResult.Text].
 * - [ContentBlock.Image], [ContentBlock.Audio], [ContentBlock.Video] and [ContentBlock.Document]
 *   become [InvocationResult.Binary] with a content type derived from the block's `format`,
 *   provided the block carries inline bytes (an `S3Location` source is not proxied).
 *
 * @throws AgentCoreInvocationException for blocks that cannot be represented as a single HTTP body
 *   (e.g. tool use/result, reasoning, or a binary block backed only by an `S3Location`).
 */
public fun ContentBlock.toInvocationResult(): InvocationResult = when (this) {
    is ContentBlock.Text -> InvocationResult.Text(value)
    is ContentBlock.Image -> InvocationResult.Binary(
        value.source?.asBytesOrNull() ?: unsupportedBinary("Image"),
        value.format.toContentType()
    )
    is ContentBlock.Audio -> InvocationResult.Binary(
        value.source?.asBytesOrNull() ?: unsupportedBinary("Audio"),
        value.format.toContentType()
    )
    is ContentBlock.Video -> InvocationResult.Binary(
        value.source?.asBytesOrNull() ?: unsupportedBinary("Video"),
        value.format.toContentType()
    )
    is ContentBlock.Document -> InvocationResult.Binary(
        value.source?.asBytesOrNull() ?: unsupportedBinary("Document"),
        value.format.toContentType()
    )
    else -> throw AgentCoreInvocationException(
        "ContentBlock '${this::class.simpleName}' cannot be mapped to a single HTTP response body"
    )
}

private fun unsupportedBinary(kind: String): Nothing = throw AgentCoreInvocationException(
    "$kind content block has no inline bytes (S3Location sources are not proxied)"
)
