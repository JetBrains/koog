package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * Base interface for events.
 */
@Serializable(with = UpdateEventSerializer::class)
public sealed interface UpdateEvent {
    /**
     * The type used as discriminator.
     */
    public val kind: String
}

/**
 * Base interface for communication units, such as messages or tasks.
 */
@Serializable(with = CommunicationEventSerializer::class)
public sealed interface CommunicationEvent : UpdateEvent
