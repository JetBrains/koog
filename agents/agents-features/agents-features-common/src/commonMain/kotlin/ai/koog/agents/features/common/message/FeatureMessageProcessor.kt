package ai.koog.agents.features.common.message

import ai.koog.agents.utils.Closeable

/**
 * Represents a provider responsible for handling outbound feature messages or events.
 *
 * Feature processors are used to encapsulate feature-related logic and provide a common interface
 * for handling feature messages and events, such as
 *   - node started
 *   - node finished
 *   - strategy started, etc.
 *
 * Implementations of this interface are designed to process feature messages,
 * which are encapsulated in the [FeatureMessage] type and presented as a model
 * for an event to be sent to a target stream. These messages carry
 * information about various events or updates related to features in the system.
 */
public abstract class FeatureMessageProcessor : Closeable {

    /**
     * Initializes the feature output stream provider to ensure it is ready for use.
     */
    public open suspend fun initialize() { }

    /**
     * Handles an incoming feature message or event for processing.
     *
     * @param message the feature message to be handled.
     */
    public abstract suspend fun processMessage(message: FeatureMessage)
}

