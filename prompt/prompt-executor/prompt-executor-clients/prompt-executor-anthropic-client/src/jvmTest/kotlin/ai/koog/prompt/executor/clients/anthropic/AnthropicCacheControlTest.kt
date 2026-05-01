package ai.koog.prompt.executor.clients.anthropic

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.anthropic.models.CacheTtl
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.time.KoogClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicCacheControl as AnthropicCacheControlBlock

class AnthropicCacheControlTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = AnthropicLLMClient(apiKey = "test-key")
    private val model = AnthropicModels.Sonnet_4
    private val metaInfo = RequestMetaInfo.create(KoogClock.System)

    // --- toAnthropicCacheControl conversion ---

    @Test
    fun testDefaultCacheControlConvertsToEphemeralWithNoTtl() {
        val cacheControl: AnthropicCacheControl = AnthropicCacheControl.Default
        val result = client.run { cacheControl.toAnthropicCacheControl() }
        val ephemeral = result as AnthropicCacheControlBlock.Ephemeral
        assertNull(ephemeral.ttl)
    }

    @Test
    fun testOneHourCacheControlConvertsToEphemeralWithOneHourTtl() {
        val cacheControl: AnthropicCacheControl = AnthropicCacheControl.OneHour
        val result = client.run { cacheControl.toAnthropicCacheControl() }
        val ephemeral = result as AnthropicCacheControlBlock.Ephemeral
        assertEquals(CacheTtl.OneHour, ephemeral.ttl)
    }

    @Test
    fun testNonAnthropicCacheControlThrowsException() {
        val fakeCacheControl = object : ai.koog.prompt.message.CacheControl {}
        assertFailsWith<IllegalStateException> {
            client.run { fakeCacheControl.toAnthropicCacheControl() }
        }
    }

    // --- System messages ---

    @Test
    fun testSystemMessageWithDefaultCacheControlHasEphemeralCacheControlWithNoTtlInJson() {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant.", AnthropicCacheControl.Default)
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val system = request["system"]?.jsonArray
        assertNotNull(system)
        assertEquals(1, system.size)
        val systemMsg = system[0].jsonObject
        val cacheControl = systemMsg["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNull(cacheControl["ttl"])
    }

    @Test
    fun testSystemMessageWithOneHourCacheControlHasEphemeralCacheControlInJson() {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant.", AnthropicCacheControl.OneHour)
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val system = request["system"]?.jsonArray
        assertNotNull(system)
        val systemMsg = system[0].jsonObject
        val cacheControl = systemMsg["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNotNull(cacheControl["ttl"])
    }

    @Test
    fun testSystemMessageWithoutCacheControlHasNoCacheControlInJson() {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant.")
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val system = request["system"]?.jsonArray
        assertNotNull(system)
        val systemMsg = system[0].jsonObject
        assertNull(systemMsg["cache_control"])
    }

    // --- User messages ---

    @Test
    fun testUserMessageWithOneHourCacheControlHasCacheControlInJson() {
        val prompt = Prompt.build("test") {
            user(listOf(ContentPart.Text("Hello")), AnthropicCacheControl.OneHour)
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val userMsg = messages[0].jsonObject
        val content = userMsg["content"]?.jsonArray
        assertNotNull(content)
        val cacheControl = content[0].jsonObject["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNotNull(cacheControl["ttl"])
    }

    @Test
    fun testUserMessageWithDefaultCacheControlHasEphemeralCacheControlWithNoTtlInJson() {
        val prompt = Prompt.build("test") {
            user(listOf(ContentPart.Text("Hello")), AnthropicCacheControl.Default)
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val userMsg = messages[0].jsonObject
        val content = userMsg["content"]?.jsonArray
        assertNotNull(content)
        val cacheControl = content[0].jsonObject["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNull(cacheControl["ttl"])
    }

    @Test
    fun testUserMessageWithoutCacheControlHasNoCacheControlInJson() {
        val prompt = Prompt.build("test") {
            user("Hello")
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val userMsg = messages[0].jsonObject
        val content = userMsg["content"]?.jsonArray
        assertNotNull(content)
        assertNull(content[0].jsonObject["cache_control"])
    }

    // --- Assistant messages ---

    @Test
    fun testAssistantMessageWithOneHourCacheControlHasCacheControlInJson() {
        val prompt = Prompt.build("test") {
            user("Hi")
            assistant("Hello!", AnthropicCacheControl.OneHour)
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        // Second message is the assistant message
        val assistantMsg = messages[1].jsonObject
        assertEquals("assistant", assistantMsg["role"]?.jsonPrimitive?.content)
        val content = assistantMsg["content"]?.jsonArray
        assertNotNull(content)
        val cacheControl = content[0].jsonObject["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun testAssistantMessageWithDefaultCacheControlHasEphemeralCacheControlWithNoTtlInJson() {
        val prompt = Prompt.build("test") {
            user("Hi")
            assistant("Hello!", AnthropicCacheControl.Default)
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val assistantMsg = messages[1].jsonObject
        assertEquals("assistant", assistantMsg["role"]?.jsonPrimitive?.content)
        val content = assistantMsg["content"]?.jsonArray
        assertNotNull(content)
        val cacheControl = content[0].jsonObject["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNull(cacheControl["ttl"])
    }

    @Test
    fun testAssistantMessageWithoutCacheControlHasNoCacheControlInJson() {
        val prompt = Prompt.build("test") {
            user("Hi")
            assistant("Hello!")
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val assistantMsg = messages[1].jsonObject
        val content = assistantMsg["content"]?.jsonArray
        assertNotNull(content)
        assertNull(content[0].jsonObject["cache_control"])
    }

    // --- Tool result messages ---

    @Test
    fun testToolResultWithOneHourCacheControlHasCacheControlInJson() {
        val prompt = Prompt.build("test") {
            messages(
                listOf(
                    ai.koog.prompt.message.Message.Tool.Result(
                        id = "tool-id",
                        tool = "my_tool",
                        content = "result",
                        metaInfo = metaInfo,
                        cacheControl = AnthropicCacheControl.OneHour
                    )
                )
            )
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val toolResultMsg = messages[0].jsonObject
        assertEquals("user", toolResultMsg["role"]?.jsonPrimitive?.content)
        val content = toolResultMsg["content"]?.jsonArray
        assertNotNull(content)
        val cacheControl = content[0].jsonObject["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun testToolResultWithoutCacheControlHasNoCacheControlInJson() {
        val prompt = Prompt.build("test") {
            messages(
                listOf(
                    ai.koog.prompt.message.Message.Tool.Result(
                        id = "tool-id",
                        tool = "my_tool",
                        content = "result",
                        metaInfo = metaInfo
                    )
                )
            )
        }
        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val messages = request["messages"]?.jsonArray
        assertNotNull(messages)
        val toolResultMsg = messages[0].jsonObject
        val content = toolResultMsg["content"]?.jsonArray
        assertNotNull(content)
        assertNull(content[0].jsonObject["cache_control"])
    }

    // --- Tool definitions ---

    @Test
    fun testToolWithOneHourCacheControlHasCacheControlInJson() {
        val tool = ToolDescriptor(
            name = "search",
            description = "Search the web",
            requiredParameters = listOf(
                ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
            ),
            cacheControl = AnthropicCacheControl.OneHour
        )
        val prompt = Prompt.build("test") { user("go") }

        val requestJson = client.createAnthropicRequest(prompt, listOf(tool), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val tools = request["tools"]?.jsonArray
        assertNotNull(tools)
        assertEquals(1, tools.size)
        val toolObj = tools[0].jsonObject
        val cacheControl = toolObj["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun testToolWithDefaultCacheControlHasEphemeralCacheControlWithNoTtlInJson() {
        val tool = ToolDescriptor(
            name = "search",
            description = "Search the web",
            requiredParameters = listOf(
                ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
            ),
            cacheControl = AnthropicCacheControl.Default
        )
        val prompt = Prompt.build("test") { user("go") }

        val requestJson = client.createAnthropicRequest(prompt, listOf(tool), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val tools = request["tools"]?.jsonArray
        assertNotNull(tools)
        assertEquals(1, tools.size)
        val toolObj = tools[0].jsonObject
        val cacheControl = toolObj["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNull(cacheControl["ttl"])
    }

    @Test
    fun testToolWithoutCacheControlHasNoCacheControlInJson() {
        val tool = ToolDescriptor(
            name = "search",
            description = "Search the web",
            requiredParameters = listOf(
                ToolParameterDescriptor("query", "Search query", ToolParameterType.String)
            )
        )
        val prompt = Prompt.build("test") { user("go") }

        val requestJson = client.createAnthropicRequest(prompt, listOf(tool), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val tools = request["tools"]?.jsonArray
        assertNotNull(tools)
        assertEquals(1, tools.size)
        val toolObj = tools[0].jsonObject
        assertNull(toolObj["cache_control"])
    }

    @Test
    fun testLastToolInListReceivingCacheControlHasCacheControlInJson() {
        val tool1 = ToolDescriptor(
            name = "tool1",
            description = "First tool",
            requiredParameters = listOf(ToolParameterDescriptor("p", "param", ToolParameterType.String))
        )
        val tool2 = ToolDescriptor(
            name = "tool2",
            description = "Second tool with cache control",
            requiredParameters = listOf(ToolParameterDescriptor("p", "param", ToolParameterType.String)),
            cacheControl = AnthropicCacheControl.OneHour
        )
        val prompt = Prompt.build("test") { user("go") }

        val requestJson = client.createAnthropicRequest(prompt, listOf(tool1, tool2), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val tools = request["tools"]?.jsonArray
        assertNotNull(tools)
        assertEquals(2, tools.size)
        assertNull(tools[0].jsonObject["cache_control"])
        val cacheControl = tools[1].jsonObject["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
    }

    // --- Automatic (request-level) cache control via AnthropicParams ---

    @Test
    fun testAutomaticCacheControlWithEphemeralAppearsAtRequestLevel() {
        val params = AnthropicParams(cacheControl = AnthropicCacheControl.Default)
        val prompt = Prompt.build("test", params = params) {
            system("You are a helpful assistant.")
            user("Hello")
        }

        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val cacheControl = request["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNull(cacheControl["ttl"])
    }

    @Test
    fun testAutomaticCacheControlWithEphemeralOneHourAppearsAtRequestLevel() {
        val params = AnthropicParams(cacheControl = AnthropicCacheControl.OneHour)
        val prompt = Prompt.build("test", params = params) {
            system("You are a helpful assistant.")
            user("Hello")
        }

        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        val cacheControl = request["cache_control"]?.jsonObject
        assertNotNull(cacheControl)
        assertEquals("ephemeral", cacheControl["type"]?.jsonPrimitive?.content)
        assertNotNull(cacheControl["ttl"])
    }

    @Test
    fun testNoAutomaticCacheControlResultsInNoCacheControlAtRequestLevel() {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant.")
            user("Hello")
        }

        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        assertNull(request["cache_control"])
    }

    // --- Combination: block-level and request-level cache control ---

    @Test
    fun testBlockLevelAndRequestLevelCacheControlCanCoexist() {
        val params = AnthropicParams(cacheControl = AnthropicCacheControl.Default)
        val prompt = Prompt.build("test", params = params) {
            system("Long system prompt.", AnthropicCacheControl.OneHour)
            user("Hello")
        }

        val requestJson = client.createAnthropicRequest(prompt, emptyList(), model, false)
        val request = json.parseToJsonElement(requestJson).jsonObject

        // Request-level cache control
        val requestCacheControl = request["cache_control"]?.jsonObject
        assertNotNull(requestCacheControl)
        assertEquals("ephemeral", requestCacheControl["type"]?.jsonPrimitive?.content)

        // Block-level cache control on system message
        val system = request["system"]?.jsonArray
        assertNotNull(system)
        val systemCacheControl = system[0].jsonObject["cache_control"]?.jsonObject
        assertNotNull(systemCacheControl)
        assertEquals("ephemeral", systemCacheControl["type"]?.jsonPrimitive?.content)
    }
}
