package ai.koog.agents.core.environment

import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer

/**
 * Optional interface for tools that return non-text content (images, files, etc.) in their results.
 *
 * By default, tool results are serialized to a string and sent as [MessagePart.Text]. Implementing
 * this interface allows a tool to return a richer list of [MessagePart.ContentPart] — including images
 * or files — which will be forwarded as-is to the LLM client layer.
 *
 * Note: not all LLM providers support non-text content in tool results. Clients will log a warning
 * and fall back gracefully when an unsupported part type is encountered.
 *
 * Usage:
 * ```kotlin
 * class ScreenshotTool : Tool<Args, ByteArray>(...), MultimodalTool<Args, ByteArray> {
 *     override suspend fun execute(args: Args): ByteArray = captureScreen()
 *
 *     override fun encodeResultToParts(result: ByteArray, serializer: JSONSerializer) = listOf(
 *         MessagePart.Attachment(AttachmentSource.Image(AttachmentContent.Binary.Bytes(result), format = "png"))
 *     )
 * }
 * ```
 */
public interface MultimodalTool<TArgs, TResult> {
    /**
     * Encodes the tool result as a list of [MessagePart.ContentPart] to be sent to the LLM.
     *
     * @param result The result returned by [Tool.execute].
     * @param serializer The JSON serializer available for encoding structured data.
     * @return A list of content parts representing the tool result.
     */
    public fun encodeResultToParts(result: TResult, serializer: JSONSerializer): List<MessagePart.ContentPart>
}
