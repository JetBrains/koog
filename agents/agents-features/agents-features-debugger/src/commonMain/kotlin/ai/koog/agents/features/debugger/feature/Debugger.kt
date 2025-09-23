package ai.koog.agents.features.debugger.feature

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.model.events.AIAgentBeforeCloseEvent
import ai.koog.agents.core.feature.model.events.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.events.AIAgentGraphStrategyStartEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.events.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.events.AIAgentRunErrorEvent
import ai.koog.agents.core.feature.model.events.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.events.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.events.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.events.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.events.ToolCallEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailureEvent
import ai.koog.agents.core.feature.model.events.ToolCallResultEvent
import ai.koog.agents.core.feature.model.events.ToolValidationErrorEvent
import ai.koog.agents.core.feature.model.events.startNodeToGraph
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.debugger.EnvironmentVariablesReader
import ai.koog.agents.features.debugger.eventString
import ai.koog.agents.features.debugger.feature.writer.DebuggerFeatureMessageRemoteWriter
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Debugger feature provides the functionality of monitoring and recording events during
 * the operation of an AI agent. It integrates into an AI agent pipeline, allowing the
 * collection and processing of various agent events such as start, end, errors,
 * tool calls, and strategy executions.
 *
 * This feature serves as a debugging tool for analyzing the AI agent's behavior and
 * interactions with its components, providing insights into the execution flow and
 * potential issues.
 */
public class Debugger {

    /**
     * Represents a feature that integrates debugging capabilities into an AI agent's pipeline.
     *
     * This companion object provides functionality for configuring and enabling a debugging system
     * in the AI agent framework. It logs debugging events, connects to a debugging server, and handles
     * various stages of an agent's lifecycle such as start, finish, and error events.
     * Debugger Feature also tracks strategy executions, node executions, LLM calls, and tool operation events.
     *
     * The feature can be customized using the `DebuggerConfig` and works in tandem with the `AIAgentPipeline` infrastructure
     * to intercept various events and log them to a remote writer connected to a debugging server. The port for the debugger
     * server can either be explicitly set in the configuration or derived from environment variables.
     */
    public companion object Feature : AIAgentGraphFeature<DebuggerConfig, Debugger> {

        private val logger = KotlinLogging.logger { }

        private const val KOOG_DEBUGGER_PORT_ENV_VAR: String = "KOOG_DEBUGGER_PORT"

        override val key: AIAgentStorageKey<Debugger> =
            AIAgentStorageKey("agents-features-debugger")

        override fun createInitialConfig(): DebuggerConfig = DebuggerConfig()

        override fun install(
            config: DebuggerConfig,
            pipeline: AIAgentGraphPipeline,
        ) {
            logger.debug { "Debugger Feature. Start installing feature: ${Debugger::class.simpleName}" }

            // Config that will be used to connect to the debugger server where
            // port is taken from environment variables if not set explicitly

            val port = config.port ?: readPortFromEnvironmentVariables()
            logger.debug { "Debugger Feature. Use debugger port: $port" }

            val debuggerServerConfig = DefaultServerConnectionConfig(
                port = port,
                waitConnection = true
            )

            val writer = DebuggerFeatureMessageRemoteWriter(connectionConfig = debuggerServerConfig)
            config.addMessageProcessor(writer)

            val interceptContext = InterceptContext(this, Debugger())

            //region Intercept Agent Events

            pipeline.interceptBeforeAgentStarted(interceptContext) intercept@{ eventContext ->
                val event = AIAgentStartedEvent(
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAgentFinished(interceptContext) intercept@{ eventContext ->
                val event = AIAgentFinishedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAgentRunError(interceptContext) intercept@{ eventContext ->
                val event = AIAgentRunErrorEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAgentBeforeClosed(interceptContext) intercept@{ eventContext ->
                val event = AIAgentBeforeCloseEvent(
                    agentId = eventContext.agentId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(interceptContext) intercept@{ eventContext ->

                val strategy = eventContext.strategy as AIAgentGraphStrategy

                @OptIn(InternalAgentsApi::class)
                val event = AIAgentGraphStrategyStartEvent(
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name,
                    graph = strategy.startNodeToGraph(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptStrategyFinished(interceptContext) intercept@{ eventContext ->
                val event = AIAgentStrategyFinishedEvent(
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(interceptContext) intercept@{ eventContext ->
                val event = AIAgentNodeExecutionStartEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = eventContext.input?.toString() ?: "",
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAfterNode(interceptContext) intercept@{ eventContext ->
                val event = AIAgentNodeExecutionEndEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = eventContext.input?.toString() ?: "",
                    output = eventContext.output?.toString() ?: "",
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(interceptContext) intercept@{ eventContext ->
                val event = BeforeLLMCallEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.eventString,
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAfterLLMCall(interceptContext) intercept@{ eventContext ->
                val event = AfterLLMCallEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.eventString,
                    responses = eventContext.responses,
                    moderationResponse = eventContext.moderationResponse,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(interceptContext) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<ToolArgs, ToolResult>

                val event = ToolCallEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptToolValidationError(interceptContext) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<ToolArgs, ToolResult>

                val event = ToolValidationErrorEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    error = eventContext.error,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptToolCallFailure(interceptContext) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<ToolArgs, ToolResult>

                val event = ToolCallFailureEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptToolCallResult(interceptContext) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<ToolArgs, ToolResult>

                val event = ToolCallResultEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    result = eventContext.result?.let { result -> tool.encodeResultToString(result) },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Tool Call Events
        }

        //region Private Methods

        private fun readPortFromEnvironmentVariables(): Int? {
            val debuggerPortVariable =
                EnvironmentVariablesReader.getEnvironmentVariable(name = KOOG_DEBUGGER_PORT_ENV_VAR)

            logger.debug { "Debugger Feature. Reading port from environment variable: KOOG_DEBUGGER_PORT_ENV_VAR=$debuggerPortVariable" }
            return debuggerPortVariable?.toIntOrNull()
        }

        //endregion Private Methods
    }
}
