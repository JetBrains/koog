package ai.koog.agents.core.feature.config

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

    /**
     * Provides a read-only list of `FeatureMessageProcessor` instances registered with the feature configuration.
     */
    public val messageProcessors: List<FeatureMessageProcessor>
        get() = _messageProcessors.toList()

    /**
     * Adds a message processor to the configuration.
     */
    public fun addMessageProcessor(processor: FeatureMessageProcessor) {
        _messageProcessors.add(processor)
    }
}
