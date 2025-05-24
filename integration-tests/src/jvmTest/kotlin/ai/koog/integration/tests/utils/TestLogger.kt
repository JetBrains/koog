package ai.koog.integration.tests.utils

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor


class TestLogPrinter : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        println(message)
    }

    override suspend fun close() {
    }
}