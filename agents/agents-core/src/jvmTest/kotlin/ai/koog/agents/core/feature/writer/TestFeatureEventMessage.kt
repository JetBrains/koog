package ai.koog.agents.core.feature.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import kotlinx.serialization.Serializable
import ai.koog.utils.time.AgentClock

@Serializable
data class TestFeatureEventMessage(
    override val eventId: String = "test-event-id",
    val testMessage: String,
    override val timestamp: Long = AgentClock.System.now().toEpochMilliseconds(),
) : FeatureEvent {

    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
