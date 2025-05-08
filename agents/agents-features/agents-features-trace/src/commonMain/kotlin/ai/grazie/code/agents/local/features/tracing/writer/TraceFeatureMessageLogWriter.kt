package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.local.features.common.model.*
import ai.grazie.code.agents.local.features.common.writer.FeatureMessageLogWriter
import ai.grazie.code.agents.core.feature.message.FeatureEvent
import ai.grazie.code.agents.core.feature.message.FeatureMessage
import ai.grazie.code.agents.core.feature.message.FeatureStringMessage
import ai.grazie.utils.mpp.MPPLogger

class TraceFeatureMessageLogWriter(
    targetLogger: MPPLogger,
    logLevel: LogLevel = LogLevel.INFO,
    private val format: ((FeatureMessage) -> String)? = null,
) : FeatureMessageLogWriter(targetLogger, logLevel) {

    companion object {
        val FeatureMessage.featureMessage
            get() = "Feature message"

        val FeatureEvent.featureEvent
            get() = "Feature event (eventId: ${this.eventId})"

        val FeatureStringMessage.featureStringMessage
            get() = "Feature string message (message: ${this.message})"

        val AgentCreateEvent.agentCreateEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val StrategyStartEvent.strategyStartEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val LLMCallStartEvent.llmCallStartEventFormat
            get() = "${this.eventId} (prompt: ${this.prompt})"

        val LLMCallEndEvent.llmCallEndEventFormat
            get() = "${this.eventId} (response: ${this.response})"

        val LLMCallWithToolsStartEvent.llmCallWithToolsStartEventFormat
            get() = "${this.eventId} (prompt: ${this.prompt}, tools: [${this.tools.joinToString(", ")}])"

        val LLMCallWithToolsEndEvent.llmCallWithToolsEndEventFormat
            get() = "${this.eventId} (responses: ${this.responses}, tools: [${this.tools.joinToString(", ")}])"

        val ToolCallsStartEvent.toolCallsStartEventFormat
            get() = "${this.eventId} (tools: [${tools.joinToString(", ")}])"

        val ToolCallsEndEvent.toolCallsEndEventFormat
            get() = "${this.eventId} (results: $results)"

        val NodeExecutionStartEvent.nodeExecutionStartEventFormat
            get() = "${this.eventId} (stage: ${this.stageName}, node: ${this.nodeName}, input: ${this.input})"

        val NodeExecutionEndEvent.nodeExecutionEndEventFormat
            get() = "${this.eventId} (stage: ${this.stageName}, node: ${this.nodeName}, input: ${this.input}, output: ${this.output})"
    }

    override fun FeatureMessage.toLoggerMessage(): String {
        if (format != null) {
            return format.invoke(this)
        }

        return when (this) {
            is AgentCreateEvent           -> { this.agentCreateEventFormat }
            is StrategyStartEvent         -> { this.strategyStartEventFormat }
            is LLMCallStartEvent          -> { this.llmCallStartEventFormat}
            is LLMCallEndEvent            -> { this.llmCallEndEventFormat}
            is LLMCallWithToolsStartEvent -> { this.llmCallWithToolsStartEventFormat }
            is LLMCallWithToolsEndEvent   -> { this.llmCallWithToolsEndEventFormat }
            is ToolCallsStartEvent        -> { this.toolCallsStartEventFormat }
            is ToolCallsEndEvent          -> { this.toolCallsEndEventFormat }
            is NodeExecutionStartEvent    -> { this.nodeExecutionStartEventFormat }
            is NodeExecutionEndEvent      -> { this.nodeExecutionEndEventFormat }
            is FeatureStringMessage       -> { this.featureStringMessage }
            is FeatureEvent               -> { this.featureEvent }
            else                          -> { this.featureMessage }
        }
    }
}
