package ai.koog.prompt.dsl

import ai.koog.prompt.message.Content
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * A message content builder class to support both text and attachments.
 *
 * @see TextContentBuilder
 * @see ContentPartsBuilder
 */
@PromptDSL
public class MessageContentBuilder : TextContentBuilderBase<Content>() {
    private var contentParts: List<ContentPart> = emptyList()

    /**
     * Configures media attachments for this content builder.
     */
    public fun attachments(body: ContentPartsBuilder.() -> Unit) {
        contentParts = ContentPartsBuilder().apply(body).build()
    }

    /**
     * Builds and returns both the text content and attachments.
     */
    override fun build(): Content {
        if (contentParts.isEmpty()) {
            return Content.Text(textBuilder.toString())
        }
        val text = textBuilder.toString()
        if (text.isEmpty()) {
            return Content.Parts(contentParts)
        }

        return Content.Parts(listOf(ContentPart.Text(text)) + contentParts)
    }
}
