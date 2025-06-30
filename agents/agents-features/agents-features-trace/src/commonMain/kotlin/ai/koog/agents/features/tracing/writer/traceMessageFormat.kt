package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.core.feature.traceString
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage

internal val FeatureMessage.featureMessage
    get() = "Feature message"

internal val FeatureEvent.featureEvent
    get() = "Feature event"

internal val FeatureStringMessage.featureStringMessage
    get() = "Feature string message (message: ${message})"

internal val AIAgentStartedEvent.agentStartedEventFormat
    get() = "$eventId (agent id: ${agentId}, session id: ${sessionId}, strategy: ${strategyName})"

internal val AIAgentFinishedEvent.agentFinishedEventFormat
    get() = "$eventId (agent id: ${agentId}, session id: ${sessionId}, result: ${result})"

internal val AIAgentRunErrorEvent.agentRunErrorEventFormat
    get() = "$eventId (agent id: ${agentId}, session id: ${sessionId}, error: ${error.message})"

internal val AIAgentBeforeCloseEvent.agentBeforeCloseFormat
    get() = "$eventId (agent id: ${agentId})"

internal val AIAgentStrategyStartEvent.strategyStartEventFormat
    get() = "$eventId (session id: ${sessionId}, strategy: ${strategyName})"

internal val AIAgentStrategyFinishedEvent.strategyFinishedEventFormat
    get() = "$eventId (session id: ${sessionId}, strategy: ${strategyName}, result: ${result})"

internal val AIAgentNodeExecutionStartEvent.nodeExecutionStartEventFormat
    get() = "$eventId (session id: ${sessionId}, node: ${nodeName}, input: ${input})"

internal val AIAgentNodeExecutionEndEvent.nodeExecutionEndEventFormat
    get() = "$eventId (session id: ${sessionId}, node: ${nodeName}, input: ${input}, output: ${output})"

internal val BeforeLLMCallEvent.llmCallStartEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, tools: [${tools.joinToString()}])"

internal val AfterLLMCallEvent.llmCallEndEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, responses: [${responses.joinToString { it.traceString }}])"

internal val ToolCallEvent.toolCallEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs})"

internal val ToolValidationErrorEvent.toolValidationErrorEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, validation error: ${error})"

internal val ToolCallFailureEvent.toolCallFailureEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, error: ${error.message})"

internal val ToolCallResultEvent.toolCallResultEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, result: ${result})"

internal val FeatureMessage.traceMessage: String
    get() {
        return when (this) {
            is AIAgentStartedEvent               -> this.agentStartedEventFormat
            is AIAgentFinishedEvent              -> this.agentFinishedEventFormat
            is AIAgentRunErrorEvent              -> this.agentRunErrorEventFormat
            is AIAgentBeforeCloseEvent           -> this.agentBeforeCloseFormat
            is AIAgentStrategyStartEvent         -> this.strategyStartEventFormat
            is AIAgentStrategyFinishedEvent      -> this.strategyFinishedEventFormat
            is AIAgentNodeExecutionStartEvent    -> this.nodeExecutionStartEventFormat
            is AIAgentNodeExecutionEndEvent      -> this.nodeExecutionEndEventFormat
            is BeforeLLMCallEvent                -> this.llmCallStartEventFormat
            is AfterLLMCallEvent                 -> this.llmCallEndEventFormat
            is ToolCallEvent                     -> this.toolCallEventFormat
            is ToolValidationErrorEvent          -> this.toolValidationErrorEventFormat
            is ToolCallFailureEvent              -> this.toolCallFailureEventFormat
            is ToolCallResultEvent               -> this.toolCallResultEventFormat
            is FeatureStringMessage              -> this.featureStringMessage
            is FeatureEvent                      -> this.featureEvent
            else                                 -> this.featureMessage
        }
    }
