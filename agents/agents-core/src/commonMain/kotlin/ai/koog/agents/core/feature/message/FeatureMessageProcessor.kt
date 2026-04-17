package ai.koog.agents.core.feature.message

import ai.koog.utils.io.Closeable
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a provider responsible for handling outbound feature messages or events.
 *
 * Feature processors are used to encapsulate feature-related logic and provide a common interface
 * for handling feature messages and events, such as
 *   - Node started
 *   - Node finished
 *   - Strategy started, etc.
 *
 * Implementations of this interface are designed to process feature messages,
 * which are encapsulated in the [FeatureMessage] type and presented as a model
 * for an event to be sent to a target stream. These messages carry
 * information about various events or updates related to features in the system.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect abstract class FeatureMessageProcessor() : Closeable {
    /**
     * A filter for messages to be sent to message processors.
     *
     * This function is called for each event before it's sent to the message processors.
     * If the function returns true, the event is processed; if it returns false, the event is ignored.
     *
     * By default, all messages are processed (the filter returns true for all messages).
     */
    public var messageFilter: (FeatureMessage) -> Boolean
        private set

    /**
     * Sets the message filter used to determine which feature messages should be processed.
     *
     * The provided filter function is invoked for each incoming feature message. If the filter
     * function returns `true`, the message is deemed to meet the criteria for further processing.
     *
     * @param filter A lambda function that accepts a [FeatureMessage] and returns a boolean
     * indicating whether the message should be processed (`true`) or ignored (`false`).
     *
     * Example:
     * ```kotlin
     *   processor.setMessageFilter { message ->
     *     message is LLMCallStartingEvent ||
     *     message is LLMCallCompletedEvent
     *   }
     * ```
     */
    public fun setMessageFilter(filter: (FeatureMessage) -> Boolean)

    /**
     * A [StateFlow] representing the current open state of the processor.
     */
    public open val isOpen: StateFlow<Boolean>

    /**
     * Initializes the feature output stream provider to ensure it is ready for use.
     */
    public open suspend fun initialize()

    /**
     * Handles an incoming feature message or event for processing.
     *
     * @param message the feature message to be handled.
     */
    protected open suspend fun processMessage(message: FeatureMessage)

    /**
     * Receives and processes an incoming feature message.
     *
     * This method evaluates the provided message using the configured message filter.
     * If the message passes the filter, it is forwarded for further processing.
     *
     * @param message The incoming feature message to be evaluated and potentially processed.
     */
    public suspend fun onMessage(message: FeatureMessage)

    /**
     * Releases resources held by this processor.
     */
    public open override suspend fun close()
}
