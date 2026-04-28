package ai.koog.prompt.dsl

import ai.koog.prompt.message.MessagePart

/**
 * A builder for constructing parts for prompt messages.
 * All parts are added to a list in declaration order and can be retrieved through the [build] method.
 *
 * Example usage:
 * ```kotlin
 * val parts = ContentMessagePartBuilder().apply {
 *     text("Hello! Here's an image:")
 *     image("screenshot.png")
 *     + "And here's a file:"
 *     binaryFile("report.pdf")
 * }.build()
 * ```
 *
 * @see MessagePart.ContentPart
 */
@PromptDSL
public open class ContentMessagePartsBuilder : ContentPartsBuilderBase<List<MessagePart.ContentPart>>() {
    override fun build(): List<MessagePart.ContentPart> {
        flushTextBuilder()
        return contentParts
    }
}
