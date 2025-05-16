package ai.jetbrains.code.integration.tests

import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureMessageProcessor


class TestLogPrinter : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        println(message)
    }

    override suspend fun close() {
    }
}