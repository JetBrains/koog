package ai.grazie.code.agents.local.features.tracing.feature

import ai.grazie.code.agents.core.feature.config.FeatureConfig
import ai.grazie.code.agents.core.feature.message.FeatureMessage

/**
 * Configuration for the tracing feature.
 */
class TraceFeatureConfig() : FeatureConfig() {

    /**
     * A filter for messages to be sent to the tracing message processor.
     */
    var messageFilter: (FeatureMessage) -> Boolean = { true }
}
