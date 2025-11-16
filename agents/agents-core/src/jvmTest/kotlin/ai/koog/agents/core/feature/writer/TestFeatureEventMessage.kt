package ai.koog.agents.core.feature.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import kotlin.time.Clock
import kotlinx.serialization.Serializable

@Serializable
data class TestFeatureEventMessage(
    val testMessage: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : FeatureEvent {

    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
