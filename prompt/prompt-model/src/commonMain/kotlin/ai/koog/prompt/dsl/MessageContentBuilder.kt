package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * A message content builder class to support both text and attachments.
 *
 * @see TextContentBuilder
 * @see AttachmentBuilder
 */
@PromptDSL
public class MessageContentBuilder : TextContentBuilderBase<MessageContent>() {
    private var attachments: List<Attachment> = emptyList()

    /**
     * Configures media attachments for this content builder.
     */
    public fun attachments(body: AttachmentBuilder.() -> Unit) {
        attachments = AttachmentBuilder().apply(body).build()
    }

    /**
     * Builds and returns both the text content and attachments.
     */
    override fun build(): MessageContent = MessageContent(textBuilder.toString(), attachments)
}

/**
 * Message content with attachments
 */
public data class MessageContent(
    val content: String,
    val attachments: List<Attachment> = emptyList()
)
