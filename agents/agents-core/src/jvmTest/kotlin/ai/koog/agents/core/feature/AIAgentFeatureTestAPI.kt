package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.FunctionalStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.GraphStrategyStartingEvent
import ai.koog.agents.core.feature.model.events.LLMCallCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMCallStartingEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingCompletedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFailedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingFrameReceivedEvent
import ai.koog.agents.core.feature.model.events.LLMStreamingStartingEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.StrategyCompletedEvent
import ai.koog.agents.core.feature.model.events.StrategyEventGraph
import ai.koog.agents.core.feature.model.events.StrategyEventGraphEdge
import ai.koog.agents.core.feature.model.events.StrategyEventGraphNode
import ai.koog.agents.core.feature.model.events.SubgraphExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.SubgraphExecutionStartingEvent
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import ai.koog.agents.core.system.mock.MockLLMProvider
import ai.koog.agents.testing.agent.agentExecutionInfo
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.toModelInfo
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object AIAgentFeatureTestAPI {

    internal val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    internal val mockLLModel = LLModel(
        provider = MockLLMProvider(),
        id = "test-llm-id",
        capabilities = emptyList(),
        contextLength = 1_000,
    )

    internal val featureStringMessage = FeatureStringMessage(
        message = "feature string message",
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val agentStartingEvent = AgentStartingEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id"),
        agentId = "test-agent-id",
        runId = "test-run-id",
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val agentCompletedEvent = AgentCompletedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id"),
        agentId = "test-agent-id",
        runId = "test-run-id",
        result = "test-result",
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val agentClosingEvent = AgentClosingEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id"),
        agentId = "test-agent-id",
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val agentExecutionFailedEvent = AgentExecutionFailedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id"),
        agentId = "test-agent-id",
        runId = "test-run-id",
        error = AIAgentError(
            message = "test-error-message",
            stackTrace = "test-error-stacktrace",
            cause = "test-error-cause"
        ),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val graphNode1 = StrategyEventGraphNode("test-node-1-id", "test-node-1-name")
    internal val graphNode2 = StrategyEventGraphNode("test-node-2-id", "test-node-2-name")
    internal val graphEdge = StrategyEventGraphEdge(graphNode1, graphNode2)
    internal val graphStrategyStartingEvent = run {
        val strategyName = "test-strategy-name"
        GraphStrategyStartingEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", strategyName),
            runId = "test-run-id",
            strategyName = strategyName,
            graph = StrategyEventGraph(nodes = listOf(graphNode1, graphNode2), edges = listOf(graphEdge)),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val functionalStrategyStartingEvent = run {
        val strategyName = "test-strategy-name"
        FunctionalStrategyStartingEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", strategyName),
            runId = "test-run-id",
            strategyName = strategyName,
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val strategyCompletedEvent = run {
        val strategyName = "test-strategy-name"
        StrategyCompletedEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", strategyName),
            runId = "test-run-id",
            strategyName = strategyName,
            result = "test-result",
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val nodeExecutionStartingEvent = run {
        val nodeName = "test-node-name"
        NodeExecutionStartingEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", nodeName),
            runId = "test-run-id",
            nodeName = nodeName,
            input = JsonPrimitive("test-input"),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val nodeExecutionCompletedEvent = run {
        val nodeName = "test-node-name"
        NodeExecutionCompletedEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", nodeName),
            runId = "test-run-id",
            nodeName = nodeName,
            input = JsonPrimitive("test-input"),
            output = JsonPrimitive("test-output"),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val nodeExecutionFailedEvent = run {
        val nodeName = "test-node-name"
        NodeExecutionFailedEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", nodeName),
            runId = "test-run-id",
            nodeName = nodeName,
            input = JsonPrimitive("test-input"),
            error = AIAgentError(
                message = "test-error-message",
                stackTrace = "test-error-stacktrace",
                cause = "test-error-cause"
            ),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val subgraphExecutionStartingEvent = run {
        val subgraphName = "test-subgraph-name"
        SubgraphExecutionStartingEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", subgraphName),
            runId = "test-run-id",
            subgraphName = subgraphName,
            input = JsonPrimitive("test-input"),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val subgraphExecutionCompletedEvent = run {
        val subgraphName = "test-subgraph-name"
        SubgraphExecutionCompletedEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", subgraphName),
            runId = "test-run-id",
            subgraphName = subgraphName,
            input = JsonPrimitive("test-input"),
            output = JsonPrimitive("test-output"),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val subgraphExecutionFailedEvent = run {
        val subgraphName = "test-subgraph-name"
        SubgraphExecutionFailedEvent(
            eventId = "test-event-id",
            executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", subgraphName),
            runId = "test-run-id",
            subgraphName = subgraphName,
            input = JsonPrimitive("test-input"),
            error = AIAgentError(
                message = "test-error-message",
                stackTrace = "test-error-stacktrace",
                cause = "test-error-cause"
            ),
            timestamp = testClock.now().toEpochMilliseconds()
        )
    }

    internal val toolCallStartingEvent = ToolCallStartingEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        toolCallId = "test-tool-call-id",
        toolName = "test-tool-name",
        toolArgs = JsonObject(mapOf("test-argument-key" to JsonPrimitive("test-argument-value"))),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val toolValidationFailedEvent = ToolValidationFailedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        toolCallId = "test-tool-call-id",
        toolName = "test-tool-name",
        toolArgs = JsonObject(mapOf("test-argument-key" to JsonPrimitive("test-argument-value"))),
        toolDescription = "test-tool-description",
        message = "test-error-message",
        error = AIAgentError("test-error-message", "test-error-stacktrace", "test-error-cause"),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val toolCallFailedEvent = ToolCallFailedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        toolCallId = "test-tool-call-id",
        toolName = "test-tool-name",
        toolArgs = JsonObject(mapOf("test-argument-key" to JsonPrimitive("test-argument-value"))),
        toolDescription = "test-tool-description",
        error = AIAgentError(
            message = "test-error-message",
            stackTrace = "test-error-stacktrace",
            cause = "test-error-cause"
        ),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val toolCallCompletedEvent = ToolCallCompletedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        toolCallId = "test-tool-call-id",
        toolName = "test-tool-name",
        toolArgs = JsonObject(mapOf("test-argument-key" to JsonPrimitive("test-argument-value"))),
        toolDescription = "test-tool-description",
        result = JsonPrimitive("test-result"),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val llmCallStartingEvent = LLMCallStartingEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        prompt = Prompt(
            id = "test-prompt-id",
            messages = listOf(
                Message.System(
                    part = ContentPart.Text("test-system-message"),
                    metaInfo = RequestMetaInfo(timestamp = testClock.now())
                )
            ),
            params = LLMParams()
        ),
        model = mockLLModel.toModelInfo(),
        tools = listOf("test-tool"),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val llmCallCompletedEvent = LLMCallCompletedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        prompt = Prompt(
            id = "test-prompt-id",
            messages = listOf(
                Message.System(
                    part = ContentPart.Text("test-system-message"),
                    metaInfo = RequestMetaInfo(timestamp = testClock.now())
                )
            ),
            params = LLMParams()
        ),
        model = mockLLModel.toModelInfo(),
        responses = listOf(
            Message.Assistant(
                content = "test-assistant-message",
                metaInfo = ResponseMetaInfo(timestamp = testClock.now())
            )
        ),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val llmStreamingStartingEvent = LLMStreamingStartingEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        prompt = Prompt(
            id = "test-prompt-id",
            messages = listOf(
                Message.System(
                    part = ContentPart.Text("test-system-message"),
                    metaInfo = RequestMetaInfo(timestamp = testClock.now())
                )
            ),
            params = LLMParams()
        ),
        model = mockLLModel.toModelInfo(),
        tools = listOf("test-tool"),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val llmStreamingFrameReceivedEvent = LLMStreamingFrameReceivedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        prompt = Prompt(
            id = "test-prompt-id",
            messages = listOf(
                Message.System(
                    part = ContentPart.Text("test-system-message"),
                    metaInfo = RequestMetaInfo(timestamp = testClock.now())
                )
            ),
            params = LLMParams()
        ),
        model = mockLLModel.toModelInfo(),
        frame = StreamFrame.Append("test-frame"),
        timestamp = testClock.now().toEpochMilliseconds(),
    )

    internal val llmStreamingFailedEvent = LLMStreamingFailedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        prompt = Prompt(
            id = "test-prompt-id",
            messages = listOf(
                Message.System(
                    part = ContentPart.Text("test-system-message"),
                    metaInfo = RequestMetaInfo(timestamp = testClock.now())
                )
            ),
            params = LLMParams()
        ),
        model = mockLLModel.toModelInfo(),
        error = AIAgentError(
            message = "test-error-message",
            stackTrace = "test-error-stacktrace",
            cause = "test-error-cause"
        ),
        timestamp = testClock.now().toEpochMilliseconds(),
    )

    internal val llmStreamingCompletedEvent = LLMStreamingCompletedEvent(
        eventId = "test-event-id",
        executionInfo = agentExecutionInfo("test-agent-id", "test-strategy-name", "test-node-name"),
        runId = "test-run-id",
        prompt = Prompt(
            id = "test-prompt-id",
            messages = listOf(
                Message.System(
                    part = ContentPart.Text("test-system-message"),
                    metaInfo = RequestMetaInfo(timestamp = testClock.now())
                )
            ),
            params = LLMParams()
        ),
        model = mockLLModel.toModelInfo(),
        tools = listOf("test-tool"),
        timestamp = testClock.now().toEpochMilliseconds()
    )

    internal val knownDefinedEvents = listOf(
        featureStringMessage,
        agentStartingEvent,
        agentCompletedEvent,
        agentClosingEvent,
        agentExecutionFailedEvent,
        graphStrategyStartingEvent,
        functionalStrategyStartingEvent,
        strategyCompletedEvent,
        nodeExecutionStartingEvent,
        nodeExecutionCompletedEvent,
        nodeExecutionFailedEvent,
        subgraphExecutionStartingEvent,
        subgraphExecutionCompletedEvent,
        subgraphExecutionFailedEvent,
        toolCallStartingEvent,
        toolValidationFailedEvent,
        toolCallFailedEvent,
        toolCallCompletedEvent,
        llmCallStartingEvent,
        llmCallCompletedEvent,
        llmStreamingStartingEvent,
        llmStreamingFrameReceivedEvent,
        llmStreamingFailedEvent,
        llmStreamingCompletedEvent,
    )
}
