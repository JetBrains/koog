package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureEvent
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallFailedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyStartingEventBase
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
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
    get() = "${this::class.simpleName} (agent id: $agentId, run id: $runId)"

internal val AgentCompletedEvent.agentFinishedEventFormat
    get() = "${this::class.simpleName} (agent id: $agentId, run id: $runId, result: $result)"

internal val AgentExecutionFailedEvent.agentRunErrorEventFormat
    get() = "${this::class.simpleName} (agent id: $agentId, run id: $runId, error: ${error?.message})"

internal val AgentClosingEvent.agentBeforeCloseFormat
    get() = "${this::class.simpleName} (agent id: $agentId)"

internal val StrategyStartingEventBase.strategyStartEventFormat
    get() = "${this::class.simpleName} (run id: $runId, strategy: $strategyName)"

internal val StrategyCompletedEvent.strategyFinishedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, strategy: $strategyName, result: $result)"

internal val NodeExecutionStartingEvent.nodeExecutionStartEventFormat
    get() = "${this::class.simpleName} (run id: $runId, node: $nodeName, input: $input)"

internal val NodeExecutionCompletedEvent.nodeExecutionEndEventFormat
    get() = "${this::class.simpleName} (run id: $runId, node: $nodeName, input: $input, output: $output)"

internal val NodeExecutionFailedEvent.nodeExecutionErrorEventFormat
    get() = "${this::class.simpleName} (run id: $runId, node: $nodeName, error: ${error.message})"

internal val LLMCallStartingEvent.beforeLLMCallEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}])"

internal val LLMCallCompletedEvent.afterLLMCallEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, response: ${response?.traceString}])"

internal val LLMCallFailedEvent.llmCallFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}], error: ${error.message})"

internal val LLMStreamingStartingEvent.llmStreamingStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}])"

internal val LLMStreamingFrameReceivedEvent.llmStreamingFrameReceivedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, model: ${model.modelIdentifierName}, frame: $frame)"

internal val LLMStreamingFailedEvent.llmStreamingFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, error: ${error.message})"

internal val LLMStreamingCompletedEvent.llmStreamingCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, prompt: ${prompt.traceString}, model: ${model.modelIdentifierName}, tools: [${tools.joinToString()}])"

internal val SubgraphExecutionStartingEvent.subgraphExecutionStartingEventFormat
    get() = "${this::class.simpleName} (run id: $runId, subgraph: $subgraphName, input: $input)"

internal val SubgraphExecutionCompletedEvent.subgraphExecutionCompletedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, subgraph: $subgraphName, input: $input, output: $output)"

internal val SubgraphExecutionFailedEvent.subgraphExecutionFailedEventFormat
    get() = "${this::class.simpleName} (run id: $runId, subgraph: $subgraphName, input: $input, error: ${error.message})"

internal val ToolCallStartingEvent.toolCallEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs)"

internal val ToolValidationFailedEvent.toolValidationErrorEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs, validation error: $error)"

internal val ToolCallFailedEvent.toolCallFailureEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs, error: ${error?.message})"

internal val ToolCallCompletedEvent.toolCallResultEventFormat
    get() = "${this::class.simpleName} (run id: $runId, tool: $toolName, tool args: $toolArgs, description: $toolDescription, result: $result)"

internal val FeatureMessage.traceMessage: String
    get() {
        return when (this) {
            is AgentStartingEvent -> this.agentStartedEventFormat
            is AgentCompletedEvent -> this.agentFinishedEventFormat
            is AgentExecutionFailedEvent -> this.agentRunErrorEventFormat
            is AgentClosingEvent -> this.agentBeforeCloseFormat
            is StrategyStartingEventBase -> this.strategyStartEventFormat
            is StrategyCompletedEvent -> this.strategyFinishedEventFormat
            is NodeExecutionStartingEvent -> this.nodeExecutionStartEventFormat
            is NodeExecutionCompletedEvent -> this.nodeExecutionEndEventFormat
            is NodeExecutionFailedEvent -> this.nodeExecutionErrorEventFormat
            is SubgraphExecutionStartingEvent -> this.subgraphExecutionStartingEventFormat
            is SubgraphExecutionCompletedEvent -> this.subgraphExecutionCompletedEventFormat
            is SubgraphExecutionFailedEvent -> this.subgraphExecutionFailedEventFormat
            is LLMCallStartingEvent -> this.beforeLLMCallEventFormat
            is LLMCallCompletedEvent -> this.afterLLMCallEventFormat
            is LLMCallFailedEvent -> this.llmCallFailedEventFormat
            is LLMStreamingStartingEvent -> this.llmStreamingStartingEventFormat
            is LLMStreamingFrameReceivedEvent -> this.llmStreamingFrameReceivedEventFormat
            is LLMStreamingFailedEvent -> this.llmStreamingFailedEventFormat
            is LLMStreamingCompletedEvent -> this.llmStreamingCompletedEventFormat
            is ToolCallStartingEvent -> this.toolCallEventFormat
            is ToolValidationFailedEvent -> this.toolValidationErrorEventFormat
            is ToolCallFailedEvent -> this.toolCallFailureEventFormat
            is ToolCallCompletedEvent -> this.toolCallResultEventFormat
            is FeatureStringMessage -> this.featureStringMessage
            is FeatureEvent -> this.featureEvent
            else -> this.featureMessage
        }
    }
