package ai.koog.integration.tests.utils

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestLogPrinter : FeatureMessageProcessor() {

    override val isOpen: StateFlow<Boolean>
        get() = MutableStateFlow(true)

    override suspend fun processMessage(message: FeatureMessage) {
        println(message)
    }

    override suspend fun close() {
    }
}
