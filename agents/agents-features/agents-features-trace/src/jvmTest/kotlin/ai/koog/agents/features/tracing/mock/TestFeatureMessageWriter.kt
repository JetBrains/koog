package ai.koog.agents.features.tracing.mock

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestFeatureMessageWriter : FeatureMessageProcessor() {

    var runId: String = ""

    private val _messages = mutableListOf<FeatureMessage>()

    val messages: List<FeatureMessage> get() =
        _messages.toList()

    companion object {
        private val logger = KotlinLogging.logger("ai.koog.agents.features.tracing.writer.TestEventMessageWriter")
    }

    override val isOpen: StateFlow<Boolean> =
        MutableStateFlow(true)

    override suspend fun processMessage(message: FeatureMessage) {
        logger.info { "Process feature message: $message" }

        if (message is AgentStartingEvent) {
            runId = message.runId
        }

        _messages.add(message)
    }

    override suspend fun close() {
        logger.info { "Closing test event message writer" }
        runId = ""
    }
}
