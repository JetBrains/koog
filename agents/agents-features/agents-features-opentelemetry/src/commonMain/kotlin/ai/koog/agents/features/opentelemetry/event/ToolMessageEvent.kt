package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

/**
 * Event emitted for a tool-result message.
 *
 * @param provider LLM provider associated with the originating tool call.
 * @param toolCallId Identifier of the tool call this result belongs to, or `null` if unknown.
 * @param content Tool execution result.
 */
public class ToolMessageEvent(
    provider: LLMProvider,
    private val toolCallId: String?,
    private val content: String,
) : GenAIAgentEvent() {

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = Message.Role.Tool))
        addBodyField(EventBodyFields.Content(content = content))
        toolCallId?.let { id ->
            addBodyField(EventBodyFields.Id(id = id))
        }
    }

    override val name: String = super.name.concatName("tool.message")
}
