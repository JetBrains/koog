package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal data class AssistantMessageEvent(
    private val provider: LLMProvider,
    private val message: Message.Response,
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("assistant.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        if (message.role != Message.Role.Assistant) {
            add(EventBodyFields.Role(role = message.role))
        }

        when (message) {
            is Message.Assistant -> {
                if (verbose) {
                    add(EventBodyFields.Content(content = message.content))
                }
            }

            is Message.Tool.Call -> {
                if (verbose) {
                    add(EventBodyFields.ToolCalls(tools = listOf(message)))
                }
            }
        }
    }
}
