package ai.koog.a2a.model

import kotlinx.serialization.Serializable

/**
 * Base interface for communication units, such as messages or tasks.
 */
@Serializable
public sealed interface CommunicationUnit {
    /**
     * The type used as discriminator.
     */
    public val kind: String
}
