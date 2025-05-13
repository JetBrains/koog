package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.local.features.common.message.FeatureEvent
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureStringMessage
import ai.grazie.code.agents.local.features.common.writer.FeatureMessageLogWriter
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
            get() = "Feature message"

        val FeatureStringMessage.featureStringMessage
            get() = "Feature string message (message: ${this.message})"

        val AgentCreateEvent.agentCreateEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val AgentStartedEvent.agentStartedEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val AgentFinishedEvent.agentFinishedEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName}, result: ${this.result})"

        val AgentRunErrorEvent.agentRunErrorEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName}, error: ${this.error.message})"

        val StrategyStartEvent.strategyStartEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val StrategyFinishedEvent.strategyFinishedEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName}, result: ${this.result})"

        val LLMCallStartEvent.llmCallStartEventFormat
            get() = "${this.eventId} (prompt: ${this.prompt})"

        val LLMCallEndEvent.llmCallEndEventFormat
            get() = "${this.eventId} (response: ${this.response})"

        val LLMCallWithToolsStartEvent.llmCallWithToolsStartEventFormat
            get() = "${this.eventId} (prompt: ${this.prompt}, tools: [${this.tools.joinToString(", ")}])"

        val LLMCallWithToolsEndEvent.llmCallWithToolsEndEventFormat
            get() = "${this.eventId} (responses: ${this.responses}, tools: [${this.tools.joinToString(", ")}])"

        val ToolCallEvent.toolCallEventFormat
            get() = "${this.eventId} (stage: ${this.stageName}, tool: ${this.toolName}, tool args: ${this.toolArgs})"

        val ToolValidationErrorEvent.toolValidationErrorEventFormat
            get() = "${this.eventId} (stage: ${this.stageName}, tool: ${this.toolName}, tool args: ${this.toolArgs}, validation error: ${this.errorMessage})"

        val ToolCallFailureEvent.toolCallFailureEventFormat
            get() = "${this.eventId} (stage: ${this.stageName}, tool: ${this.toolName}, tool args: ${this.toolArgs}, error: ${this.error.message})"

        val ToolCallResultEvent.toolCallResultEventFormat
            get() = "${this.eventId} (stage: ${this.stageName}, tool: ${this.toolName}, tool args: ${this.toolArgs}, result: ${this.result})"

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
            is AgentStartedEvent          -> { this.agentStartedEventFormat }
            is AgentFinishedEvent         -> { this.agentFinishedEventFormat }
            is AgentRunErrorEvent         -> { this.agentRunErrorEventFormat}
            is StrategyStartEvent         -> { this.strategyStartEventFormat }
            is StrategyFinishedEvent      -> { this.strategyFinishedEventFormat }
            is LLMCallStartEvent          -> { this.llmCallStartEventFormat}
            is LLMCallEndEvent            -> { this.llmCallEndEventFormat}
            is LLMCallWithToolsStartEvent -> { this.llmCallWithToolsStartEventFormat }
            is LLMCallWithToolsEndEvent   -> { this.llmCallWithToolsEndEventFormat }
            is ToolCallEvent              -> { this.toolCallEventFormat }
            is ToolValidationErrorEvent   -> { this.toolValidationErrorEventFormat }
            is ToolCallFailureEvent       -> { this.toolCallFailureEventFormat }
            is ToolCallResultEvent        -> { this.toolCallResultEventFormat }
            is NodeExecutionStartEvent    -> { this.nodeExecutionStartEventFormat }
            is NodeExecutionEndEvent      -> { this.nodeExecutionEndEventFormat }
            is FeatureStringMessage       -> { this.featureStringMessage }
            is FeatureEvent               -> { this.featureEvent }
            else                          -> { this.featureMessage }
        }
    }
}
