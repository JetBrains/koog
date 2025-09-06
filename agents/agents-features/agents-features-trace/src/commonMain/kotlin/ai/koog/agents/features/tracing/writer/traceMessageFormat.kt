package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AIAgentBeforeCloseEvent
import ai.koog.agents.core.feature.model.events.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionErrorEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.events.AIAgentRunErrorEvent
import ai.koog.agents.core.feature.model.events.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.events.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.events.AIAgentStrategyStartEvent
import ai.koog.agents.core.feature.model.events.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.events.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.events.ToolCallEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailureEvent
import ai.koog.agents.core.feature.model.events.ToolCallResultEvent
import ai.koog.agents.core.feature.model.events.ToolValidationErrorEvent
import ai.koog.agents.features.tracing.traceString

@Suppress("UnusedReceiverParameter")
internal val FeatureMessage.featureMessage
    get() = "Feature message"

@Suppress("UnusedReceiverParameter")
internal val FeatureEvent.featureEvent
    get() = "Feature event"

internal val FeatureStringMessage.featureStringMessage
    get() = "Feature string message (message: $message)"

internal val AIAgentStartedEvent.agentStartedEventFormat
    get() = "$eventId (agent id: $agentId, run id: $runId)"

internal val AIAgentFinishedEvent.agentFinishedEventFormat
    get() = "$eventId (agent id: $agentId, run id: $runId, result: $result)"

internal val AIAgentRunErrorEvent.agentRunErrorEventFormat
    get() = "$eventId (agent id: $agentId, run id: $runId, error: ${error.message})"

internal val AIAgentBeforeCloseEvent.agentBeforeCloseFormat
    get() = "$eventId (agent id: $agentId)"

internal val AIAgentStrategyStartEvent.strategyStartEventFormat
    get() = "$eventId (run id: $runId, strategy: $strategyName)"

internal val AIAgentStrategyFinishedEvent.strategyFinishedEventFormat
    get() = "$eventId (run id: $runId, strategy: $strategyName, result: $result)"

internal val AIAgentNodeExecutionStartEvent.nodeExecutionStartEventFormat
    get() = "$eventId (run id: $runId, node: $nodeName, input: $input)"

internal val AIAgentNodeExecutionEndEvent.nodeExecutionEndEventFormat
    get() = "$eventId (run id: $runId, node: $nodeName, input: $input, output: $output)"

internal val AIAgentNodeExecutionErrorEvent.nodeExecutionErrorEventFormat
    get() = "$eventId (run id: $runId, node: $nodeName, error: ${error.message})"

internal val BeforeLLMCallEvent.beforeLLMCallEventFormat
    get() = "$eventId (run id: $runId, prompt: ${prompt.traceString}, model: $model, tools: [${tools.joinToString()}])"

internal val AfterLLMCallEvent.afterLLMCallEventFormat
    get() = "$eventId (run id: $runId, prompt: ${prompt.traceString}, model: $model, responses: [${
        responses.joinToString {
            "{${it.traceString}}"
        }
    }])"

internal val ToolCallEvent.toolCallEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs)"

internal val ToolValidationErrorEvent.toolValidationErrorEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs, validation error: $error)"

internal val ToolCallFailureEvent.toolCallFailureEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs, error: ${error.message})"

internal val ToolCallResultEvent.toolCallResultEventFormat
    get() = "$eventId (run id: $runId, tool: $toolName, tool args: $toolArgs, result: $result)"

internal val FeatureMessage.traceMessage: String
    get() {
        return when (this) {
            is AIAgentStartedEvent -> this.agentStartedEventFormat
            is AIAgentFinishedEvent -> this.agentFinishedEventFormat
            is AIAgentRunErrorEvent -> this.agentRunErrorEventFormat
            is AIAgentBeforeCloseEvent -> this.agentBeforeCloseFormat
            is AIAgentStrategyStartEvent -> this.strategyStartEventFormat
            is AIAgentStrategyFinishedEvent -> this.strategyFinishedEventFormat
            is AIAgentNodeExecutionStartEvent -> this.nodeExecutionStartEventFormat
            is AIAgentNodeExecutionEndEvent -> this.nodeExecutionEndEventFormat
            is AIAgentNodeExecutionErrorEvent -> this.nodeExecutionErrorEventFormat
            is BeforeLLMCallEvent -> this.beforeLLMCallEventFormat
            is AfterLLMCallEvent -> this.afterLLMCallEventFormat
            is ToolCallEvent -> this.toolCallEventFormat
            is ToolValidationErrorEvent -> this.toolValidationErrorEventFormat
            is ToolCallFailureEvent -> this.toolCallFailureEventFormat
            is ToolCallResultEvent -> this.toolCallResultEventFormat
            is FeatureStringMessage -> this.featureStringMessage
            is FeatureEvent -> this.featureEvent
            else -> this.featureMessage
        }
    }
