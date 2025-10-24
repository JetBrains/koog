package ai.koog.agents.features.debugger.feature

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentCompletedEvent
import ai.koog.agents.core.feature.model.events.AgentExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
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
import ai.koog.agents.core.feature.model.events.ToolCallCompletedEvent
import ai.koog.agents.core.feature.model.events.ToolCallFailedEvent
import ai.koog.agents.core.feature.model.events.ToolCallStartingEvent
import ai.koog.agents.core.feature.model.events.ToolValidationFailedEvent
import ai.koog.agents.core.feature.model.events.startNodeToGraph
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.utils.SerializationUtil
import ai.koog.agents.features.debugger.feature.writer.DebuggerFeatureMessageRemoteWriter
import ai.koog.agents.features.debugger.readEnvironmentVariable
import ai.koog.agents.features.debugger.readVMOption
import ai.koog.prompt.llm.toModelInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
     * Companion object implementing agent feature, handling [Debugger] creation and installation.
     */
    public companion object Feature : AIAgentGraphFeature<DebuggerConfig, Debugger> {

        private val logger = KotlinLogging.logger { }

        /**
         * The name of the environment variable used to specify the port number for the Koog debugger.
         */
        public const val KOOG_DEBUGGER_PORT_ENV_VAR: String = "KOOG_DEBUGGER_PORT"

        /**
         * Name of the environment variable used to specify the timeout duration (in milliseconds)
         * for the debugger to wait for a connection.
         */
        public const val KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR: String = "KOOG_DEBUGGER_WAIT_CONNECTION_MS"

        /**
         * The name of the VM option used to specify the port number for the Koog debugger.
         */
        public const val KOOG_DEBUGGER_PORT_VM_OPTION: String = "koog.debugger.port"

        /**
         * The name of the JVM option used to configure the timeout duration (in milliseconds)
         * for the Koog debugger to wait for a connection.
         */
        public const val KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION: String = "koog.debugger.wait.connection.ms"

        override val key: AIAgentStorageKey<Debugger> =
            AIAgentStorageKey("agents-features-debugger")

        override fun createInitialConfig(): DebuggerConfig = DebuggerConfig()

        override fun install(
            config: DebuggerConfig,
            pipeline: AIAgentGraphPipeline,
        ): Debugger {
            logger.debug { "Debugger Feature. Start installing feature: ${Debugger::class.simpleName}" }

            // Config that will be used to connect to the debugger server where
            // port is taken from environment variables if not set explicitly

            val port = config.port ?: readPortValue()
            logger.debug { "Debugger Feature. Use debugger port: $port" }

            val awaitInitialConnectionTimeout = config.awaitInitialConnectionTimeout ?: readWaitConnectionTimeout()
            logger.debug { "Debugger Feature. Use debugger server wait connection timeout: $awaitInitialConnectionTimeout" }

            val debuggerServerConfig = DefaultServerConnectionConfig(
                port = port,
                awaitInitialConnection = true,
                awaitInitialConnectionTimeout = awaitInitialConnectionTimeout,
            )

            val writer = DebuggerFeatureMessageRemoteWriter(connectionConfig = debuggerServerConfig)
            config.addMessageProcessor(writer)

            val debugger = Debugger()

            //region Intercept Agent Events

            pipeline.interceptAgentStarting(this) intercept@{ eventContext ->
                val event = AgentStartingEvent(
                    agentId = eventContext.agent.id,
                    runId = eventContext.runId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAgentCompleted(this) intercept@{ eventContext ->
                val event = AgentCompletedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAgentExecutionFailed(this) intercept@{ eventContext ->
                val event = AgentExecutionFailedEvent(
                    agentId = eventContext.agentId,
                    runId = eventContext.runId,
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptAgentClosing(this) intercept@{ eventContext ->
                val event = AgentClosingEvent(
                    agentId = eventContext.agentId,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarting(this) intercept@{ eventContext ->

                val strategy = eventContext.strategy as AIAgentGraphStrategy

                @OptIn(InternalAgentsApi::class)
                val event = GraphStrategyStartingEvent(
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name,
                    graph = strategy.startNodeToGraph(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptStrategyCompleted(this) intercept@{ eventContext ->
                val event = StrategyCompletedEvent(
                    runId = eventContext.runId,
                    strategyName = eventContext.strategy.name,
                    result = eventContext.result?.toString(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptNodeExecutionStarting(this) intercept@{ eventContext ->
                val event = NodeExecutionStartingEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = getNodeData(eventContext.input, eventContext.inputType),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptNodeExecutionCompleted(this) intercept@{ eventContext ->

                val event = NodeExecutionCompletedEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = getNodeData(eventContext.input, eventContext.inputType),
                    output = getNodeData(eventContext.output, eventContext.outputType),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptNodeExecutionFailed(this) intercept@{ eventContext ->
                val event = NodeExecutionFailedEvent(
                    runId = eventContext.context.runId,
                    nodeName = eventContext.node.name,
                    input = getNodeData(eventContext.input, eventContext.inputType),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptLLMCallStarting(this) intercept@{ eventContext ->
                val event = LLMCallStartingEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo(),
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptLLMCallCompleted(this) intercept@{ eventContext ->
                val event = LLMCallCompletedEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo(),
                    responses = eventContext.responses,
                    moderationResponse = eventContext.moderationResponse,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept LLM Streaming Events

            pipeline.interceptLLMStreamingStarting(this) intercept@{ eventContext ->
                val event = LLMStreamingStartingEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo().modelIdentifierName,
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptLLMStreamingFrameReceived(this) intercept@{ eventContext ->
                val event = LLMStreamingFrameReceivedEvent(
                    runId = eventContext.runId,
                    frame = eventContext.streamFrame,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptLLMStreamingFailed(this) intercept@{ eventContext ->
                val event = LLMStreamingFailedEvent(
                    runId = eventContext.runId,
                    error = eventContext.error.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptLLMStreamingCompleted(this) intercept@{ eventContext ->
                val event = LLMStreamingCompletedEvent(
                    runId = eventContext.runId,
                    prompt = eventContext.prompt,
                    model = eventContext.model.toModelInfo().modelIdentifierName,
                    tools = eventContext.tools.map { it.name },
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            //endregion Intercept LLM Streaming Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCallStarting(this) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolCallStartingEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptToolValidationFailed(this) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolValidationFailedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = eventContext.tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    error = eventContext.error,
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptToolCallFailed(this) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolCallFailedEvent(
                    runId = eventContext.runId,
                    toolCallId = eventContext.toolCallId,
                    toolName = tool.name,
                    toolArgs = tool.encodeArgs(eventContext.toolArgs),
                    error = eventContext.throwable.toAgentError(),
                    timestamp = pipeline.clock.now().toEpochMilliseconds()
                )
                writer.onMessage(event)
            }

            pipeline.interceptToolCallCompleted(this) intercept@{ eventContext ->
                @Suppress("UNCHECKED_CAST")
                val tool = eventContext.tool as Tool<Any?, Any?>

                val event = ToolCallCompletedEvent(
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

            return debugger
        }

        //region Private Methods

        private fun readPortValue(): Int? {
            val debuggerPortValue =
                readEnvironmentVariable(name = KOOG_DEBUGGER_PORT_ENV_VAR)
                    ?: readVMOption(name = KOOG_DEBUGGER_PORT_VM_OPTION)

            logger.debug { "Debugger Feature. Reading Koog debugger port value from system variables: $debuggerPortValue" }
            return debuggerPortValue?.toIntOrNull()
        }

        private fun readWaitConnectionTimeout(): Duration? {
            val debuggerWaitConnectionTimeoutValue =
                readEnvironmentVariable(name = KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR)
                    ?: readVMOption(name = KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION)

            logger.debug { "Debugger Feature. Reading Koog debugger wait connection timeout value from system variables: $debuggerWaitConnectionTimeoutValue" }
            return debuggerWaitConnectionTimeoutValue?.toLongOrNull()?.toDuration(DurationUnit.MILLISECONDS)
        }

        /**
         * Retrieves the JSON representation of the given data based on its type.
         *
         * Note: See [KG-485](https://youtrack.jetbrains.com/issue/KG-485)
         *       Workaround for processing non-serializable [ReceivedToolResult] type in the node input/output.
         */
        private fun getNodeData(data: Any?, dataType: KType): JsonElement? {
            data ?: return null

            return when (data) {
                is ReceivedToolResult -> {
                    runCatching { Json.parseToJsonElement(data.content) }.getOrNull()
                        ?: JsonPrimitive(data.content)
                }
                else -> {
                    @OptIn(InternalAgentsApi::class)
                    SerializationUtil.trySerializeDataToJsonElement(data, dataType)
                }
            }
        }

        //endregion Private Methods
    }
}
