package ai.grazie.code.agents.core.feature.message

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

interface FeatureMessage {
    val timestamp: Long
    val messageType: Type

    enum class Type(val value: String) {
        Message("message"),
        Event("event")
    }
}

interface FeatureEvent : FeatureMessage {
    val eventId: String
}

@Serializable
data class FeatureStringMessage(val message: String) : FeatureMessage {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Message
}

@Serializable
data class FeatureEventMessage(override val eventId: String) : FeatureEvent {
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}
