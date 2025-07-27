package ai.koog.ktor

import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.ktor.utils.loadAgentsConfig
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.util.AttributeKey

/**
 * Represents an instance of Koog with configuration for prompt execution, language model,
 * tool management, agent setup, and features.
 *
 * @property promptExecutor The executor responsible for handling language model prompts and interaction.
 * @property defaultLLM The default language model to be used if no specific model is provided.
 * @property tools The registry containing available tools for agent operations.
 * @property agentConfig The configuration settings for the AI agent.
 * @property agentFeatures A list of features enabled for the agent.
 */
public class Koog(
    public val pipeline: ApplicationCallPipeline,
    public val promptExecutor: PromptExecutor,
    public val defaultLLM: LLModel?,
    public val tools: ToolRegistry,
    public val agentConfig: AIAgentConfig,
    public val agentFeatures: List<FeatureContext.() -> Unit>
) {
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
            val config = try {
                pipeline.environment.loadAgentsConfig()
            } catch (e: Exception) {
                pipeline.environment.log.error("Failed to read Koog configuration from application config", e)
                KoogAgentsConfig()
            }.apply(configure)

            val executor =
                MultiLLMPromptExecutor(llmClients = config.llmConnections, fallback = config.fallbackLLMSettings)

            return Koog(
                pipeline,
                executor,
                config.defaultLLM,
                config.agentTools,
                requireNotNull(config.agentConfig) { "agentConfig is not set" },
                config.agentFeatures
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
