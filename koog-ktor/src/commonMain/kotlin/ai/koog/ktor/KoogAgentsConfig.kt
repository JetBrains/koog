package ai.koog.ktor

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.ktor.KoogAgentsConfig.TimeoutConfiguration.Companion.DEFAULT_TIMEOUT
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.PromptDSL
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import ai.koog.prompt.dsl.prompt as koogPrompt

/**
 * Configuration class for setting up a Koog agents server.
 * Provides options to configure LLM connections, agent tools, features, and other related settings.
 */
public class KoogAgentsConfig(private val scope: CoroutineScope) {
    /**
     * A mutable map that associates `LLMProvider` instances with their corresponding `LLMClient` implementations.
     *
     * This map is used to store and manage connections to various Large Language Model (LLM) providers,
     * enabling interactions with specific LLM services through their associated client interfaces.
     *
     * Keys:
     * - [LLMProvider]: Represents the provider of the large language model (e.g., OpenAI, Google, Anthropic, etc.).
     *
     * Values:
     * - [LLMClient]: Represents the client responsible for communicating with the respective LLM provider.
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
     * The fallback settings are encapsulated in the [MultiLLMPromptExecutor.FallbackPromptExecutorSettings] class within
     * the [MultiLLMPromptExecutor].
     *
     * It is internally mutable and primarily used within the `KoogAgentsServerConfig` class.
     */
    internal var fallbackLLMSettings: MultiLLMPromptExecutor.FallbackPromptExecutorSettings? = null

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
    internal var agentConfig: AgentConfig = AgentConfig()

    /**
     * A mutable list that stores instances of `AgentFeature` for configuring and customizing
     * agent-specific functionalities within the system.
     *
     * This list serves as a centralized registry to manage features that can be installed
     * and utilized by agents, enabling the extension of agent capabilities via the installation
     * of specific `AgentFeature` implementations.
     */
    internal val agentFeatures: MutableList<FeatureContext.() -> Unit> = mutableListOf()

    /**
     * Configuration class for defining timeout durations in network requests.
     * Used to set and customize the request, connection, and socket timeouts.
     */
    @Serializable
    public data class TimeoutConfiguration(
        /**
         * Specifies the maximum duration allowed for a network request to complete
         * before timing out. It acts as a safeguard to prevent indefinite waiting for responses.
         *
         * The default value is 15.minutes.
         *
         * This is particularly useful for configuring timeout behavior in network-related
         * operations across various integrations.
         */
        public var requestTimeout: Duration = DEFAULT_TIMEOUT,
        /**
         * Specifies the connection timeout duration for establishing a network connection.
         * This value determines the maximum time allowed for a connection to be established before the
         * operation is terminated.
         *
         * Default value is 60 seconds.
         */
        public var connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,

        /**
         * Specifies the maximum amount of time to wait for data over an established
         * socket connection before timing out.
         *
         * This property is used to configure the timeout duration for socket operations, such as reading
         * or writing data through the network connection. Setting this value appropriately ensures that
         * the application does not hang indefinitely when waiting for data to be transmitted or received
         * over the socket.
         *
         * The default value is defined by [DEFAULT_TIMEOUT], which represents 15.minutes.
         */
        public var socketTimeout: Duration = DEFAULT_TIMEOUT
    ) {

        /**
         * Companion object holding default timeout constants for the TimeoutConfiguration class.
         */
        private companion object {
            /**
             * Default timeout duration
             * This value is used for configuring various timeout settings,
             * such as request or socket timeouts, in cases where no custom value is specified.
             * The default duration is set to 15 minutes.
             */
            private val DEFAULT_TIMEOUT: Duration = 15.minutes

            /**
             * Default timeout value for establishing a connection.
             * This value represents the duration to wait before timing out a connection attempt.
             */
            private val DEFAULT_CONNECT_TIMEOUT: Duration = 60.seconds
        }
    }

    /**
     * Configuration class for managing various Language Learning Model (LLM) providers and their settings.
     * This class allows integration with different LLM services such as OpenAI,
     * Anthropic, Google, MistralAI, OpenRouter, DeepSeek, and Ollama.
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
         * Configures the MistralAI client for the application.
         *
         * @param apiKey The API key to authenticate with MistralAI services.
         * @param configure A lambda to customize the `MistralAIConfig` settings such as base URL, timeouts, or HTTP client configuration.
         */
        public fun mistral(apiKey: String, configure: MistralAIConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.mistral(apiKey, configure)
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
         * Configures and initializes the DeepSeek API with the provided API key and optional configuration.
         *
         * @param apiKey The API key used to authenticate with the DeepSeek API.
         * @param configure An optional lambda function used to customize the DeepSeek configuration.
         */
        public fun deepSeek(apiKey: String, configure: DeepSeekConfig.() -> Unit = {}) {
            this@KoogAgentsConfig.deepSeek(apiKey, configure)
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
        internal val scope: CoroutineScope
            get() = this@KoogAgentsConfig.scope

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
         *
         * By default, it uses a [Prompt] instance with the ID "agent" and includes a system
         * message that provides context to the language model by describing its role as a helpful assistant.
         */
        internal var prompt: Prompt = koogPrompt("ai/koog/agent") {
            system("You are a helpful assistant")
        }

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
         * @param build A lambda function for configuring the tool registry using the `ToolRegistry.Builder`.
         */
        public fun registerTools(build: ToolRegistry.Builder.() -> Unit) {
            toolRegistry += ToolRegistry {
                build()
            }
        }

        /**
         * Configures and sets the prompt for the agent using the provided parameters and a prompt-building function.
         *
         * @param llmParams The parameters that define the behavior of the language model, such as temperature
         * and tool selection. Defaults to an instance of `LLMParams`.
         * @param build A lambda function that is used to construct the prompt using a `PromptBuilder`.
         */
        @PromptDSL
        public fun prompt(
            name: String = "ai/koog/agent",
            llmParams: LLMParams = LLMParams(),
            clock: Clock = Clock.System,
            build: PromptBuilder.() -> Unit
        ) {
            prompt = koogPrompt(name, llmParams, clock, build)
        }

        /**
         * Adds an AI agent feature to the current configuration by applying the specific configuration logic.
         *
         * @param TConfig The type of feature configuration that extends [FeatureConfig].
         * @param feature The AI agent feature to be added, which provides functionality and configuration capabilities.
         * @param configure A lambda function to configure the feature. The default is an empty configuration.
         */
        public fun <TConfig : FeatureConfig> install(
            feature: AIAgentGraphFeature<TConfig, *>,
            configure: TConfig.() -> Unit = {}
        ) {
            this@KoogAgentsConfig.agentFeatures += {
                this.install(feature, configure)
            }
        }
    }

    /**
     * Configures default configuration for all AI agents with the specified settings.
     *
     * This method allows the customization of an AI agent by providing a suspendable configuration block
     * that modifies the agent's prompt, model, tool registry, and other parameters. The resulting agent
     * setup is created based on the defined configuration.
     *
     * @param configure A suspendable lambda function that defines the configuration of the agent.
     * The configuration block operates on an instance of [AgentConfig], where properties such as
     * `prompt`, `model`, `maxAgentIterations`, and tools can be customized.
     */
    public fun agentConfig(configure: AgentConfig.() -> Unit) {
        agentConfig = AgentConfig().apply {
            configure()
        }
    }

    /**
     * Configuration class for OpenAI integration, providing options to set
     * API-specific paths, network timeouts, and base connection settings.
     */
    public class OpenAIConfig {

        /**
         * The base URL for the OpenAI API. This property defines the endpoint that the client
         * connects to for making API requests. It is used to construct the full URL for various
         * API operations such as chat completions, embeddings, and moderations.
         *
         * The default value is set to "[OpenAIClientSettings.baseUrl]". This can be overridden for
         * custom API endpoints or testing purposes by changing its value.
         */
        public var baseUrl: String? = null

        /**
         * A configuration property that defines timeout settings for network interactions with the OpenAI API.
         * It specifies limits for request execution time, connection establishment time, and socket operation time.
         * These timeout values are represented in [Duration] and provide control over handling delayed or
         * unresponsive network operations.
         *
         * The default values for these timeouts are derived from the [ConnectionTimeoutConfig] class, but can
         * be customized through the `timeouts` function in [OpenAIConfig].
         *
         * Used primarily when configuring an [OpenAILLMClient] for making API requests.
         */
        public var timeoutConfig: ConnectionTimeoutConfig? = null

        /**
         * Represents the API path segment used for OpenAI's chat completions endpoint.
         *
         * This variable can be configured to specify a custom endpoint path when interacting
         * with the OpenAI chat completions API. By default, it is set to [OpenAIClientSettings.chatCompletionsPath].
         */
        public var chatCompletionsPath: String? = null

        /**
         * Specifies the API path for embedding operations in the OpenAI API.
         *
         * This variable determines the endpoint to be used when interacting with
         * embedding-related functionalities provided by the OpenAI service.
         * By default, it is set to [OpenAIClientSettings.embeddingsPath].
         *
         * Can be customized to target a different API path if required.
         */
        public var embeddingsPath: String? = null

        /**
         * Represents the API path for the moderation endpoint used in OpenAI API requests.
         * This is a constant value and is typically appended to the base URL when making
         * requests to moderation-related services.
         *
         * By default, it is set to [OpenAIClientSettings.moderationsPath].
         */
        public var moderationsPath: String? = null

        /**
         * Represents the HTTP client used for making network requests to the OpenAI API.
         * This client is configurable and can be replaced or customized to meet specific requirements,
         * such as adjusting timeouts, adding interceptors, or modifying base client behavior.
         * The default implementation initializes with a standard [HttpClient] instance.
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
         * @param configure A lambda with the [TimeoutConfiguration] receiver to define
         *                  custom timeout values for request, connection, and socket operations.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
            }
        }
    }

    /**
     * AnthropicConfig is a configuration class for integrating with the Anthropic API.
     * It allows for customization of base API URL, model versions, API version, timeout settings,
     * and the HTTP client used for requests. This class facilitates specifying all necessary
     * parameters and settings required to interact with Anthropic's LLM services.
     */
    public class AnthropicConfig {
        /**
         * Specifies the base URL for the Anthropic API used in client requests.
         *
         * This URL serves as the root endpoint for all API interactions with Anthropic services.
         * It can be customized to point to different server environments (e.g., production, staging, or testing).
         * By default, it is set to [AnthropicClientSettings.baseUrl].
         */
        public var baseUrl: String? = null

        /**
         * Maps a specific `LLModel` to its corresponding version string. This configuration is primarily
         * used to associate particular model identifiers with their appropriate versions, allowing the
         * system to select or adjust model behaviors based on these mappings.
         *
         * By default, this property is initialized with a predefined map ([AnthropicClientSettings.modelVersionsMap]),
         * but can be customized to support other mappings depending on the requirements.
         *
         * This property is typically utilized in the configuration of interaction with Anthropic LLM clients
         * to ensure appropriate versioned models are used during LLM execution.
         */
        public var modelVersionsMap: Map<LLModel, String>? = null

        /**
         * Specifies the API version used for requests to the Anthropic API.
         *
         * This variable determines the version of the API that the client interacts with and ensures compatibility
         * with the desired API features and endpoints. It plays a key role in configuring Anthropic API requests
         * and is initialized to the default API version provided by the system.
         *
         * The value can be updated to specify a different version if required for a specific use case.
         */
        public var apiVersion: String? = null

        /**
         * Configures the timeout settings for API requests, connection establishment, and
         * socket operations when interacting with the Anthropic API.
         * This property is used to customize timeout behavior to handle use cases
         * requiring different default durations for network-related operations.
         */
        public var timeoutConfig: ConnectionTimeoutConfig? = null

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
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
            }
        }
    }

    /**
     * GoogleConfig is a configuration class for setting up and customizing
     * integrations with the Google Generative Language API. It allows for
     * specifying an API key, configuring timeouts, and setting the base URL
     * used for API requests.
     */
    public class GoogleConfig {
        /**
         * Specifies the base URL for API requests to the Generative Language API.
         * It determines the endpoint to which HTTP requests are made.
         *
         * By default, this is set to "[GoogleClientSettings.baseUrl]".
         * Users can customize this value to point to alternative endpoints if needed.
         */
        public var baseUrl: String? = null

        /**
         * Represents the timeout configuration for network interactions with the Google API.
         * This configuration includes parameters for setting the timeouts for request execution,
         * connection establishment, and socket communication.
         *
         * The default values for the configuration are inherited from the defaults specified
         * in the [ConnectionTimeoutConfig] class.
         */
        public var timeoutConfig: ConnectionTimeoutConfig? = null

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
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
            }
        }
    }

    /**
     * Configuration class for MistralAI integration, providing options to set
     * API-specific paths, network timeouts, and base connection settings.
     */
    public class MistralAIConfig {

        /**
         * The base URL for the MistralAI API. This property defines the endpoint that the client
         * connects to for making API requests. It is used to construct the full URL for various
         * API operations such as chat completions, embeddings, and moderations.
         *
         * The default value is set to "[MistralAIClientSettings.baseUrl]". This can be overridden for
         * custom API endpoints or testing purposes by changing its value.
         */
        public var baseUrl: String? = null

        /**
         * Represents the API path segment used for MistralAI's chat completions endpoint.
         *
         * This variable can be configured to specify a custom endpoint path when interacting
         * with the MistralAI chat completions API. By default, it is set to [MistralAIClientSettings.chatCompletionsPath].
         */
        public var chatCompletionsPath: String? = null

        /**
         * Specifies the API path for embedding operations in the MistralAI API.
         *
         * This variable determines the endpoint to be used when interacting with
         * embedding-related functionalities provided by the MistralAI service.
         * By default, it is set to [MistralAIClientSettings.embeddingsPath].
         *
         * Can be customized to target a different API path if required.
         */
        public var embeddingsPath: String? = null

        /**
         * Represents the API path for the moderation endpoint used in MistralAI API requests.
         * This is a constant value and is typically appended to the base URL when making
         * requests to moderation-related services.
         *
         * By default, it is set to [MistralAIClientSettings.moderationPath].
         */
        public val moderationPath: String? = null

        /**
         * A configuration property that defines timeout settings for network interactions with the MistralAI API.
         * It specifies limits for request execution time, connection establishment time, and socket operation time.
         * These timeout values are represented in [Duration] and provide control over handling delayed or
         * unresponsive network operations.
         *
         * The default values for these timeouts are derived from the [ConnectionTimeoutConfig] class, but can
         * be customized through the `timeouts` function in [MistralAIConfig].
         *
         * Used primarily when configuring an [MistralAIClientSettings] for making API requests.
         */
        public var timeoutConfig: ConnectionTimeoutConfig? = null

        /**
         * Represents the HTTP client used for making network requests to the MistralAI API.
         * This client is configurable and can be replaced or customized to meet specific requirements,
         * such as adjusting timeouts, adding interceptors, or modifying base client behavior.
         * The default implementation initializes with a standard [HttpClient] instance.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures custom timeout settings for the MistralAI API client.
         *
         * This method allows users to specify custom timeout values by providing
         * a lambda using the `TimeoutConfiguration` class. The configured timeouts
         * will then be used for API requests, including request timeout, connection
         * timeout, and socket timeout.
         *
         * @param configure A lambda with the [TimeoutConfiguration] receiver to define
         *                  custom timeout values for request, connection, and socket operations.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
            }
        }
    }

    /**
     * OpenRouterConfig is a configuration class for setting up the OpenRouter client.
     * It manages essential parameters such as API key, base URL, connection timeout settings,
     * and the HTTP client used for requests.
     */
    public class OpenRouterConfig {
        /**
         * Defines the base URL used for configuring the target endpoint of the OpenRouter API.
         * This property allows customization of the API's base endpoint to interact with different server environments
         * or instances beyond the default URL.
         *
         * The default value is `[OpenRouterClientSettings.baseUrl]`.
         */
        public var baseUrl: String? = null

        /**
         * Represents the configuration for connection timeouts used in network requests.
         * This configuration specifies the timeout durations in milliseconds for requests,
         * connection establishment, and socket operations.
         *
         * By default, it is initialized with the default timeout values provided by the
         * `ConnectionTimeoutConfig` class. It can be modified using the `timeouts` function
         * in the containing [OpenRouterConfig] class, or directly assigned with a new instance
         * of [ConnectionTimeoutConfig].
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
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
            }
        }
    }

    /**
     * DeepSeekConfig is a configuration class for setting up the DeepSeek client.
     * It manages essential parameters such as API key, base URL, connection timeout settings,
     * and the HTTP client used for requests.
     */
    public class DeepSeekConfig {
        /**
         * Defines the base URL used for configuring the target endpoint of the DeepSeek API.
         * This property allows customization of the API's base endpoint to interact with different server environments
         * or instances beyond the default URL.
         *
         * The default value is `[DeepSeekClientSettings.baseUrl]`.
         */
        public var baseUrl: String? = null

        /**
         * Represents the configuration for connection timeouts used in network requests.
         * This configuration specifies the timeout durations in milliseconds for requests,
         * connection establishment, and socket operations.
         *
         * By default, it is initialized with the default timeout values provided by the
         * `ConnectionTimeoutConfig` class. It can be modified using the `timeouts` function
         * in the containing [DeepSeekConfig] class, or directly assigned with a new instance
         * of [ConnectionTimeoutConfig].
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
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
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
         * by the Ollama client. By default, it is set to `[OllamaClient.baseUrl]`.
         *
         * This property can be configured to point to a custom server or different instance
         * of the Ollama service, depending on the deployment or development needs.
         *
         * For example, `baseUrl` might need to be updated if the Ollama service is hosted on
         * a remote server or a different port.
         */
        public var baseUrl: String? = null

        /**
         * Configuration object for specifying timeout settings for network operations
         * within the OllamaConfig class. It defines timeouts for requests, connection
         * establishment, and socket operations in milliseconds.
         *
         * The timeout settings can be updated using the `timeouts` function within the
         * OllamaConfig class, where custom timeout values can be provided.
         */
        public var timeoutConfig: ConnectionTimeoutConfig? = null

        /**
         * A configurable HTTP client used for handling HTTP requests and responses.
         * This client is used to interact with external APIs or services requiring network communication.
         * It can be customized with specific timeout configurations and other properties through the containing class.
         */
        public var httpClient: HttpClient = HttpClient()

        /**
         * Configures timeout settings for network connections by applying the provided configuration block.
         *
         * @param configure A lambda function with [TimeoutConfiguration] as the receiver,
         *                  allowing customization of request, connection, and socket timeouts.
         */
        public fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(
                    requestTimeout.inWholeMilliseconds,
                    connectTimeout.inWholeMilliseconds,
                    socketTimeout.inWholeMilliseconds
                )
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
        val client = with(OpenAIConfig()) {
            configure()
            val defaults = OpenAIClientSettings()

            OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(
                    baseUrl = baseUrl ?: defaults.baseUrl,
                    timeoutConfig = timeoutConfig ?: defaults.timeoutConfig,
                    chatCompletionsPath = chatCompletionsPath ?: defaults.chatCompletionsPath,
                    embeddingsPath = embeddingsPath ?: defaults.embeddingsPath,
                    moderationsPath = moderationsPath ?: defaults.moderationsPath,
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
        val client = with(AnthropicConfig()) {
            configure()

            val default = AnthropicClientSettings()
            val settings = AnthropicClientSettings(
                baseUrl = baseUrl ?: default.baseUrl,
                apiVersion = apiVersion ?: default.apiVersion,
                timeoutConfig = timeoutConfig ?: default.timeoutConfig,
                modelVersionsMap = modelVersionsMap ?: default.modelVersionsMap,
            )

            AnthropicLLMClient(
                apiKey = apiKey,
                settings = settings,
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
        val client = with(GoogleConfig()) {
            configure()
            val defaults = GoogleClientSettings()

            GoogleLLMClient(
                apiKey = apiKey,
                settings = GoogleClientSettings(
                    baseUrl = baseUrl ?: defaults.baseUrl,
                    timeoutConfig = timeoutConfig ?: defaults.timeoutConfig,
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.Google, client)
    }

    /**
     * Configures and initializes an MistralAI LLM client.
     *
     * @param apiKey The API key used for authenticating with the MistralAI API.
     * @param configure A lambda receiver to customize the MistralAI configuration such as base URL, timeout settings, and paths.
     */
    internal fun mistral(apiKey: String, configure: MistralAIConfig.() -> Unit) {
        val client = with(MistralAIConfig()) {
            configure()
            val defaults = MistralAIClientSettings()

            MistralAILLMClient(
                apiKey = apiKey,
                settings = MistralAIClientSettings(
                    baseUrl = baseUrl ?: defaults.baseUrl,
                    chatCompletionsPath = chatCompletionsPath ?: defaults.chatCompletionsPath,
                    embeddingsPath = embeddingsPath ?: defaults.embeddingsPath,
                    moderationPath = moderationPath ?: defaults.moderationPath,
                    timeoutConfig = timeoutConfig ?: defaults.timeoutConfig,
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.MistralAI, client)
    }

    /**
     * Configures and integrates an OpenRouter client into the system using the provided API key and configuration.
     *
     * @param apiKey The API key for authenticating with the OpenRouter service.
     * @param configure A lambda to set up additional configurations for the OpenRouter client.
     */
    internal fun openRouter(apiKey: String, configure: OpenRouterConfig.() -> Unit) {
        val client = with(OpenRouterConfig()) {
            configure()
            val defaults = OpenRouterClientSettings()

            OpenRouterLLMClient(
                apiKey = apiKey,
                settings = OpenRouterClientSettings(
                    baseUrl = baseUrl ?: defaults.baseUrl,
                    timeoutConfig = timeoutConfig
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.OpenRouter, client)
    }

    /**
     * Configures and integrates a DeepSeek client into the system using the provided API key and configuration.
     *
     * @param apiKey The API key for authenticating with the DeepSeek service.
     * @param configure A lambda to set up additional configurations for the DeepSeek client.
     */
    internal fun deepSeek(apiKey: String, configure: DeepSeekConfig.() -> Unit) {
        val client = with(DeepSeekConfig()) {
            configure()
            val defaults = DeepSeekClientSettings()

            DeepSeekLLMClient(
                apiKey = apiKey,
                settings = DeepSeekClientSettings(
                    baseUrl = baseUrl ?: defaults.baseUrl,
                    timeoutConfig = timeoutConfig
                ),
                baseClient = httpClient
            )
        }
        addLLMClient(LLMProvider.DeepSeek, client)
    }

    /**
     * Configures and registers an Ollama client for use with the server configuration.
     *
     * @param configure A lambda function to configure an instance of [OllamaConfig].
     */
    internal fun ollama(configure: OllamaConfig.() -> Unit) {
        val client = with(OllamaConfig()) {
            configure()
            val defaults = OllamaClient()

            OllamaClient(
                baseUrl = baseUrl ?: defaults.baseUrl,
                baseClient = httpClient,
                timeoutConfig = timeoutConfig ?: ConnectionTimeoutConfig()
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
