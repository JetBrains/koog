package ai.koog.agents.example.mcp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.utils.io.use
import ai.koog.serialization.kotlinx.toKoogJSONObject
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalAgentsApi::class)
class ParallelSearchMcpClientTest {
    @Test
    fun testAgentDispatchesSearchAndFetchWithoutParallelCredentials() = runBlocking {
        val methods = mutableListOf<String>()
        val engine = MockEngine { request ->
            assertEquals(PARALLEL_SEARCH_MCP_URL, request.url.toString())
            assertEquals(PARALLEL_SEARCH_USER_AGENT, request.headers[HttpHeaders.UserAgent])
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            if (request.method != HttpMethod.Post) {
                respond("", HttpStatusCode.MethodNotAllowed)
            } else {
                val rpc = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                val method = rpc.getValue("method").jsonPrimitive.content
                methods += method
                if ("id" !in rpc) {
                    respond("", HttpStatusCode.Accepted)
                } else {
                    val result = when (method) {
                        "initialize" -> Json.parseToJsonElement(
                            """{"protocolVersion":"2025-03-26","capabilities":{"tools":{}},"serverInfo":{"name":"fixture","version":"1.0"}}"""
                        )
                        "tools/list" -> Json.parseToJsonElement(
                            """{"tools":[{"name":"web_search","description":"Search the web","inputSchema":{"type":"object","properties":{"objective":{"type":"string"},"search_queries":{"type":"array","items":{"type":"string"}}},"required":["objective","search_queries"]}},{"name":"web_fetch","description":"Fetch a page","inputSchema":{"type":"object","properties":{"urls":{"type":"array","items":{"type":"string"}}},"required":["urls"]}}]}"""
                        )
                        "tools/call" -> {
                            val params = rpc.getValue("params").jsonObject
                            when (params.getValue("name").jsonPrimitive.content) {
                                "web_search" -> {
                                    assertEquals("Koog MCP support", params.getValue("arguments").jsonObject["objective"]?.jsonPrimitive?.content)
                                    Json.parseToJsonElement("""{"content":[{"type":"text","text":"Search result: https://docs.koog.ai/"}]}""")
                                }
                                "web_fetch" -> {
                                    assertEquals("[\"https://docs.koog.ai/\"]", params.getValue("arguments").jsonObject["urls"].toString())
                                    Json.parseToJsonElement("""{"content":[{"type":"text","text":"Koog supports Model Context Protocol tools."}]}""")
                                }
                                else -> error("Unexpected tool")
                            }
                        }
                        else -> error("Unexpected MCP method: $method")
                    }
                    respond(
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", rpc.getValue("id"))
                            put("result", result)
                        }.toString(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        parallelSearchHttpClient(engine).use { httpClient ->
            withParallelSearchTools(httpClient) { registry ->
                assertEquals(setOf("web_search", "web_fetch"), registry.tools.map { it.name }.toSet())
                val search = registry.getTool("web_search") as McpTool
                val fetch = registry.getTool("web_fetch") as McpTool
                val executor = getMockExecutor {
                    mockLLMToolCall(search, buildJsonObject {
                        put("objective", "Koog MCP support")
                        put("search_queries", buildJsonArray { add(JsonPrimitive("Koog MCP support documentation")) })
                    }.toKoogJSONObject()) onRequestEquals "Find Koog docs"
                    mockLLMToolCall(fetch, buildJsonObject {
                        put("urls", buildJsonArray { add(JsonPrimitive("https://docs.koog.ai/")) })
                    }.toKoogJSONObject()) onRequestContains "Search result:"
                    mockLLMAnswer("Koog supports MCP tools: https://docs.koog.ai/") onRequestContains "Koog supports Model Context Protocol"
                }
                executor.use {
                    AIAgent(
                        promptExecutor = executor,
                        llmModel = OpenAIModels.Chat.GPT4o,
                        toolRegistry = registry,
                        maxIterations = 5
                    ).use { agent ->
                        assertEquals("Koog supports MCP tools: https://docs.koog.ai/", agent.run("Find Koog docs"))
                    }
                }
            }
        }
        assertEquals(2, methods.count { it == "tools/call" })
        assertTrue("initialize" in methods && "tools/list" in methods)
    }

    @Test
    fun testConnectionFailurePropagates() = runBlocking {
        var blockInvoked = false
        parallelSearchHttpClient(MockEngine { respond("Unavailable", HttpStatusCode.ServiceUnavailable) }).use { httpClient ->
            assertFailsWith<Exception> {
                withParallelSearchTools(httpClient) { blockInvoked = true }
            }
        }
        assertFalse(blockInvoked)
    }
}
