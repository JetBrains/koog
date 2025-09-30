package ai.koog.agents.core.feature.config

import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.message.FeatureMessageProcessor

/**
 * Abstract base class for configuring features with stream providers.
 *
 * This class provides mechanisms to manage a list of `FeatureMessageProcessor` instances, which are responsible for
 * handling outbound feature events or messages (e.g., node started, strategy started). Subclasses may extend this
 * configuration class to define additional settings specific to a feature.
 */
public abstract class FeatureConfig {

    private val _messageProcessors = mutableListOf<FeatureMessageProcessor>()

    private var _eventFilter: (AgentLifecycleEventContext) -> Boolean = { true }

    /**
     * Provides a read-only list of `FeatureMessageProcessor` instances registered with the feature configuration.
     */
    public val messageProcessors: List<FeatureMessageProcessor>
        get() = _messageProcessors.toList()

    /**
     * A filter for events to be processed by a feature.
     */
    public val eventFilter: (AgentLifecycleEventContext) -> Boolean
        get() = _eventFilter

    /**
     * Adds a message processor to the configuration.
     */
    public fun addMessageProcessor(processor: FeatureMessageProcessor) {
        _messageProcessors.add(processor)
    }

    /**
     * A filter for messages to be sent to the tracing message processors.
     *
     * This function is called for each trace event before it's sent to the message processors.
     * If the function returns true, the event is processed; if it returns false, the event is ignored.
     *
     * By default, all messages are processed (the filter returns true for all messages).
     *
     * Example:
     * ```kotlin
     * // Only trace LLM-related events
     * setMessageFilter { message ->
     *     message is LLMCallStartEvent ||
     *     message is LLMCallEndEvent
     * }
     * ```
     */
    public fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        _eventFilter = filter
    }
}
