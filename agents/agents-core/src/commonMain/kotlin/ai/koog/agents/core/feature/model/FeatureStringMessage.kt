package ai.koog.agents.core.feature.model

import ai.koog.agents.core.feature.message.FeatureMessage
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents a detailed implementation of [ai.koog.agents.core.feature.message.FeatureMessage] that encapsulates a string message.
 *
 * This class associates a string content with a specific feature-related message type, along with
 * a timestamp indicating when the message was created.
 *
 * It is primarily used for text-based feature messages and integrates with the [ai.koog.agents.core.feature.message.FeatureMessage]
 * interface to define its structure.
 *
 * Instances of this type are timestamped at the moment of their creation, ensuring consistent
 * temporal tracking for feature messages.
 *
 * @property message The textual message content encapsulated by this feature message.
 * @property timestamp The time at which this message was created has represented in milliseconds since the epoch.
 */
@Serializable
public data class FeatureStringMessage(
    val message: String,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
) : FeatureMessage {

    /**
     * Represents the type of the feature message, identifying the message's purpose or category.
     *
     * In this implementation, the `messageType` property is set to `FeatureMessage.Type.Message`,
     * which classifies the message as a standard feature-related message.
     *
     * The `messageType` property enables proper categorization and handling of feature messages
     * within the system, facilitating streamlined processing and functionality differentiation
     * based on the message type.
     */
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Message
}
