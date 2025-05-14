package ai.grazie.code.agents.features.tracing.feature

import ai.grazie.code.agents.core.agent.entity.AIAgentNodeBase
import ai.grazie.code.agents.core.agent.entity.AIAgentStorageKey
import ai.grazie.code.agents.core.agent.entity.stage.AIAgentStageContextBase
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.AIAgentFeatureBase
import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.features.common.message.FeatureMessage
import ai.grazie.code.agents.features.common.message.FeatureMessageProcessorUtil.onMessageForEachSafe
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import ai.jetbrains.code.prompt.message.Message

/**
 * Feature that collects comprehensive tracing data during agent execution and sends it to configured feature message processors.
 * 
 * Tracing is crucial for evaluation and analysis of the working agent, as it captures detailed information about:
 * - All LLM calls and their responses
 * - Prompts sent to LLMs
 * - Tool calls, arguments, and results
 * - Graph node visits and execution flow
 * - Agent lifecycle events (creation, start, finish, errors)
 * - Strategy execution events
 * 
 * This data can be used for debugging, performance analysis, auditing, and improving agent behavior.
 * 
 * Example of installing tracing to an agent:
 * ```kotlin
 * val agent = AIAgentBase(
 *     promptExecutor = executor,
 *     strategy = strategy,
 *     // other parameters...
 * ) {
 *     install(Tracing) {
 *         // Configure message processors to handle trace events
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *         addMessageProcessor(TraceFeatureMessageFileWriter(fileSystem, outputPath))
 *         
 *         // Optionally filter messages
 *         messageFilter = { message -> 
 *             // Only trace LLM calls and tool calls
 *             message is LLMCallStartEvent || message is ToolCallEvent 
 *         }
 *     }
 * }
 * ```
 * 
 * Example of logs produced by tracing:
 * ```
 * AgentCreateEvent (strategy name: my-agent-strategy)
 * AgentStartedEvent (strategy name: my-agent-strategy)
 * StrategyStartEvent (strategy name: my-agent-strategy)
 * NodeExecutionStartEvent (stage: main, node: definePrompt, input: user query)
 * NodeExecutionEndEvent (stage: main, node: definePrompt, input: user query, output: processed query)
 * LLMCallStartEvent (prompt: Please analyze the following code...)
 * LLMCallEndEvent (response: I've analyzed the code and found...)
 * ToolCallEvent (stage: main, tool: readFile, tool args: {"path": "src/main.py"})
 * ToolCallResultEvent (stage: main, tool: readFile, tool args: {"path": "src/main.py"}, result: "def main():...")
 * StrategyFinishedEvent (strategy name: my-agent-strategy, result: Success)
 * AgentFinishedEvent (strategy name: my-agent-strategy, result: Success)
 * ```
 */
class Tracing {

    /**
     * Feature implementation for the Tracing functionality.
     * 
     * This companion object implements [AIAgentFeatureBase] and provides methods for creating
     * an initial configuration and installing the tracing feature in an agent pipeline.
     * 
     * To use tracing in your agent, install it during agent creation:
     * 
     * ```kotlin
     * val agent = AIAgentBase(...) {
     *     install(Tracing) {
     *         // Configure tracing here
     *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
     *     }
     * }
     * ```
     */
    companion object Feature : AIAgentFeatureBase<TraceFeatureConfig, Tracing> {

        private val logger: MPPLogger =
            LoggerFactory.create("ai.grazie.code.agents.features.tracing.feature.TracingFeature")

        override val key: AIAgentStorageKey<Tracing> =
            AIAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig() = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${Tracing::class.simpleName}" }

            if (config.messageProcessor.isEmpty()) {
                logger.warning { "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target." }
            }

            val featureImpl = Tracing()

            //region Intercept Agent Events

            pipeline.interceptAgentCreated(this, featureImpl) intercept@{
                val event = AIAgentCreateEvent(
                    strategyName = strategy.name,
                )
                readStages { _ ->
                    processMessage(config, event)
                }
            }

            pipeline.interceptAgentStarted(this, featureImpl) intercept@{ strategyName ->
                val event = AIAgentStartedEvent(
                    strategyName = strategyName,
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentFinished(this, featureImpl) intercept@{ strategyName, result ->
                val event = AIAgentFinishedEvent(
                    strategyName = strategyName,
                    result = result,
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentRunError(this, featureImpl) intercept@{ strategyName, throwable ->
                val event = AIAgentRunErrorEvent(
                    strategyName = strategyName,
                    error = throwable.toAgentError(),
                )
                processMessage(config, event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(this, featureImpl) intercept@{
                val event = AIAgentStrategyStartEvent(
                    strategyName = strategy.name,
                )
                readStages { _ -> processMessage(config, event) }
            }

            pipeline.interceptStrategyFinished(this, featureImpl) intercept@{ strategyName, result ->
                val event = AIAgentStrategyFinishedEvent(
                    strategyName = strategyName,
                    result = result,
                )
                processMessage(config, event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(this, featureImpl) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any? ->
                val event = AIAgentNodeExecutionStartEvent(
                    nodeName = node.name,
                    stageName = context.stageName,
                    input = input?.toString() ?: ""
                )
                processMessage(config, event)
            }

            pipeline.interceptAfterNode(this, featureImpl) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentStageContextBase, input: Any?, output: Any? ->
                val event = AIAgentNodeExecutionEndEvent(
                    nodeName = node.name,
                    stageName = context.stageName,
                    input = input?.toString() ?: "",
                    output = output?.toString() ?: ""
                )
                processMessage(config, event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(this, featureImpl) intercept@{ prompt ->
                val event = LLMCallStartEvent(
                    prompt = prompt.messages.firstOrNull { it.role == Message.Role.User }?.content ?: ""
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptBeforeLLMCallWithTools(this, featureImpl) intercept@{ prompt, tools ->
                val event = LLMCallWithToolsStartEvent(
                    prompt = prompt.messages.firstOrNull { it.role == Message.Role.User }?.content ?: "",
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCall(this, featureImpl) intercept@{ response ->
                val event = LLMCallEndEvent(
                    response = response
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCallWithTools(this, featureImpl) intercept@{ responses, tools ->
                val event = LLMCallWithToolsEndEvent(
                    responses = responses.map { it.content },
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(this, featureImpl) intercept@{ stage, tool, toolArgs ->
                val event = ToolCallEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs
                )
                processMessage(config, event)
            }

            pipeline.interceptToolValidationError(this, featureImpl) intercept@{ stage, tool, toolArgs, value ->
                val event = ToolValidationErrorEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    errorMessage = value
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallFailure(this, featureImpl) intercept@{ stage, tool, toolArgs, throwable ->
                val event = ToolCallFailureEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    error = throwable.toAgentError()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallResult(this, featureImpl) intercept@{ stage, tool, toolArgs, result ->
                val event = ToolCallResultEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    result = result
                )
                processMessage(config, event)
            }

            //endregion Intercept Tool Call Events
        }

        //region Private Methods

        private suspend fun processMessage(config: TraceFeatureConfig, message: FeatureMessage) {
            if (!config.messageFilter(message)) {
                return
            }

            config.messageProcessor.onMessageForEachSafe(message)
        }

        //endregion Private Methods
    }
}
