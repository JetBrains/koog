package ai.koog.agents.example.features

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.handler.BeforeNodeHandler
import ai.koog.agents.example.TokenService
import ai.koog.agents.ext.agent.simpleSingleRunAgent
import ai.koog.agents.features.common.config.FeatureConfig
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
    companion object Feature : AIAgentFeature<Config, Logging> {
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
            pipeline: AIAgentPipeline
        ) {
            val logging = Logging(LoggerFactory.getLogger(config.loggerName))

            pipeline.interceptBeforeAgentStarted(this, logging) {
                logging.logger.info("Agent is going to be started with strategy: ${strategy.name}.")
            }

            pipeline.interceptStrategyStarted(this, logging) {
                logging.logger.info("Strategy ${strategy.name} started")
            }

            pipeline.interceptBeforeNode(this, logging) { node, context, input ->
                logger.info("Node ${node.name} received input: $input")
            }

            pipeline.interceptAfterNode(this, logging) { node, context, input, output ->
                logger.info("Node ${node.name} with input: $input produced output: $output")
            }

            pipeline.interceptBeforeLLMCall(this, logging) { prompt ->
                logger.info("Before LLM call with prompt: $prompt")
            }

            pipeline.interceptAfterLLMCall(this, logging) { response ->
                logger.info("After LLM call with response: $response")
            }

            pipeline.interceptBeforeLLMCallWithTools(this, logging) { prompt, tools ->
                logger.info("Before LLM call with tools. Prompt: $prompt, Tools: ${tools.map { it.name }}")
            }

            pipeline.interceptAfterLLMCallWithTools(this, logging) { response, tools ->
                logger.info("After LLM call with tools. Response: $response, Tools: ${tools.map { it.name }}")
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
    val agent = simpleSingleRunAgent(
        executor = simpleOpenAIExecutor(TokenService.openAIToken),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
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
