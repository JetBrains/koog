package ai.grazie.code.agents.local.features.message

import ai.grazie.utils.mpp.LoggerFactory

object FeatureMessageProcessorUtil {

    private val logger =
        LoggerFactory.create("ai.grazie.code.agents.local.features.logger.FeatureProviderUtil")

    suspend fun FeatureMessageProcessor.onMessageSafe(message: FeatureMessage) {
        try {
            this.processMessage(message)
        }
        catch (t: Throwable) {
            logger.error(t) { "Error while processing the provider onMessage handler: ${message.messageType.value}" }
        }
    }

    suspend fun List<FeatureMessageProcessor>.onMessageForEachSafe(message: FeatureMessage) {
        this.forEach { provider -> provider.onMessageSafe(message) }
    }
}
