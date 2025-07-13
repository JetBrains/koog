package ai.koog.ktor

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.ktor.utils.config.getModelFromIdentifier
import ai.koog.ktor.utils.config.loadEnvironmentConfig
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings.Companion.DEFAULT_ANTHROPIC_API_VERSION
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings.Companion.DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

/**
 * Represents the default prompt configuration for the agent.
 *
 * This property initializes a `Prompt` instance with the ID "agent" and includes a system
 * message that provides context to the language model by describing its role as a helpful assistant.
 *
 * This default configuration can be used to standardize the behavior of the language model
 * and offer consistent instructions when interacting with the agent.
 */
internal val DEFAULT_PROMPT = prompt("agent") {
    system("You are a helpful assistant")
}

/**
 * Configuration class for setting up a Koog agents server.
 * Provides options to configure LLM connections, agent tools, features, and other related settings.
 */
public class KoogAgentsConfig {
    /**
     * A mutable map that associates `LLMProvider` instances with their corresponding `LLMClient` implementations.
     *
     * This map is used to store and manage connections to various Large Language Model (LLM) providers,
     * enabling interactions with specific LLM services through their associated client interfaces.
     *
     * Keys:
     * - `LLMProvider`: Represents the provider of the large language model (e.g., OpenAI, Google, Anthropic, etc.).
     *
     * Values:
     * - `LLMClient`: Represents the client responsible for communicating with the respective LLM provider.
     *
     * The map is intended to facilitate dynamic management of LLM connections within the system by adding,
     * retrieving, or removing `LLMClient` instances corresponding to each registered `LLMProvider`.
     */
    internal val llmConnections: MutableMap<LLMProvider, LLMClient> = mutableMapOf()

    /**
     * Represents the configuration settings for the fallback prompt executor in a multi-LLM environment.
     *
     * This variable is used to define the behaviors and parameters for fallback logic when no primary
     * LLM connection is successful or applicable for processing a request. It is an optional configuration
     * and may be null if no fallback mechanism is set up.
     *
     * The fallback settings are encapsulated in the `FallbackPromptExecutorSettings` class within
     * the `MultiLLMPromptExecutor`.
     *
     * It is internally mutable and primarily used within the `KoogAgentsServerConfig` class.
     */
    internal var fallbackLLMSettings: MultiLLMPromptExecutor.FallbackPromptExecutorSettings? = null

    /**
     * Represents the default Large Language Model (LLM) to be used in the configuration if no specific model is provided.
     *
     * This variable holds an optional instance of `LLModel`, which defines the LLM provider, model identifier,
     * and supported capabilities. If not explicitly set, the absence of a default value will result in an error
     * when a model is required but not specified.
     *
     * The `defaultLLM` acts as a fallback mechanism within the system, ensuring that an appropriate LLM instance
     * is available when not overridden by specific configurations. It plays a critical role in defining the system's
     * behavior regarding language model selection.
     */
    public var defaultLLM: LLModel? = null

    /**
     * The registry used to manage and provide a collection of tools for agents.
     *
     * The `agentTools` variable holds a `ToolRegistry`, which serves as a central repository
     * for all tools available to an agent. By default, it is initialized to an empty registry
     * (`ToolRegistry.EMPTY`), but it can be populated and updated as part of the agent configuration process.
     *
     * The available tools within this registry are utilized by agents during their operations to perform
     * various actions and tasks. The tools are configured through the agent setup process and are accessible
     * via this registry.
     *
     * Note: This property is internal and intended for use within the configuration and execution context
     * of agents in the `KoogAgentsServerConfig` class.
     */
    internal var agentTools: ToolRegistry = ToolRegistry.EMPTY

    /**
     * Represents the configuration of an AI agent within the server.
     *
     * This variable holds an instance of `AIAgentConfig` that defines the specific settings,
     * prompt, and behavior for the AI agent. It is initialized when the `agent` function is called
     * with the appropriate configuration.
     *
     * The configuration encapsulated by this variable is used to determine the agent's execution
     * parameters, including the prompt, model, and strategies for handling complex operations.
     *
     * If no configuration is provided, the value remains null.
     */
    internal var agentConfig: AIAgentConfig? = null

    /**
     * A mutable list that stores instances of `AgentFeature` for configuring and customizing
     * agent-specific functionalities within the system.
     *
     * This list serves as a centralized registry to manage features that can be installed
     * and utilized by agents, enabling the extension of agent capabilities via the installation
     * of specific `AgentFeature` implementations.
     */
    internal val agentFeatures: MutableList<AgentFeature> = mutableListOf()

    /**
     * Configuration class for defining timeout durations in network requests.
     * Used to set and customize the request, connection, and socket timeouts.
     */
    public class TimeoutConfiguration {
        /**
         * Specifies the maximum duration, in milliseconds, allowed for a network request to complete
         * before timing out. It acts as a safeguard to prevent indefinite waiting for responses.
         *
         * The default value is 900000 milliseconds (15 minutes).
         *
         * This is particularly useful for configuring timeout behavior in network-related
         * operations across various integrations.
         */
        public var requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MS

        /**
         * Specifies the connection timeout duration in milliseconds for establishing a network connection.
         * This value determines the maximum time allowed for a connection to be established before the
         * operation is terminated.
         *
         * Default value is 60,000 milliseconds (60 seconds).
         */
        public var connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS

        /**
         * Specifies the maximum amount of time, in milliseconds, to wait for data over an established
         * socket connection before timing out.
         *
         * This property is used to configure the timeout duration for socket operations, such as reading
         * or writing data through the network connection. Setting this value appropriately ensures that
         * the application does not hang indefinitely when waiting for data to be transmitted or received
         * over the socket.
         *
         * The default value is defined by `DEFAULT_TIMEOUT_MS`, which represents 900 seconds.
         */
        public var socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MS

        /**
         * Companion object holding default timeout constants for the TimeoutConfiguration class.
         */
        private companion object {
            /**
             * Default timeout duration in milliseconds.
             * This value is used for configuring various timeout settings,
             * such as request or socket timeouts, in cases where no custom value is specified.
             * The default duration is set to 900,000 milliseconds, equivalent to 900 seconds or 15 minutes.
             */
            private const val DEFAULT_TIMEOUT_MS: Long = 900000 // 900 seconds

            /**
             * Default timeout value in milliseconds for establishing a connection.
             * This value represents the duration to wait before timing out a connection attempt.
             */
            private const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 60_000
        }
    }

    /**
     * Configuration class for managing various Language Learning Model (LLM) providers and their settings.
     * This class allows integration with different LLM services such as OpenAI, Anthropic, Google, OpenRouter, and Ollama.
     * Users can also define fallback configurations and custom LLM clients.
     */
    public inner class LLMConfig {
        /**
         * Configures the OpenAI integration for the system using the provided API key and optional configuration.
         *
         * @param apiKey The API key used to authenticate with the OpenAI service.
         * @param configure A lambda function to customize the OpenAI configuration, such as setting base URLs, paths,
         * or timeout settings. Defaults to an empty lambda.
         */
        public fun openAI(apiKey: String, configure: OpenAIConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.openAI(apiKey, configure)
        }

        /**
         * Configures the Anthropic API client with the specified API key and additional settings.
         *
         * @param apiKey The API key used for authenticating with the Anthropic API.
         * @param configure A lambda function for further configuring the Anthropic client. Default is an empty configuration.
         */
        public fun anthropic(apiKey: String, configure: AnthropicConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.anthropic(apiKey, configure)
        }

        /**
         * Configures the Google client for the application.
         *
         * @param apiKey The API key to authenticate with Google services.
         * @param configure A lambda to customize the `GoogleConfig` settings such as base URL, timeouts, or HTTP client configuration.
         */
        public fun google(apiKey: String, configure: GoogleConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.google(apiKey, configure)
        }

        /**
         * Configures and initializes the OpenRouter API with the provided API key and optional configuration.
         *
         * @param apiKey The API key used to authenticate with the OpenRouter API.
         * @param configure An optional lambda function used to customize the OpenRouter configuration.
         */
        public fun openRouter(apiKey: String, configure: OpenRouterConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.openRouter(apiKey, configure)
        }

        /**
         * Configures the Ollama integration using the provided configuration block.
         * This method allows customization of settings for communicating with the Ollama service.
         *
         * @param configure A lambda providing a configuration block for customizing the behavior of Ollama integration.
         */
        public fun ollama(configure: OllamaConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.ollama(configure)
        }

        /**
         * Adds a custom Large Language Model (LLM) client to the configuration.
         *
         * This method allows you to register a specific implementation of an LLM client
         * for a given provider, enabling seamless integration with the desired LLM service.
         *
         * @param provider The LLM provider associated with the client being added.
         * @param client The LLM client implementation to be registered for the specified provider.
         */
        public fun addClient(provider: LLMProvider, client: LLMClient) {
            this@KoogAgentsConfig.addLLMClient(provider, client)
        }

        /**
         * Configuration class for defining a fallback Large Language Model (LLM) setup.
         *
         * This class allows specifying a provider and model to be used as a fallback
         * in cases where the primary LLM configuration is unavailable or fails.
         *
         * The fallback configuration requires explicitly setting both the provider and model.
         */
        public inner class FallbackLLMConfig {
            /**
             * Defines the provider for the fallback Large Language Model (LLM) to be used when the primary
             * configuration or execution fails. The provider specifies which LLM platform will be utilized,
             * such as OpenAI, Google, or others, derived from the `LLMProvider` hierarchy.
             *
             * This property should be set to a specific `LLMProvider` implementation before configuring a fallback.
             * Failure to specify a provider will result in an error when attempting to use the fallback configuration.
             */
            public var provider: LLMProvider? = null

            /**
             * Represents the fallback Large Language Model (LLM) to be used in cases where the primary configuration is unavailable
             * or a specific fallback is required.
             *
             * This property defines a large language model with its associated provider, unique identifier, and capabilities.
             * It must be explicitly set when configuring a fallback LLM to ensure proper functionality.
             */
            public var model: LLModel? = null
        }

        /**
         * Configures fallback settings for the LLM (Large Language Model) configuration.
         * This is used to define a fallback provider and model in case primary configurations are unavailable.
         *
         * @param configure A lambda function to configure the fallback provider and model using the
         * FallbackLLMConfig instance.
         * The provider and model must be specified within this configuration.
         */
        public fun fallback(configure: FallbackLLMConfig.() -> Unit) {
            with(FallbackLLMConfig()) {
                configure()
                fallbackLLMSettings = MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
                    provider ?: error("Fallback provider must be specified"),
                    model ?: error("Fallback model must be specified")
                )
            }
        }
    }

    /**
     * Configures the properties and behavior of the large language model (LLM) within the server environment.
     * Allows setting and customizing various aspects of LLM providers, fallbacks, and related configurations.
     *
     * @param configure A configuration block where the properties and behaviors of the LLM can be specified
     *                  using the [LLMConfig] receiver.
     */
    public fun llm(configure: LLMConfig.() -> Unit) {
        LLMConfig().configure()
    }


    /**
     * Configuration class for managing agent-specific settings and tools.
     *
     * The `AgentConfig` class allows customization of the agent's behavior, including:
     * - Setting the prompt and language model to use
     * - Managing tools available to the agent
     * - Defining strategies for handling missing tools
     * - Installing additional features
     */
    public inner class AgentConfig {
        /**
         * Represents the registry of tools available to an agent within the AgentConfig context.
         *
         * This variable holds the tools configured and registered via the AgentConfig or
         * higher-level configurations. It is initialized to an empty registry but can
         * be modified dynamically through methods like `registerTools`. The registry
         * manages tools that the agent may utilize during its execution.
         *
         * In the context of the containing `AgentConfig` and `KoogAgentsServerConfig`,
         * this property serves as the central repository for maintaining agent-specific tools.
         *
         * @see ToolRegistry
         * @see AgentConfig.registerTools
         */
        internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

        /**
         * Represents the prompt configuration for the Agent.
         * This variable defines the initial prompt structure used by the Agent for generating responses or actions.
         */
        public var prompt: Prompt = DEFAULT_PROMPT

        /**
         * Specifies the Large Language Model (LLM) to be used by the agent.
         *
         * This property determines which LLM instance the agent will interact with.
         * If not explicitly set, a default LLM may be used, depending on the system configuration.
         *
         * Nullable to allow flexibility in situations where a model might not be immediately specified.
         */
        public var model: LLModel? = null

        /**
         * Specifies the maximum number of iterations an agent is permitted to perform during its execution cycle.
         *
         * Adjusting this value controls how many steps an agent can take while attempting
         * to fulfill a given task or respond to a prompt. This acts as a constraint to prevent
         * unbounded loops or excessive resource usage in scenarios where the agent's computations
         * or decision-making processes continue indefinitely.
         *
         * Default value is 50.
         */
        public var maxAgentIterations: Int = 50

        /**
         * Defines the strategy for handling tool calls present in the prompt that do not have corresponding tool definitions
         * registered in the current context. This is used to convert missing tool information into a format suitable for
         * processing by the model.
         *
         * By default, this variable is set to `MissingToolsConversionStrategy.Missing`, which replaces only the missing
         * tool calls with a descriptive format using the `ToolCallDescriber.JSON` implementation. This ensures that
         * missing tool calls are represented as plaintext messages in the prompt while leaving other tool-related data intact.
         *
         * This property can be customized to use other strategies, such as replacing all tool calls irrespective of their
         * presence in the registry or using custom formatting strategies defined by other implementations of the
         * `ToolCallDescriber`.
         */
        public var missingToolsConversionStrategy: MissingToolsConversionStrategy =
            MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

        /**
         * Registers tools into the tool registry using the provided configuration lambda.
         *
         * This method allows adding custom tools to the registry by defining them through the
         * `ToolRegistry.Builder`. The tools are applied to the internal `toolRegistry` of the
         * `AgentConfig` class instance by merging existing tools with the newly registered tools.
         *
         * @param bindTools A lambda function for configuring the tool registry using the `ToolRegistry.Builder`.
         */
        public fun registerTools(bindTools: ToolRegistry.Builder.() -> Unit) {
            toolRegistry += ToolRegistry {
                bindTools()
            }
        }

        /**
         * Configures and sets the prompt for the agent using the provided parameters and a prompt-building function.
         *
         * @param llmParams The parameters that define the behavior of the language model, such as temperature
         * and tool selection. Defaults to an instance of `LLMParams`.
         * @param buildPrompt A lambda function that is used to construct the prompt using a `PromptBuilder`.
         */
        public fun prompt(llmParams: LLMParams = LLMParams(), buildPrompt: PromptBuilder.() -> Unit) {
            prompt = prompt("agent", llmParams, build = buildPrompt)
        }

        /**
         * Adds an AI agent feature to the current configuration by applying the specific configuration logic.
         *
         * @param TConfig The type of feature configuration that extends [FeatureConfig].
         * @param feature The AI agent feature to be added, which provides functionality and configuration capabilities.
         * @param configure A lambda function to configure the feature. The default is an empty configuration.
         */
        public fun <TConfig : FeatureConfig> install(
            feature: AIAgentFeature<TConfig, *>,
            configure: TConfig.() -> Unit = {}
        ) {
            this@KoogAgentsConfig.agentFeatures += {
                install(feature, configure)
            }
        }
    }

    /**
     * Configures and initializes an AI agent with the specified settings.
     *
     * This method allows the customization of an AI agent by providing a suspendable configuration block
     * that modifies the agent's prompt, model, tool registry, and other parameters. The resulting agent
     * setup is created based on the defined configuration.
     *
     * @param configure A suspendable lambda function that defines the configuration of the agent.
     * The configuration block operates on an instance of [AgentConfig], where properties such as
     * `prompt`, `model`, `maxAgentIterations`, and tools can be customized.
     */
    public fun agent(configure: AgentConfig.() -> Unit) {
        with(AgentConfig()) {
            configure()

            agentTools = toolRegistry
            agentConfig = AIAgentConfig(
                prompt = prompt,
                model = model ?: defaultLLM ?: throw IllegalArgumentException("Model must be specified"),
                maxAgentIterations = maxAgentIterations,
                missingToolsConversionStrategy = missingToolsConversionStrategy,
            )
        }
    }

    /**
     * Configuration class for OpenAI integration, providing options to set
     * API-specific paths, network timeouts, and base connection settings.
     *
     * @param apiKey The API key used for authenticating with the OpenAI service.
     */
    public class OpenAIConfig(
        private val apiKey: String,
    ) {
        /**
         * The base URL for the OpenAI API. This property defines the endpoint that the client
         * connects to for making API requests. It is used to construct the full URL for various
         * API operations such as chat completions, embeddings, and moderations.
         *
         * The default value is set to "https://api.openai.com". This can be overridden for
         * custom API endpoints or testing purposes by changing its value.
         */
        public var baseUrl: String = "https://api.openai.com"

        /**
         * A configuration property that defines timeout settings for network interactions with the OpenAI API.
         * It specifies limits for request execution time, connection establishment time, and socket operation time.
         * These timeout values are represented in milliseconds and provide control over handling delayed or
         * unresponsive network operations.
         *
         * The default values for these timeouts are derived from the `ConnectionTimeoutConfig` class, but can
         * be customized through the `timeouts` function in `OpenAIConfig`.
         *
         * Used primarily when configuring an `OpenAILLMClient` for making API requests.
         */
        public var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        /**
         * Represents the API path segment used for OpenAI's chat completions endpoint.
         *
         * This variable can be configured to specify a custom endpoint path when interacting
         * with the OpenAI chat completions API. By default, it is set to "v1/chat/completions".
         */
        public var chatCompletionsPath: String = "v1/chat/completions"

        /**
         * Specifies the API path for embedding operations in the OpenAI API.
         *
         * This variable determines the endpoint to be used when interacting with
         * embedding-related functionalities provided by the OpenAI service.
         * By default, it is set to "v1/embeddings".
         *
         * Can be customized to target a different API path if required.
         */
        public var embeddingsPath: String = "v1/embeddings"

        /**
         * Represents the API path for the moderation endpoint used in OpenAI API requests.
         * This is a constant value and is typically appended to the base URL when making
         * requests to moderation-related services.
         */
        public val moderationsPath: String = "v1/moderations"

        /**
         * Represents the HTTP client used for making network requests to the OpenAI API.
         * This client is configurable and can be replaced or customized to meet specific requirements,
         * such as adjusting timeouts, adding interceptors, or modifying base client behavior.
         * The default implementation initializes with a standard `HttpClient` instance.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures custom timeout settings for the OpenAI API client.
         *
         * This method allows users to specify custom timeout values by providing
         * a lambda using the `TimeoutConfiguration` class. The configured timeouts
         * will then be used for API requests, including request timeout, connection
         * timeout, and socket timeout.
         *
         * @param configure A lambda with the `TimeoutConfiguration` receiver to define
         *                  custom timeout values for request, connection, and socket operations.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    /**
     * AnthropicConfig is a configuration class for integrating with the Anthropic API.
     * It allows for customization of base API URL, model versions, API version, timeout settings,
     * and the HTTP client used for requests. This class facilitates specifying all necessary
     * parameters and settings required to interact with Anthropic's LLM services.
     *
     * @constructor Creates an instance of AnthropicConfig with a mandatory API key.
     *
     * @param apiKey The API key used for authenticating requests to the Anthropic API.
     */
    public class AnthropicConfig(
        private val apiKey: String,
    ) {
        /**
         * Specifies the base URL for the Anthropic API used in client requests.
         *
         * This URL serves as the root endpoint for all API interactions with Anthropic services.
         * It can be customized to point to different server environments (e.g., production, staging, or testing).
         * By default, it is set to "https://api.anthropic.com".
         */
        public var baseUrl: String = "https://api.anthropic.com"

        /**
         * Maps a specific `LLModel` to its corresponding version string. This configuration is primarily
         * used to associate particular model identifiers with their appropriate versions, allowing the
         * system to select or adjust model behaviors based on these mappings.
         *
         * By default, this property is initialized with a predefined map (`DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP`),
         * but can be customized to support other mappings depending on the requirements.
         *
         * This property is typically utilized in the configuration of interaction with Anthropic LLM clients
         * to ensure appropriate versioned models are used during LLM execution.
         */
        public var modelVersionsMap: Map<LLModel, String> = DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP

        /**
         * Specifies the API version used for requests to the Anthropic API.
         *
         * This variable determines the version of the API that the client interacts with and ensures compatibility
         * with the desired API features and endpoints. It plays a key role in configuring Anthropic API requests
         * and is initialized to the default API version provided by the system.
         *
         * The value can be updated to specify a different version if required for a specific use case.
         */
        public var apiVersion: String = DEFAULT_ANTHROPIC_API_VERSION

        /**
         * Configures the timeout settings for API requests, connection establishment, and
         * socket operations when interacting with the Anthropic API.
         * This property is used to customize timeout behavior to handle use cases
         * requiring different default durations for network-related operations.
         */
        public var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        /**
         * Represents the HTTP client that is used to perform network operations
         * such as API requests within the AnthropicConfig configuration.
         *
         * This variable serves as the base client for executing HTTP calls, including
         * request preparation, timeout handling, and connection management, utilizing
         * settings specified in the configuration.
         *
         * It can be customized or replaced if an alternative HTTP client
         * is required for specific use cases or integrations.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures the timeout values for network requests, connection establishment,
         * and socket operations by applying the provided configuration block.
         *
         * @param configure A lambda function to customize the timeout configuration
         *                  using the provided TimeoutConfiguration instance.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    /**
     * GoogleConfig is a configuration class for setting up and customizing
     * integrations with the Google Generative Language API. It allows for
     * specifying an API key, configuring timeouts, and setting the base URL
     * used for API requests.
     *
     * @constructor Creates an instance of GoogleConfig with the provided API key.
     *
     * @param apiKey The API key required to authenticate requests to the Google Generative Language API.
     */
    public class GoogleConfig(
        private val apiKey: String,
    ) {
        /**
         * Specifies the base URL for API requests to the Generative Language API.
         * It determines the endpoint to which HTTP requests are made.
         *
         * By default, this is set to "https://generativelanguage.googleapis.com".
         * Users can customize this value to point to alternative endpoints if needed.
         */
        public var baseUrl: String = "https://generativelanguage.googleapis.com"

        /**
         * Represents the timeout configuration for network interactions with the Google API.
         * This configuration includes parameters for setting the timeouts for request execution,
         * connection establishment, and socket communication.
         *
         * The default values for the configuration are inherited from the defaults specified
         * in the `ConnectionTimeoutConfig` class.
         */
        public var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        /**
         * httpClient is an instance of HttpClient used for making HTTP requests to external services.
         * This property is configurable and allows customization of the HTTP client settings, such as
         * connection timeouts, headers, and other HTTP-specific configurations.
         *
         * In the context of the containing class, it integrates with the provided timeout configuration
         * and base URL setup to facilitate requests, typically to the Google Generative Language API.
         *
         * This property acts as the base client for APIs and can be overridden or modified as needed
         * to suit specific requirements in HTTP communication or integration scenarios.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures the timeout settings for requests, connections, and socket operations.
         * Applies the settings specified in the provided configuration block to update the `timeoutConfig`.
         *
         * @param configure A lambda function where the timeout values can be customized
         *                  using properties from the TimeoutConfiguration class.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    /**
     * OpenRouterConfig is a configuration class for setting up the OpenRouter client.
     * It manages essential parameters such as API key, base URL, connection timeout settings,
     * and the HTTP client used for requests.
     *
     * @property apiKey The API key used for authenticating with the OpenRouter service.
     */
    public class OpenRouterConfig(
        private val apiKey: String,
    ) {
        /**
         * Defines the base URL used for configuring the target endpoint of the OpenRouter API.
         * This property allows customization of the API's base endpoint to interact with different server environments
         * or instances beyond the default URL.
         *
         * The default value is `https://openrouter.ai`.
         */
        public var baseUrl: String = "https://openrouter.ai"

        /**
         * Represents the configuration for connection timeouts used in network requests.
         * This configuration specifies the timeout durations in milliseconds for requests,
         * connection establishment, and socket operations.
         *
         * By default, it is initialized with the default timeout values provided by the
         * `ConnectionTimeoutConfig` class. It can be modified using the `timeouts` function
         * in the containing `OpenRouterConfig` class, or directly assigned with a new instance
         * of `ConnectionTimeoutConfig`.
         */
        public var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        /**
         * Represents the HTTP client used to handle network requests within the configuration.
         * This client can be customized or replaced to adapt to specific use cases, such as
         * modifying headers, interceptors, or other client-level configurations.
         *
         * By default, it is initialized with a standard instance of `HttpClient`.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures timeout settings to be applied to the client.
         *
         * @param configure A lambda receiver that configures an instance of TimeoutConfiguration.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    /**
     * OllamaConfig is a configuration class for managing the settings required to connect
     * and interact with an Ollama-based language model server. It includes properties for setting
     * the server's base URL, connection timeouts, and an HTTP client for underlying network communication.
     */
    public class OllamaConfig {
        /**
         * The base URL for the Ollama API, used as the endpoint for all HTTP requests made
         * by the Ollama client. By default, it is set to `http://localhost:11434`.
         *
         * This property can be configured to point to a custom server or different instance
         * of the Ollama service, depending on the deployment or development needs.
         *
         * For example, `baseUrl` might need to be updated if the Ollama service is hosted on
         * a remote server or a different port.
         */
        public var baseUrl: String = "http://localhost:11434"

        /**
         * Configuration object for specifying timeout settings for network operations
         * within the OllamaConfig class. It defines timeouts for requests, connection
         * establishment, and socket operations in milliseconds.
         *
         * The timeout settings can be updated using the `timeouts` function within the
         * OllamaConfig class, where custom timeout values can be provided.
         */
        public var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        /**
         * A configurable HTTP client used for handling HTTP requests and responses.
         * This client is used to interact with external APIs or services requiring network communication.
         * It can be customized with specific timeout configurations and other properties through the containing class.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures timeout settings for network connections by applying the provided configuration block.
         *
         * @param configure A lambda function with `TimeoutConfiguration` as the receiver,
         *                  allowing customization of request, connection, and socket timeouts.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    /**
     * Configures and initializes an OpenAI LLM client.
     *
     * @param apiKey The API key used for authenticating with the OpenAI API.
     * @param configure A lambda receiver to customize the OpenAI configuration such as base URL, timeout settings, and paths.
     */
    internal fun openAI(apiKey: String, configure: OpenAIConfig.() -> Unit) {
        val client = with(OpenAIConfig(apiKey)) {
            configure()
            OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(
                    baseUrl = baseUrl,
                    timeoutConfig = timeoutConfig,
                    chatCompletionsPath = chatCompletionsPath,
                    embeddingsPath = embeddingsPath,
                    moderationsPath = moderationsPath
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.OpenAI, client)
    }

    /**
     * Configures and initializes an Anthropic LLM client using the provided API key and configuration.
     *
     * @param apiKey The API key used to authenticate with the Anthropic API.
     * @param configure A lambda function to customize the Anthropic client settings.
     */
    internal fun anthropic(apiKey: String, configure: AnthropicConfig.() -> Unit) {
        val client = with(AnthropicConfig(apiKey)) {
            configure()
            AnthropicLLMClient(
                apiKey = apiKey,
                settings = AnthropicClientSettings(
                    baseUrl = baseUrl,
                    modelVersionsMap = modelVersionsMap,
                    apiVersion = apiVersion,
                    timeoutConfig = timeoutConfig
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.Anthropic, client)
    }

    /**
     * Configures and initializes a Google client using the provided API key and configuration settings.
     *
     * @param apiKey The API key used to authenticate requests to the Google API.
     * @param configure A configuration block used to set up the `GoogleConfig` instance for the client.
     */
    internal fun google(apiKey: String, configure: GoogleConfig.() -> Unit) {
        val client = with(GoogleConfig(apiKey)) {
            configure()
            GoogleLLMClient(
                apiKey = apiKey,
                settings = GoogleClientSettings(
                    baseUrl = baseUrl,
                    timeoutConfig = timeoutConfig
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.Google, client)
    }

    /**
     * Configures and integrates an OpenRouter client into the system using the provided API key and configuration.
     *
     * @param apiKey The API key for authenticating with the OpenRouter service.
     * @param configure A lambda to set up additional configurations for the OpenRouter client.
     */
    internal fun openRouter(apiKey: String, configure: OpenRouterConfig.() -> Unit) {
        val client = with(OpenRouterConfig(apiKey)) {
            configure()
            OpenRouterLLMClient(
                apiKey = apiKey,
                settings = OpenRouterClientSettings(
                    baseUrl = baseUrl,
                    timeoutConfig = timeoutConfig
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.OpenRouter, client)
    }

    /**
     * Configures and registers an Ollama client for use with the server configuration.
     *
     * @param configure A lambda function to configure an instance of [OllamaConfig].
     */
    internal fun ollama(configure: OllamaConfig.() -> Unit) {
        val client = with(OllamaConfig()) {
            configure()
            OllamaClient(
                baseUrl = baseUrl,
                baseClient = httpClient,
                timeoutConfig = timeoutConfig
            )
        }
        addLLMClient(LLMProvider.Ollama, client)
    }

    /**
     * Associates a large language model (LLM) client with its provider in the configuration.
     *
     * @param provider The LLMProvider that uniquely identifies the specific large language model provider.
     * @param client The LLMClient instance that communicates directly with the specified LLM provider.
     */
    internal fun addLLMClient(provider: LLMProvider, client: LLMClient) {
        llmConnections[provider] = client
    }

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
private val KoogPluginKey = AttributeKey<KoogInstance>("koog.prompt.executor")

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
public class KoogInstance(
    public val promptExecutor: PromptExecutor,
    public val defaultLLM: LLModel?,
    public val tools: ToolRegistry,
    public val agentConfig: AIAgentConfig?,
    public val agentFeatures: List<AgentFeature>
)


/**
 * A scoped plugin named "KoogAgents" for managing the Koog instance lifecycle in the application context.
 *
 * The plugin initializes the necessary components such as the `MultiLLMPromptExecutor` and `KoogInstance`
 * using configuration parameters provided via `pluginConfig`. The `KoogInstance` carries the core functionality
 * for language model communication, agent tools, configurations, and features.
 *
 * The initialized `KoogInstance` is then stored in the application's attributes to be accessible across the application.
 */
public val Koog: RouteScopedPlugin<KoogAgentsConfig> = createRouteScopedPlugin(
    name = "KoogAgents",
    createConfiguration = ::KoogAgentsConfig
) {
    // Read LLM configurations from the application configuration
    val configFromEnvironment = try {
        loadEnvironmentConfig(application.environment.config)
    } catch (e: Exception) {
        application.log.error("Failed to read Koog configuration from application config", e)

        KoogAgentsConfig()
    }

    val executor = MultiLLMPromptExecutor(
        llmClients = configFromEnvironment.llmConnections + pluginConfig.llmConnections,
        fallback = pluginConfig.fallbackLLMSettings
    )

    val koogInstance = KoogInstance(
        executor,
        pluginConfig.defaultLLM,
        pluginConfig.agentTools,
        pluginConfig.agentConfig,
        pluginConfig.agentFeatures
    )

    application.attributes.put(KoogPluginKey, koogInstance)
}

/**
 * Represents a type alias for a lambda function that extends the [FeatureContext] receiver,
 * allowing the configuration or addition of specific features to a Kotlin AI Agent instance.
 *
 * This type is intended to encapsulate feature-specific logic that operates within the
 * [FeatureContext], abstracting the internal mechanisms for installing or customizing features
 * as part of the agent's functionality.
 */
internal typealias AgentFeature = FeatureContext.() -> Unit

/**
 * Provides access to the `KoogInstance` associated with the current `Route`.
 *
 * The `koog` property retrieves the `KoogInstance` stored as an attribute
 * in the current `Route`. This instance serves as the central configuration
 * and runtime context, encapsulating various components required for
 * executing language model interactions and agent functionalities.
 *
 * The `KoogInstance` includes:
 * - `PromptExecutor`: Facilitates executing prompts in language models.
 * - `defaultLLM`: Specifies the default language model to use if none is explicitly provided.
 * - `tools`: Maintains a registry of tools available for agent use.
 * - `agentConfig`: Optional configuration for an AI-based agent.
 * - `agentFeatures`: Defines features/capabilities enabled for the agent.
 *
 * @return The `KoogInstance` associated with the current `Route`.
 * @throws IllegalStateException if the `KoogInstance` is not yet configured for the `Route`.
 */
public val Route.koog: KoogInstance get() = attributes[KoogPluginKey]

/**
 * A property associated with the `Route` that provides access to an instance of `PromptExecutor`.
 *
 * This property is used to execute language model prompts with or without tool assistance,
 * stream responses, moderate content, or handle multiple language model choices.
 * The `PromptExecutor` serves as a core component when integrating language model capabilities
 * into `Route`-based AI workflows.
 *
 * Typical usages include creating AI agents or facilitating prompt execution within the context
 * of a `Route`.
 */
public val Route.promptExecutor: PromptExecutor get() = koog.promptExecutor

/**
 * Provides access to the `ToolRegistry` associated with this route.
 *
 * `agentTools` is a property that retrieves the tools available for agents within the context of the current route.
 * This property delegates to the `koog.tools` property for obtaining the `ToolRegistry`.
 *
 * Typically, this registry serves as a centralized resource, allowing agents to retrieve tools by name or type,
 * facilitating their operational tasks. It is utilized in configurations and executions where tools are required.
 *
 * @receiver The current `Route` context to which the tool registry is associated.
 * @return The `ToolRegistry` instance containing the collection of tools available for the route.
 */
internal val Route.agentTools: ToolRegistry get() = koog.tools

/**
 * Provides access to the AI agent configuration associated with the current route.
 *
 * This property retrieves an `AIAgentConfig` instance, which defines the configuration
 * needed for an AI agent to operate, including the prompt, model, iteration limits,
 * and strategies for managing missing tools. If no configuration is set, the value will be `null`.
 *
 * The configuration retrieved by this property is used to initialize AI agents in the
 * route through relevant functions.
 */
internal val Route.agentConfig: AIAgentConfig? get() = koog.agentConfig

/**
 * Retrieves a list of agent-specific features associated with the given route.
 *
 * The `agentFeatures` property provides access to a list of `AgentFeature` objects,
 * which represent the features or attributes tied to an agent within the specified route.
 */
internal val Route.agentFeatures: List<AgentFeature> get() = koog.agentFeatures

/**
 * Creates an AI agent using the provided AI agent strategy within the specified route.
 *
 * @param Input The type of input data for the AI agent.
 * @param Output The type of output data for the AI agent.
 * @param strategy The AI agent strategy defining the workflow and execution logic of the agent.
 * @return An instance of `AIAgent` configured with the specified strategy and the route's resources.
 * @throws IllegalArgumentException If the agent configuration (`agentConfig`) is not set in the route.
 */
public fun <Input, Output> Route.aiAgent(strategy: AIAgentStrategy<Input, Output>): AIAgent<Input, Output> = AIAgent(
    promptExecutor = promptExecutor,
    strategy = strategy,
    agentConfig = agentConfig ?: throw IllegalArgumentException("agentConfig is not set"),
    toolRegistry = agentTools,
)

/**
 * Creates and configures an AI Agent with the specified execution mode.
 *
 * @param runMode The execution mode for the AI Agent as defined by the `ToolCalls` enum.
 *                Defaults to `ToolCalls.SINGLE_RUN_SEQUENTIAL`, which allows multiple
 *                tool calls executed in a sequential manner.
 * @return A configured instance of `AIAgent` with the given execution strategy and tools.
 */
public fun Route.aiAgent(runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL): AIAgent<String, String> = AIAgent(
    promptExecutor = promptExecutor,
    strategy = singleRunStrategy(),
    agentConfig = agentConfig ?: throw IllegalArgumentException("agentConfig is not set"),
    toolRegistry = agentTools,
)

/**
 * Executes a prompt using a specified large language model (LLM) and optional tools, returning a list of responses.
 *
 * @param model The LLM to be used for processing the prompt. If null, a default LLM is used.
 * @param tools A list of tools that the LLM may utilize during execution. Defaults to an empty list.
 * @param buildPrompt A lambda to construct the prompt using a [PromptBuilder].
 * @return A list of [Message.Response] objects generated by the LLM after processing the prompt.
 * @throws IllegalArgumentException If no LLM is specified and no default LLM is configured.
 */
public suspend fun RoutingContext.askLLM(
    model: LLModel?,
    tools: List<ToolDescriptor> = emptyList(),
    buildPrompt: PromptBuilder.() -> Unit
): List<Message.Response> {
    val prompt = Prompt.build("id", init = buildPrompt)

    val koog = call.application.attributes[KoogPluginKey]

    return koog.promptExecutor.execute(
        prompt = prompt,
        model = model ?: koog.defaultLLM ?: throw IllegalArgumentException("LLM not specified"),
        tools = tools
    )
}

/**
 * Executes a moderation task using a specified Large Language Model (LLM) and a dynamically built prompt.
 *
 * @param model An optional [LLModel] to be used for moderation. If not provided, a default LLM will be used if available.
 * @param buildPrompt A lambda to construct the moderation prompt using a [PromptBuilder].
 * @return A [ModerationResult] containing the result of the moderation process.
 */
public suspend fun RoutingContext.moderateWithLLM(
    model: LLModel?,
    buildPrompt: PromptBuilder.() -> Unit
): ModerationResult {
    val prompt = Prompt.build("id", init = buildPrompt)

    val koog = call.application.attributes[KoogPluginKey]

    return koog.promptExecutor.moderate(
        prompt = prompt,
        model = model ?: koog.defaultLLM ?: throw IllegalArgumentException("LLM not specified")
    )
}


/**
 * Executes the AI agent strategy with the provided input and responds with the result.
 *
 * This method invokes a specified `AIAgentStrategy` to process the input data through a
 * defined AI agent workflow and then responds with the processed output data. The response
 * is returned with an HTTP status code of 200 (OK).
 *
 * @param Input The type of the input data to be processed.
 * @param Output The type of the output data generated by processing the input.
 * @param input The input data to be processed by the AI agent strategy.
 * @param strategy The AI agent strategy that defines how the input is transformed into the output
 * by executing a subgraph of interconnected workflow nodes.
 */
public suspend inline fun <Input, reified Output : Any> RoutingCall.agentRespond(
    input: Input,
    strategy: AIAgentStrategy<Input, Output>
) {
    val agentOutput = route.aiAgent(strategy).run(input)
    respond(HttpStatusCode.OK, agentOutput)
}

/**
 * Executes an AI agent's logic based on the provided input string and responds with the result.
 *
 * @param input The input string to be processed by the AI agent.
 */
public suspend fun RoutingCall.agentRespond(input: String) {
    val agentOutput = route.aiAgent().run(input)
    respond(HttpStatusCode.OK, agentOutput)
}