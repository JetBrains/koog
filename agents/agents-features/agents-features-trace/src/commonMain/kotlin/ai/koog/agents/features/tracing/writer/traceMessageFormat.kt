package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import ai.koog.agents.features.tracing.traceString

@Suppress("UnusedReceiverParameter")
internal val FeatureMessage.featureMessage
    get() = "Feature message"

@Suppress("UnusedReceiverParameter")
internal val FeatureEvent.featureEvent
    get() = "Feature event"

internal val FeatureStringMessage.featureStringMessage
    get() = "Feature string message (message: $message)"

internal val AgentStartingEvent.agentStartedEventFormat
    get() = "$eventId (agent id: $agentId, run id: $runId)"

internal val AgentCompletedEvent.agentFinishedEventFormat
    get() = "$eventId (agent id: $agentId, run id: $runId, result: $result)"

internal val AgentExecutionFailedEvent.agentRunErrorEventFormat
    get() = "$eventId (agent id: $agentId, run id: $runId, error: ${error.message})"

internal val AgentClosingEvent.agentBeforeCloseFormat
    get() = "$eventId (agent id: $agentId)"

internal val StrategyStartingEvent.strategyStartEventFormat
    get() = "$eventId (run id: $runId, strategy: $strategyName)"

internal val StrategyCompletedEvent.strategyFinishedEventFormat
    get() = "$eventId (run id: $runId, strategy: $strategyName, result: $result)"

internal val NodeExecutionStartingEvent.nodeExecutionStartEventFormat
    get() = "$eventId (run id: $runId, node: $nodeName, input: $input)"

internal val NodeExecutionCompletedEvent.nodeExecutionEndEventFormat
    get() = "$eventId (run id: $runId, node: $nodeName, input: $input, output: $output)"

internal val NodeExecutionFailedEvent.nodeExecutionErrorEventFormat
    get() = "$eventId (run id: $runId, node: $nodeName, error: ${error.message})"

internal val LLMCallStartingEvent.beforeLLMCallEventFormat
    get() = "$eventId (run id: $runId, prompt: ${prompt.traceString}, model: $model, tools: [${tools.joinToString()}])"

internal val LLMCallCompletedEvent.afterLLMCallEventFormat
    get() = "$eventId (run id: $runId, prompt: ${prompt.traceString}, model: $model, responses: [${
        responses.joinToString {
            "{${it.traceString}}"
        }
    }])"

internal val ToolCallStartingEvent.toolCallEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs)"

internal val ToolValidationFailedEvent.toolValidationErrorEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs, validation error: $error)"

internal val ToolCallFailedEvent.toolCallFailureEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs, error: ${error.message})"

internal val ToolCallCompletedEvent.toolCallResultEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs, result: $result)"

internal val FeatureMessage.traceMessage: String
    get() {
        return when (this) {
            is AgentStartingEvent -> this.agentStartedEventFormat
            is AgentCompletedEvent -> this.agentFinishedEventFormat
            is AgentExecutionFailedEvent -> this.agentRunErrorEventFormat
            is AgentClosingEvent -> this.agentBeforeCloseFormat
            is StrategyStartingEvent -> this.strategyStartEventFormat
            is StrategyCompletedEvent -> this.strategyFinishedEventFormat
            is NodeExecutionStartingEvent -> this.nodeExecutionStartEventFormat
            is NodeExecutionCompletedEvent -> this.nodeExecutionEndEventFormat
            is NodeExecutionFailedEvent -> this.nodeExecutionErrorEventFormat
            is LLMCallStartingEvent -> this.beforeLLMCallEventFormat
            is LLMCallCompletedEvent -> this.afterLLMCallEventFormat
            is ToolCallStartingEvent -> this.toolCallEventFormat
            is ToolValidationFailedEvent -> this.toolValidationErrorEventFormat
            is ToolCallFailedEvent -> this.toolCallFailureEventFormat
            is ToolCallCompletedEvent -> this.toolCallResultEventFormat
            is FeatureStringMessage -> this.featureStringMessage
            is FeatureEvent -> this.featureEvent
            else -> this.featureMessage
        }
    }
