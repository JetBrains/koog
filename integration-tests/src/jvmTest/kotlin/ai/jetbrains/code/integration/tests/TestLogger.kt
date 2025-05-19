package ai.jetbrains.code.integration.tests

import ai.koog.agents.local.features.common.message.FeatureMessage
import ai.koog.agents.local.features.common.message.FeatureMessageProcessor


class TestLogPrinter : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        println(message)
    }

    override suspend fun close() {
    }
}