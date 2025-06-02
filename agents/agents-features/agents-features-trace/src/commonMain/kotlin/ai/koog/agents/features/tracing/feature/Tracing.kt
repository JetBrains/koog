package ai.koog.agents.features.tracing.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessorUtil.onMessageForEachSafe
import io.github.oshai.kotlinlogging.KotlinLogging

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
 * val agent = AIAgent(
 *     promptExecutor = executor,
 *     strategy = strategy,
 *     // other parameters...
 * ) {
 *     install(Tracing) {
 *         // Configure message processors to handle trace events
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *         addMessageProcessor(TraceFeatureMessageFileWriter(outputFile, fileSystem::sink))
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
public class Tracing {

    /**
     * Feature implementation for the Tracing functionality.
     * 
     * This companion object implements [AIAgentFeature] and provides methods for creating
     * an initial configuration and installing the tracing feature in an agent pipeline.
     * 
     * To use tracing in your agent, install it during agent creation:
     * 
     * ```kotlin
     * val agent = AIAgent(...) {
     *     install(Tracing) {
     *         // Configure tracing here
     *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
     *     }
     * }
     * ```
     */
    public companion object Feature : AIAgentFeature<TraceFeatureConfig, Tracing> {

        private val logger = KotlinLogging.logger {  }

        override val key: AIAgentStorageKey<Tracing> =
            AIAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig(): TraceFeatureConfig = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${Tracing::class.simpleName}" }

            if (config.messageProcessor.isEmpty()) {
                logger.warn { "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target." }
            }

            val featureImpl = Tracing()

            //region Intercept Agent Events

            pipeline.interceptBeforeAgentStarted(this, featureImpl) intercept@{
                val event = AIAgentStartedEvent(
                    strategyName = strategy.name,
                )
                readStrategy { stages ->
                    processMessage(config, event)
                }
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
                readStrategy { stages ->
                    processMessage(config, event)
                }
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

            pipeline.interceptBeforeNode(this, featureImpl) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
                val event = AIAgentNodeExecutionStartEvent(
                    nodeName = node.name,
                    input = input?.toString() ?: ""
                )
                processMessage(config, event)
            }

            pipeline.interceptAfterNode(this, featureImpl) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
                val event = AIAgentNodeExecutionEndEvent(
                    nodeName = node.name,
                    input = input?.toString() ?: "",
                    output = output?.toString() ?: ""
                )
                processMessage(config, event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(this, featureImpl) intercept@{ prompt, tools ->
                val event = LLMCallStartEvent(
                    prompt = prompt,
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCall(this, featureImpl) intercept@{ responses ->
                val event = LLMCallEndEvent(
                    responses = responses
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(this, featureImpl) intercept@{ tool, toolArgs ->
                val event = ToolCallEvent(
                    toolName = tool.name,
                    toolArgs = toolArgs
                )
                processMessage(config, event)
            }

            pipeline.interceptToolValidationError(this, featureImpl) intercept@{ tool, toolArgs, value ->
                val event = ToolValidationErrorEvent(
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    errorMessage = value
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallFailure(this, featureImpl) intercept@{ tool, toolArgs, throwable ->
                val event = ToolCallFailureEvent(
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    error = throwable.toAgentError()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallResult(this, featureImpl) intercept@{ tool, toolArgs, result ->
                val event = ToolCallResultEvent(
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
