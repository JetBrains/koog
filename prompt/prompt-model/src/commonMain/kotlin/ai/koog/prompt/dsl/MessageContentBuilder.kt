package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.text.TextContentBuilder

/**
 * A message content builder class to support both text and attachments.
 *
 * @see TextContentBuilder
 * @see AttachmentBuilder
 */
@PromptDSL
public class MessageContentBuilder {
    private var content: String = ""
    private var attachments: List<Attachment> = emptyList()

    /**
     * Configures text content for this content builder.
     */
    public fun content(body: TextContentBuilder.() -> Unit) {
        content = TextContentBuilder().apply(body).build()
    }

    /**
     * Configures media attachments for this content builder.
     */
    public fun attachments(body: AttachmentBuilder.() -> Unit) {
        attachments = AttachmentBuilder().apply(body).build()
    }

    /**
     * Builds and returns both the text content and attachments.
     */
    public fun build(): MessageContent = MessageContent(content, attachments)
}

/**
 * Message content with attachments
 */
public data class MessageContent(
    val content: String,
    val attachments: List<Attachment> = emptyList()
)
