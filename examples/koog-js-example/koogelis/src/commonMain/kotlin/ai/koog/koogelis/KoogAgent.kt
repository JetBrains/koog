package ai.koog.koogelis

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.koogelis.llm.SupportedGoogleModels
import ai.koog.koogelis.logging.KLoggerImpl
import ai.koog.koogelis.persistence.PersistenceStorageProviderImpl
import ai.koog.koogelis.tools.McpTransportManager
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import web.abort.AbortSignal
import web.abort.asCoroutineScope

@OptIn(ExperimentalJsExport::class)
@JsExport
class KoogAgent(val agentConfiguration: AgentConfiguration) {

    private val logger: KLogger =
        if (agentConfiguration.koogelisLogger != null) {
            KLoggerImpl(
                agentConfiguration.koogelisLogger,
                "KLoggerImpl"
            )
        } else {
            KotlinLogging.logger {}
        }

    private val mcpTransportManager = McpTransportManager(logger)

    private val defaultOllamaModel = LLModel(
        provider = LLMProvider.Ollama, id = "llama3.2:1b",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Tools
        ),
        contextLength = 131_072,
    )

    companion object KoogFactory {
        fun create(agentConfiguration: AgentConfiguration): KoogAgent = KoogAgent(agentConfiguration)
    }

    suspend fun invoke(userPrompt: String, signal: AbortSignal? = null): String {
        val llm = SupportedGoogleModels.findModel(agentConfiguration.llm.id) ?: defaultOllamaModel

        val promptExecutor = if (llm != defaultOllamaModel) {
            simpleGoogleAIExecutor(
                agentConfiguration.llm.authToken ?: throw IllegalArgumentException("API key is required")
            )
        } else {
            val llmUrl = if (agentConfiguration.llm.url.isNullOrBlank()) {
                "http://localhost:11434"
            } else {
                agentConfiguration.llm.url
            }
            simpleOllamaAIExecutor(llmUrl)
        }

        if (signal == null) {
            return createAndRun(agentConfiguration, promptExecutor, llm, userPrompt)
        }

        val result = signal.asCoroutineScope().async {
            createAndRun(agentConfiguration, promptExecutor, llm, userPrompt)
        }

        return result.await()
    }

    private suspend fun createAndRun(
        agentConfiguration: AgentConfiguration,
        promptExecutor: PromptExecutor,
        llm: LLModel,
        userPrompt: String
    ): String {
        val strategy = if (agentConfiguration.strategy == AgentConfiguration.AgentStrategy.RE_ACT) {
            reActStrategy()
        } else {
            singleRunStrategy()
        }

        val aiAgentConfig = AIAgentConfig(
            prompt = prompt(id = "prompt_id", params = mapLlmParams(agentConfiguration.llm.llmParams)) {
                system(agentConfiguration.systemPrompt.trimIndent())
            },
            model = llm,
            maxAgentIterations = agentConfiguration.maxAgentIterations
        )

        val agent = AIAgent(
            id = agentConfiguration.name,
            promptExecutor = promptExecutor,
            strategy = strategy,
            agentConfig = aiAgentConfig,
            toolRegistry = provideToolRegistry(agentConfiguration.tools),
        ) {
            if (agentConfiguration.tracingEnabled) {
                install(Tracing) {
                    addMessageProcessor(TraceFeatureMessageLogWriter(logger))
                }
            }

            if (agentConfiguration.persistenceStorageProvider != null) {
                install(Persistence) {
                    storage = PersistenceStorageProviderImpl(agentConfiguration.persistenceStorageProvider, logger)
                    enableAutomaticPersistence = true
                    //TODO: enable rollback?
                }
            }

        }

        logger.info { "An agent ${agent.id} is created" }

        try {
            val result = agent.run(userPrompt)
            logger.info { "The agent result is: $result" }
            return result
        } finally {
            // Run cleanup in NonCancellable to guarantee resource release even if
            // the coroutine was cancelled (e.g., via JS AbortSignal).
            withContext(NonCancellable) {
                mcpTransportManager.closeAll()
            }
        }
    }

    private suspend fun provideToolRegistry(
        tools: Array<AgentConfiguration.ToolDefinition>,
    ): ToolRegistry {
        if (tools.isEmpty()) return ToolRegistry.EMPTY

        val toolRegistries = mutableListOf<ToolRegistry>()
        for (toolDefinition in tools) {
            when (toolDefinition.type) {
                AgentConfiguration.ToolType.SIMPLE -> { // You can define your tools and then use them here
//                    if (toolDefinition.id == "web_scraping") {
//                        toolRegistries.add(ToolRegistry {
//                            tool(WebScrapingTool("https://nos.nl"))
//                        })
//                    }
                }

                AgentConfiguration.ToolType.MCP -> {
                    toolRegistries.add(provideMcpToolRegistry(toolDefinition))
                }
            }
        }

        return if (toolRegistries.isEmpty()) {
            ToolRegistry.EMPTY
        } else if (toolRegistries.size == 1) {
            toolRegistries.first()
        } else {
            toolRegistries.fold(toolRegistries.first()) { acc, registry -> acc + registry }
        }
    }

    private suspend fun provideMcpToolRegistry(toolDefinition: AgentConfiguration.ToolDefinition): ToolRegistry {
        if (toolDefinition.type != AgentConfiguration.ToolType.MCP) throw IllegalArgumentException("Tool type should be MCP")
        if (toolDefinition.options == null) throw IllegalArgumentException("Options field is required for MCP tool")

        val headers = toolDefinition.options.headersKeys.zip(toolDefinition.options.headersValues).toMap()

        return McpToolRegistryProvider.fromTransport(
            transport = mcpTransportManager.createTransport(
                toolDefinition.options.transportType,
                toolDefinition.options.serverUrl,
                headers
            )
        )
    }

    private fun mapLlmParams(llmParams: AgentConfiguration.LlmParams?): LLMParams {
        if (llmParams == null) return LLMParams()

        return LLMParams(
            temperature = llmParams.temperature,
            maxTokens = llmParams.maxTokens
        )
    }
}