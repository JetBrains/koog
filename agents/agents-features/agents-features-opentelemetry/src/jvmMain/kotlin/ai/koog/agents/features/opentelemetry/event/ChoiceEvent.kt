package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    override val verbose: Boolean = false,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("choice")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        add(EventBodyFields.Index(0))

        when (message) {
            is Message.Assistant -> {
                message.finishReason?.let { reason ->
                    add(EventBodyFields.FinishReason(reason))
                }

                if (verbose) {
                    add(EventBodyFields.Message(
                        role = message.role.takeIf { role -> role != Message.Role.Assistant },
                        content = message.content
                    ))
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
