package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.utils.io.use
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

internal const val PARALLEL_SEARCH_MCP_URL = "https://search.parallel.ai/mcp"
internal const val PARALLEL_SEARCH_USER_AGENT = "Koog-ParallelSearch-Example/1.0"

internal fun parallelSearchHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        install(SSE)
        install(UserAgent) { agent = PARALLEL_SEARCH_USER_AGENT }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }
    return if (engine == null) HttpClient(CIO, configure) else HttpClient(engine, configure)
}

internal suspend fun <T> withParallelSearchTools(
    httpClient: HttpClient,
    block: suspend (ToolRegistry) -> T
): T {
    val client = Client(clientInfo = Implementation("koog-parallel-search-example", "1.0.0"))
    try {
        client.connect(httpClient.mcpStreamableHttpTransport(PARALLEL_SEARCH_MCP_URL))
        val registry = McpToolRegistryProvider.fromClient(
            mcpClient = client,
            serverInfo = McpServerInfo(url = PARALLEL_SEARCH_MCP_URL)
        )
        return block(registry)
    } finally {
        withContext(NonCancellable) { client.close() }
    }
}

/**
 * Connects a Koog agent to Parallel's anonymous Search MCP over Streamable HTTP.
 * Web search and page fetching need no Parallel account or API key. The OpenAI
 * model still requires OPENAI_API_KEY. All clients are closed after the run.
 */
fun main(): Unit = runBlocking {
    val openAIApiKey = System.getenv("OPENAI_API_KEY")
        ?: error("OPENAI_API_KEY environment variable is not set")

    parallelSearchHttpClient().use { httpClient ->
        withTimeout(5.minutes) {
            withParallelSearchTools(httpClient) { registry ->
                simpleOpenAIExecutor(openAIApiKey).use { executor ->
                    AIAgent(
                        promptExecutor = executor,
                        llmModel = OpenAIModels.Chat.GPT4o,
                        toolRegistry = registry,
                        systemPrompt = "Use web_search to find sources and cite their URLs. " +
                            "Use web_fetch only when search excerpts are insufficient. " +
                            "Reuse one session_id across related searches and fetches.",
                        maxIterations = 10
                    ).use { agent ->
                        println(agent.run("Find Koog's official documentation and explain its MCP support."))
                    }
                }
            }
        }
    }
}
