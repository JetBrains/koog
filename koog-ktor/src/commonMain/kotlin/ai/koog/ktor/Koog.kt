package ai.koog.ktor

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.ktor.utils.loadAgentsConfig
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.SuitableForIO
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.application
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * Represents an instance of Koog with configuration for prompt execution, language model,
 * tool management, agent setup, and features.
 *
 * @property promptExecutor The executor responsible for handling language model prompts and interaction.
 * @property agentConfig The configuration settings for the AI agent.
 * @property agentFeatures A list of features enabled for the agent.
 */
public class Koog(
    public val application: Application,
    public val promptExecutor: PromptExecutor,
    public val agentConfig: KoogAgentsConfig.AgentConfig,
    public val agentFeatures: List<GraphAIAgent.FeatureContext.() -> Unit>,
    private val job: CompletableJob
) {

    internal suspend fun agentConfig(model: LLModel): AIAgentConfig {
        job.join()
        return AIAgentConfig(
            agentConfig.prompt,
            model,
            agentConfig.maxAgentIterations,
            agentConfig.missingToolsConversionStrategy
        )
    }

    /**
     * A scoped plugin named "KoogAgents" for managing the Koog instance lifecycle in the application context.
     *
     * The plugin initializes the necessary components such as the `MultiLLMPromptExecutor` and `KoogInstance`
     * using configuration parameters provided via `pluginConfig`. The `KoogInstance` carries the core functionality
     * for language model communication, agent tools, configurations, and features.
     *
     * The initialized `KoogInstance` is then stored in the application's attributes to be accessible across the application.
     */
    public companion object Companion : Plugin<ApplicationCallPipeline, KoogAgentsConfig, Koog> {
        override fun install(pipeline: ApplicationCallPipeline, configure: KoogAgentsConfig.() -> Unit): Koog {
            val application = when (pipeline) {
                is RoutingNode -> pipeline.application
                is Application -> pipeline
                else -> error("Unsupported pipeline type: ${pipeline::class}")
            }

            val job = Job(application.coroutineContext[Job])
            val scope = CoroutineScope(Dispatchers.SuitableForIO + job)

            val config = pipeline.environment.loadAgentsConfig(scope).apply(configure)

            job.complete()

            val executor =
                MultiLLMPromptExecutor(llmClients = config.llmConnections, fallback = config.fallbackLLMSettings)

            return Koog(
                application,
                executor,
                config.agentConfig,
                config.agentFeatures,
                job
            )
        }

        /**
         * Attribute key used to store and retrieve the `KoogInstance` from the application's attributes.
         *
         * The `KoogInstance` holds a reference to the `PromptExecutor`, the default language model (`LLModel`),
         * and other necessary configurations and tools required for executing prompts and performing AI-driven operations.
         *
         * This key is utilized within the application to access the `KoogInstance` for tasks such as processing
         * language model queries, moderating content, and employing available AI tools in a routing context.
         */
        override val key: AttributeKey<Koog> = AttributeKey("KoogAgents")
    }
}
