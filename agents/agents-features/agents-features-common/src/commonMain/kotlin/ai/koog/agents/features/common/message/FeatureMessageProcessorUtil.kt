package ai.koog.agents.features.common.message

import io.github.oshai.kotlinlogging.KotlinLogging


/**
 * Utility object providing safe handling and processing of feature messages for collections
 * and individual `FeatureMessageProcessor` implementations.
 *
 * This utility ensures that exceptions thrown during message processing are caught and logged
 * without interrupting the execution flow. It is particularly useful in scenarios where multiple
 * processors are involved, and a failing processor shouldn't impact the processing of others.
 */
public object FeatureMessageProcessorUtil {

    private val logger = KotlinLogging.logger {  }

    internal suspend fun FeatureMessageProcessor.onMessageSafe(message: FeatureMessage) {
        try {
            this.processMessage(message)
        }
        catch (t: Throwable) {
            logger.error(t) { "Error while processing the provider onMessage handler: ${message.messageType.value}" }
        }
    }

    /**
     * Safely processes a given feature message using each `FeatureMessageProcessor` in the list.
     *
     * This method iterates through each `FeatureMessageProcessor` in the list and calls
     * their `onMessageSafe` method to process the provided `FeatureMessage`. Any exceptions
     * that occur during the message processing are caught and logged, ensuring that an error
     * in one processor does not disrupt the processing of the remaining processors.
     *
     * @param message The feature message to be processed by each `FeatureMessageProcessor`.
     */
    public suspend fun List<FeatureMessageProcessor>.onMessageForEachSafe(message: FeatureMessage) {
        this.forEach { provider -> provider.onMessageSafe(message) }
    }
}
