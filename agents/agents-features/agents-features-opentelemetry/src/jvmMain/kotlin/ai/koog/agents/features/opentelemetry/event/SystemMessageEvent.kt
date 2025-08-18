package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class SystemMessageEvent(
    provider: LLMProvider,
    private val message: Message.System
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("system.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        add(EventBodyFields.Role(role = message.role))
        add(EventBodyFields.Content(content = message.content))
    }
}
