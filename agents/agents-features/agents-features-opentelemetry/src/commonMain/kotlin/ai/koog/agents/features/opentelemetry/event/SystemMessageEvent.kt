package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

/**
 * Event emitted for a system-role chat message.
 *
 * @param provider LLM provider that received the message.
 * @param message System message being recorded.
 */
public class SystemMessageEvent(
    provider: LLMProvider,
    private val message: Message.System
) : GenAIAgentEvent() {

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = message.role))
        addBodyField(EventBodyFields.Content(content = message.content))
    }

    override val name: String = super.name.concatName("system.message")
}
