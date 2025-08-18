package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json

internal class ModerationResponseEvent(
    provider: LLMProvider,
    private val moderationResult: ModerationResult
) : GenAIAgentEvent() {

    companion object {
        private val json = Json { allowStructuredMapKeys = true }
    }

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = Message.Role.Assistant))
        addBodyField(EventBodyFields.Content(content = json.encodeToString(ModerationResult.serializer(), moderationResult)))
    }

    override val name: String = "moderation.result"
}
