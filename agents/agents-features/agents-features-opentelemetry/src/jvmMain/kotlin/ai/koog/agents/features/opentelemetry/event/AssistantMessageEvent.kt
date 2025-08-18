package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

internal class AssistantMessageEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    private val arguments: JsonObject? = null,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("assistant.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        add(EventBodyFields.Role(role = message.role))
        when (message) {
            is Message.Assistant -> {
                add(EventBodyFields.Content(content = message.content))
                arguments?.let { add(EventBodyFields.Arguments(it)) }
            }

            is Message.Tool.Call -> {
                add(EventBodyFields.ToolCalls(tools = listOf(message)))
            }
        }
    }
}
