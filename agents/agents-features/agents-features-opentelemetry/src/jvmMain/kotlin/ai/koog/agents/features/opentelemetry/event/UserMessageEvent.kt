package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class UserMessageEvent(
    provider: LLMProvider,
    private val message: Message.User
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("user.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {

        if (message.role != Message.Role.User) {
            add(EventBodyFields.Role(role = message.role))
        }

        add(EventBodyFields.Content(content = message.content))
    }
}
