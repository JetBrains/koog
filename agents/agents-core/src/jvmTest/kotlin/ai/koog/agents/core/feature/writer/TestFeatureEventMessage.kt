package ai.koog.agents.core.feature.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.Serializable

@Serializable
data class TestFeatureEventMessage(
    override val eventId: String = "test-event-id",
    val testMessage: String,
    override val timestamp: Long = KoogClock.System.now().toEpochMilliseconds(),
) : FeatureEvent {

    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
