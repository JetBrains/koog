package ai.koog.agents.example.features.logging

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.BeforeNodeHandler
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * An example of a feature that provides logging capabilities for the agent to trace a particular event
 * during the agent run.
 *
 * @property logger The logger instance used to perform logging operations.
 */
class Logging(val logger: Logger) {

    class Config : FeatureConfig() {
        var loggerName: String = "agent-logs"
    }

    /**
     * A Logging Feature implementation.
     *
     * This feature supports configuration via the [Config] class,
     * which allows specifying custom logger names.
     */
    companion object Feature : AIAgentGraphFeature<Config, Logging> {
        override val key: AIAgentStorageKey<Logging> = createStorageKey("logging-feature")

        override fun createInitialConfig(): Config = Config()

        /**
         * Installs the Logging Feature into the provided pipeline.
         *
         * The method integrates the feature capabilities into the agent pipeline by setting up interceptors
         * to log information during agent creation, before processing nodes, and after processing nodes by a predefined
         * hooks, e.g. [BeforeNodeHandler], etc.
         *
         * @param config The configuration for the LoggingFeature, providing logger details.
         * @param pipeline The agent pipeline where logging functionality will be installed.
         */
        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline
        ) {
            val logging = Logging(LoggerFactory.getLogger(config.loggerName))
            val interceptContext = InterceptContext(this, logging)
            pipeline.interceptBeforeAgentStarted(interceptContext) { eventContext ->
                logging.logger.info("Agent is going to be started with strategy: ${eventContext.strategy.name}.")
            }

            pipeline.interceptStrategyStarted(interceptContext) { eventContext ->
                logging.logger.info("Strategy ${eventContext.strategy.name} started")
            }

            pipeline.interceptBeforeNode(interceptContext) { eventContext ->
                logger.info("Node ${eventContext.node.name} received input: ${eventContext.input}")
            }

            pipeline.interceptAfterNode(interceptContext) { eventContext ->
                logger.info(
                    "Node ${eventContext.node.name} with input: ${eventContext.input} produced output: ${eventContext.output}"
                )
            }

            pipeline.interceptBeforeLLMCall(interceptContext) { eventContext ->
                logger.info(
                    "Before LLM call with prompt: ${eventContext.prompt}, tools: [${eventContext.tools.joinToString {
                        it.name
                    }}]"
                )
            }

            pipeline.interceptAfterLLMCall(interceptContext) { eventContext ->
                logger.info("After LLM call with response: ${eventContext.responses}")
            }
        }
    }
}

/**
 * Examples of installing a feature interceptors on the earlier stage before agent is created
 * to catch agent creation events.
 */
@Suppress("unused")
fun installLogging(coroutineScope: CoroutineScope, logName: String = "agent-logs") {
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        llmModel = OpenAIModels.CostOptimized.GPT4oMini,
        systemPrompt = "You are a code assistant. Provide concise code examples."
    ) {
        install(Logging) {
            loggerName = logName
        }
    }

    coroutineScope.launch {
        agent.run("5 plus 2")
    }
}
