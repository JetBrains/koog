package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json

internal class ModerationResponseEvent(
    provider: LLMProvider,
    private val moderationResult: ModerationResult,
    override val verbose: Boolean = false,
) : GenAIAgentEvent {

    override val name: String = "moderation.result"

    override val bodyFields: List<EventBodyField> = buildList {
        add(EventBodyFields.Role(role = Message.Role.Assistant))
        add(EventBodyFields.Content(content = Json.encodeToString(ModerationResult.serializer(), moderationResult)))
    }

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }
}
