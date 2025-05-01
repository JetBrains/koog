package ai.grazie.code.agents.local.features.message

import kotlinx.coroutines.CompletableDeferred

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
abstract class FeatureMessageProcessor : Closeable {

    /**
     * A property representing the readiness status of the provider.
     */
    val isReady: CompletableDeferred<Boolean> = CompletableDeferred()

    /**
     * Initializes the feature output stream provider to ensure it is ready for use.
     */
    open suspend fun initialize() {
        isReady.complete(true)
    }

    /**
     * Handles an incoming feature message or event for processing.
     *
     * @param message the feature message to be handled.
     */
    abstract suspend fun processMessage(message: FeatureMessage)
}
