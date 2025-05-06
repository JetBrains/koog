package ai.jetbrains.code.prompt.executor.llms.all

import ai.grazie.code.agents.local.features.message.FeatureMessage
import ai.grazie.code.agents.local.features.message.FeatureMessageProcessor


class TestLogPrinter : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        println(message)
    }

    override suspend fun close() {
    }
}