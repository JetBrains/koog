package ai.koog.agents.core.feature.message

import ai.koog.utils.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstract class responsible for processing feature messages or events within the system.
 *
 * The `FeatureMessageProcessor` serves as a foundational interface for implementing processors
 * that handle various feature-related messages. These messages, represented by the [FeatureMessage]
 * type, encapsulate relevant information about events or updates in the system.
 *
 * This class provides mechanisms to filter incoming messages, manage processing states, and ensure
 * proper handling through a defined workflow.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual abstract class FeatureMessageProcessor actual constructor() : Closeable {
    /**
     * A filter for messages to be sent to message processors.
     *
     * This function is called for each event before it's sent to the message processors.
     * If the function returns true, the event is processed; if it returns false, the event is ignored.
     *
     * By default, all messages are processed (the filter returns true for all messages).
     */
    public actual var messageFilter: (FeatureMessage) -> Boolean = { true }
        private set

    /**
     * Sets the message filter used to determine which feature messages should be processed.
     *
     * The provided filter function is invoked for each incoming feature message. If the filter
     * function returns `true`, the message is deemed to meet the criteria for further processing.
     *
     * @param filter A lambda function that accepts a [FeatureMessage] and returns a boolean
     * indicating whether the message should be processed (`true`) or ignored (`false`).
     */
    public actual fun setMessageFilter(filter: (FeatureMessage) -> Boolean) {
        messageFilter = filter
    }

    private val _isOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** A [StateFlow] representing the current open state of the processor. */
    public actual open val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    /** Initializes the feature output stream provider to ensure it is ready for use. */
    public actual open suspend fun initialize() {}

    /**
     * Handles an incoming feature message or event for processing.
     *
     * @param message the feature message to be handled.
     */
    protected actual open suspend fun processMessage(message: FeatureMessage) {}

    /**
     * Receives and processes an incoming feature message.
     *
     * This method evaluates the provided message using the configured message filter.
     * If the message passes the filter, it is forwarded for further processing.
     *
     * @param message The incoming feature message to be evaluated and potentially processed.
     */
    public actual suspend fun onMessage(message: FeatureMessage) {
        if (messageFilter(message)) {
            processMessage(message)
        }
    }

    /** Releases resources held by this processor. */
    actual override suspend fun close() {}
}
