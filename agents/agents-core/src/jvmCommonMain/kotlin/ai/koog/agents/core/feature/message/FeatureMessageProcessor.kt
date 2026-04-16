package ai.koog.agents.core.feature.message

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runBlockingIfRequired
import ai.koog.utils.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService

/**
 * Abstract class responsible for processing feature messages within the system.
 *
 * The `FeatureMessageProcessor` provides functionality for filtering and handling
 * incoming feature messages. Classes inheriting from this abstract class are expected
 * to implement the necessary logic for processing messages.
 *
 * This class provides Java-friendly APIs for initializing and processing messages
 * synchronously, in addition to its coroutine-based methods for use in Kotlin.
 *
 * Java subclasses should override [handleMessage] and [handleClose] instead of the
 * suspend [processMessage] and [close] methods to avoid dealing with Kotlin coroutine internals.
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

    // JAVA Unique methods:

    /**
     * Initializes the feature output stream provider to ensure it is ready for use.
     * This is a blocking version of [initialize] for use from Java code.
     *
     * @param executorService An optional [ExecutorService] to provide a coroutine context for execution.
     *                        If not provided, [Dispatchers.Default] is used.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("initialize")
    @OptIn(InternalAgentsApi::class)
    public fun javaNonSuspendInitialize(executorService: ExecutorService? = null) {
        runBlockingIfRequired(executorService?.asCoroutineDispatcher() ?: Dispatchers.Default) {
            initialize()
        }
    }

    /**
     * Receives and processes an incoming feature message.
     * This is a blocking version of [onMessage] for use from Java code.
     *
     * @param message The incoming feature message to be evaluated and potentially processed.
     * @param executorService An optional [ExecutorService] to provide a coroutine context for execution.
     *                        If not provided, [Dispatchers.Default] is used.
     */
    @JavaAPI
    @JvmOverloads
    @JvmName("onMessage")
    @OptIn(InternalAgentsApi::class)
    public fun javaNonSuspendOnMessage(message: FeatureMessage, executorService: ExecutorService? = null) {
        runBlockingIfRequired(executorService?.asCoroutineDispatcher() ?: Dispatchers.Default) {
            onMessage(message)
        }
    }

    /**
     * Handles an incoming feature message or event for processing.
     * This is a non-suspend version of [processMessage] for overriding from Java code.
     *
     * Java subclasses should override this method instead of [processMessage] to avoid
     * dealing with Kotlin coroutine internals (Continuation parameter).
     *
     * @param message the feature message to be handled.
     */
    @JavaAPI
    protected open fun handleMessage(message: FeatureMessage) {}

    /**
     * Releases resources held by this processor.
     * This is a non-suspend version of [close] for overriding from Java code.
     *
     * Java subclasses should override this method instead of to suspend [close] to avoid
     * dealing with Kotlin coroutine internals (Continuation parameter).
     */
    @JavaAPI
    public open fun handleClose() {}

    // Common (multiplatform) methods:

    /** Initializes the feature output stream provider to ensure it is ready for use. */
    public actual open suspend fun initialize() {
        _isOpen.value = true
    }

    /**
     * Handles an incoming feature message or event for processing.
     * The default implementation delegates to [handleMessage].
     *
     * @param message the feature message to be handled.
     */
    protected actual open suspend fun processMessage(message: FeatureMessage) {
        handleMessage(message)
    }

    /**
     * Releases resources held by this processor.
     * The default implementation delegates to [handleClose].
     */
    actual override suspend fun close() {
        handleClose()
        _isOpen.value = false
    }

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
}
