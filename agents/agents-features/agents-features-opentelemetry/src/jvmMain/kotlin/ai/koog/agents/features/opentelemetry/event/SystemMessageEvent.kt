package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal data class SystemMessageEvent(
    private val provider: LLMProvider,
    private val message: Message.System,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("system.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {

        add(EventBodyFields.Content(content = message.content))

        if (message.role != Message.Role.System) {
            add(EventBodyFields.Role(role = message.role))
        }
    }
}
