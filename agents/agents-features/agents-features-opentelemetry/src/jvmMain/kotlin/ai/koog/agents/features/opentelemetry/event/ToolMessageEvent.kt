package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ToolMessageEvent(
    provider: LLMProvider,
    private val toolCallId: String?,
    private val content: String,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("tool.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        // Role (conditional).
        add(EventBodyFields.Role(role = Message.Role.Tool))

        // Content
        add(EventBodyFields.Content(content = content))

        // Id
        toolCallId?.let { id ->
            add(EventBodyFields.Id(id = id))
        }
    }
}
