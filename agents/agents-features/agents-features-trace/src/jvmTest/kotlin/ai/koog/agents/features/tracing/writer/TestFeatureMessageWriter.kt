package ai.koog.agents.features.tracing.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import io.github.oshai.kotlinlogging.KotlinLogging

class TestFeatureMessageWriter : FeatureMessageProcessor() {

    private val _messages = mutableListOf<FeatureMessage>()

    val messages: List<FeatureMessage> get() =
        _messages.toList()

    companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.features.tracing.writer.TestEventMessageWriter")
    }

    override suspend fun processMessage(message: FeatureMessage) {
        logger.info { "Process feature message: $message" }
        _messages.add(message)
    }

    override suspend fun close() {
        logger.info { "Closing test event message writer" }
    }
}
