package ai.koog.agents.example.ktor

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
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.mcp.DefaultMcpToolDescriptorParser
import ai.koog.agents.mcp.McpToolDescriptorParser
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME
import ai.koog.agents.mcp.McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION
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
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.runBlocking

internal val DEFAULT_PROMPT = prompt("agent") {
    system("You are a helpful assistant")
}

class KoogAgentsServerConfig {
    internal val llmConnections: MutableMap<LLMProvider, LLMClient> = mutableMapOf()
    internal var fallbackLLMSettings: MultiLLMPromptExecutor.FallbackPromptExecutorSettings? = null

    var defaultLLM: LLModel? = null

    internal var agentTools: ToolRegistry = ToolRegistry.EMPTY
    internal var agentConfig: AIAgentConfig? = null
    internal val agentFeatures: MutableList<AgentFeature> = mutableListOf()

    class TimeoutConfiguration {
        var requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MS
        var connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS
        var socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MS

        private companion object {
            private const val DEFAULT_TIMEOUT_MS: Long = 900000 // 900 seconds
            private const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 60_000
        }
    }

    inner class LLMConfig {
        fun openAI(apiKey: String, configure: OpenAIConfig.() -> Unit = {}) {
            this@KoogAgentsServerConfig.openAI(apiKey, configure)
        }

        fun anthropic(apiKey: String, configure: AnthropicConfig.() -> Unit = {}) {
            this@KoogAgentsServerConfig.anthropic(apiKey, configure)
        }

        fun google(apiKey: String, configure: GoogleConfig.() -> Unit = {}) {
            this@KoogAgentsServerConfig.google(apiKey, configure)
        }

        fun openRouter(apiKey: String, configure: OpenRouterConfig.() -> Unit = {}) {
            this@KoogAgentsServerConfig.openRouter(apiKey, configure)
        }

        fun ollama(configure: OllamaConfig.() -> Unit = {}) {
            this@KoogAgentsServerConfig.ollama(configure)
        }

        fun addClient(provider: LLMProvider, client: LLMClient) {
            this@KoogAgentsServerConfig.addLLMClient(provider, client)
        }

        inner class FallbackLLMConfig {
            var provider: LLMProvider? = null
            var model: LLModel? = null
        }

        fun fallback(configure: FallbackLLMConfig.() -> Unit) {
            with(FallbackLLMConfig()) {
                configure()
                fallbackLLMSettings = MultiLLMPromptExecutor.FallbackPromptExecutorSettings(
                    provider ?: error("Fallback provider must be specified"),
                    model ?: error("Fallback model must be specified")
                )
            }
        }
    }

    fun llm(configure: LLMConfig.() -> Unit) {
        LLMConfig().configure()
    }


    inner class AgentConfig {
        internal var toolRegistry: ToolRegistry = ToolRegistry.EMPTY

        var prompt: Prompt = prompt("agent") {
            system("You are a helpful assistant")
        }

        var model: LLModel? = null

        var maxAgentIterations: Int = 50

        var missingToolsConversionStrategy: MissingToolsConversionStrategy =
            MissingToolsConversionStrategy.Missing(ToolCallDescriber.JSON)

        fun registerTools(bindTools: ToolRegistry.Builder.() -> Unit) {
            toolRegistry += ToolRegistry {
                bindTools()
            }
        }

        inner class MCPToolsConfig() {
            suspend fun process(
                process: Process,
                mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
                name: String = DEFAULT_MCP_CLIENT_NAME,
                version: String = DEFAULT_MCP_CLIENT_VERSION,
            ) {
                this@AgentConfig.toolRegistry += McpToolRegistryProvider.fromTransport(
                    transport = McpToolRegistryProvider.defaultStdioTransport(process),
                    mcpToolParser = mcpToolParser,
                    name = name,
                    version = version,
                )
            }

            suspend fun sse(
                url: String,
                mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
                name: String = DEFAULT_MCP_CLIENT_NAME,
                version: String = DEFAULT_MCP_CLIENT_VERSION,
            ) {
                this@AgentConfig.toolRegistry += McpToolRegistryProvider.fromTransport(
                    transport = McpToolRegistryProvider.defaultSseTransport(url),
                    mcpToolParser = mcpToolParser,
                    name = name,
                    version = version,
                )
            }

            suspend fun client(
                mcpClient: Client,
                mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser
            ) {
                this@AgentConfig.toolRegistry += McpToolRegistryProvider.fromClient(mcpClient, mcpToolParser)
            }
        }

        suspend fun mcp(configure: suspend MCPToolsConfig.() -> Unit) {
            MCPToolsConfig().configure()
        }

        fun prompt(llmParams: LLMParams = LLMParams(), buildPrompt: PromptBuilder.() -> Unit) {
            prompt = prompt("agent", llmParams, build = buildPrompt)
        }

        fun <TConfig : FeatureConfig> install(feature: AIAgentFeature<TConfig, *>, configure: TConfig.() -> Unit = {}) {
            this@KoogAgentsServerConfig.agentFeatures += {
                install(feature, configure)
            }
        }
    }

    fun agent(configure: suspend AgentConfig.() -> Unit) {
        with(AgentConfig()) {
            runBlocking {
                configure()
            }

            agentTools = toolRegistry
            agentConfig = AIAgentConfig(
                prompt = prompt,
                model = model ?: defaultLLM ?: throw IllegalArgumentException("Model must be specified"),
                maxAgentIterations = maxAgentIterations,
                missingToolsConversionStrategy = missingToolsConversionStrategy,
            )
        }
    }

    class OpenAIConfig(
        val apiKey: String,
    ) {
        var baseUrl: String = "https://api.openai.com"
        var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
        var chatCompletionsPath: String = "v1/chat/completions"
        var embeddingsPath: String = "v1/embeddings"
        val moderationsPath: String = "v1/moderations"

        var httpClient = HttpClient()

        fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    class AnthropicConfig(
        val apiKey: String,
    ) {
        var baseUrl: String = "https://api.anthropic.com"
        var modelVersionsMap: Map<LLModel, String> = DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP
        var apiVersion: String = DEFAULT_ANTHROPIC_API_VERSION
        var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        var httpClient = HttpClient()

        fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    class GoogleConfig(
        val apiKey: String,
    ) {
        var baseUrl: String = "https://generativelanguage.googleapis.com"
        var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        var httpClient = HttpClient()

        fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    class OpenRouterConfig(
        val apiKey: String,
    ) {
        var baseUrl: String = "https://openrouter.ai"
        var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        var httpClient = HttpClient()

        fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

    class OllamaConfig {
        var baseUrl: String = "http://localhost:11434"
        var timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()

        var httpClient = HttpClient()

        fun timeouts(configure: TimeoutConfiguration.() -> Unit) {
            timeoutConfig = with(TimeoutConfiguration()) {
                configure()
                ConnectionTimeoutConfig(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis)
            }
        }
    }

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

    internal fun addLLMClient(provider: LLMProvider, client: LLMClient) {
        llmConnections[provider] = client
    }

}

private val KoogPluginKey = AttributeKey<KoogInstance>("koog.prompt.executor")

class KoogInstance(
    val promptExecutor: PromptExecutor,
    val defaultLLM: LLModel?,
    val tools: ToolRegistry,
    val agentConfig: AIAgentConfig?,
    val agentFeatures: List<AgentFeature>
)


val Koog = createRouteScopedPlugin("KoogAgents", ::KoogAgentsServerConfig) {
    val executor = MultiLLMPromptExecutor(pluginConfig.llmConnections, fallback = pluginConfig.fallbackLLMSettings)

    val koogInstance = KoogInstance(
        executor,
        pluginConfig.defaultLLM,
        pluginConfig.agentTools,
        pluginConfig.agentConfig,
        pluginConfig.agentFeatures
    )

    application.attributes.put(KoogPluginKey, koogInstance)
}

internal typealias AgentFeature = FeatureContext.() -> Unit

val Route.koog: KoogInstance get() = attributes[KoogPluginKey]
val Route.promptExecutor: PromptExecutor get() = koog.promptExecutor
internal val Route.agentTools: ToolRegistry get() = koog.tools
internal val Route.agentConfig: AIAgentConfig? get() = koog.agentConfig
internal val Route.agentFeatures: List<AgentFeature> get() = koog.agentFeatures

fun <Input, Output> Route.aiAgent(strategy: AIAgentStrategy<Input, Output>): AIAgent<Input, Output> = AIAgent(
    promptExecutor = promptExecutor,
    strategy = strategy,
    agentConfig = agentConfig ?: throw IllegalArgumentException("agentConfig is not set"),
    toolRegistry = agentTools,
)

fun Route.aiAgent(runMode: ToolCalls = ToolCalls.SINGLE_RUN_SEQUENTIAL): AIAgent<String, String> = AIAgent(
    promptExecutor = promptExecutor,
    strategy = singleRunStrategy(),
    agentConfig = agentConfig ?: throw IllegalArgumentException("agentConfig is not set"),
    toolRegistry = agentTools,
)

suspend fun RoutingContext.askLLM(
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

suspend fun RoutingContext.moderateWithLLM(
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


class MyAIAgent()

suspend inline fun <Input, reified Output : Any> RoutingCall.agentRespond(
    input: Input,
    strategy: AIAgentStrategy<Input, Output>
) {
    val agentOutput = route.aiAgent(strategy).run(input)
    respond(HttpStatusCode.OK, agentOutput)
}

suspend fun RoutingCall.agentRespond(input: String) {
    val agentOutput = route.aiAgent().run(input)
    respond(HttpStatusCode.OK, agentOutput)
}