package ai.grazie.code.agents.features.writer

import ai.grazie.code.agents.features.common.message.FeatureEvent
import ai.grazie.code.agents.features.common.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class TestFeatureEventMessage(
    val testMessage: String,
    override val eventId: String = TestFeatureEventMessage::class.simpleName!!
) : FeatureEvent {

    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()

    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
