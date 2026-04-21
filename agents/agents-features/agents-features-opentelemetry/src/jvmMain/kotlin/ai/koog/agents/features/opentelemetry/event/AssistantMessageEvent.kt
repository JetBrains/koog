package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

internal class AssistantMessageEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    private val arguments: JsonObject? = null,
) : GenAIAgentEvent() {

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = message.role))

        when (message) {
            is Message.Assistant, is Message.Reasoning -> {
                addBodyField(EventBodyFields.Content(content = message.content))
                arguments?.let { addBodyField(EventBodyFields.Arguments(it)) }
            }

            is Message.Tool.Call -> {
                addBodyField(EventBodyFields.ToolCalls(tools = listOf(message)))
            }
        }
    }

    override val name: String = super.name.concatName("assistant.message")
}
