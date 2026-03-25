package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.message.CacheControl
import aws.sdk.kotlin.services.bedrockruntime.model.CachePointType
import aws.sdk.kotlin.services.bedrockruntime.model.CacheTtl
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.SystemContentBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import aws.sdk.kotlin.services.bedrockruntime.model.Tool as BedrockTool

class BedrockCacheControlTest {

    private val model = BedrockModels.AnthropicClaude4Sonnet

    private fun converseRequest(prompt: Prompt, tools: List<ToolDescriptor> = emptyList()) =
        BedrockConverseConverters.createConverseRequest(prompt, model, tools)

    // --- System ---

    @Test
    fun testSystemWithCacheControlDefault() {
        val prompt = Prompt.build("test") { system("You are helpful.", CacheControl.Bedrock.Default) }
        val system = converseRequest(prompt).system!!
        assertEquals(2, system.size)
        assertIs<SystemContentBlock.Text>(system[0])
        val cp = assertIs<SystemContentBlock.CachePoint>(system[1])
        assertEquals(CachePointType.Default, cp.value.type)
        assertNull(cp.value.ttl)
    }

    @Test
    fun testSystemWithoutCacheControl() {
        val prompt = Prompt.build("test") { system("Hello.") }
        val system = converseRequest(prompt).system!!
        assertEquals(1, system.size)
        assertIs<SystemContentBlock.Text>(system[0])
    }

    @Test
    fun testSystemCacheControlFiveMinutes() {
        val prompt = Prompt.build("test") { system("Cached.", CacheControl.Bedrock.FiveMinutes) }
        val cp = assertIs<SystemContentBlock.CachePoint>(converseRequest(prompt).system!![1])
        assertNotNull(cp.value.ttl)
        assertEquals(CacheTtl.FiveMinutes, cp.value.ttl)
    }

    @Test
    fun testSystemCacheControlOneHour() {
        val prompt = Prompt.build("test") { system("Cached.", CacheControl.Bedrock.OneHour) }
        val cp = assertIs<SystemContentBlock.CachePoint>(converseRequest(prompt).system!![1])
        assertNotNull(cp.value.ttl)
        assertEquals(CacheTtl.OneHour, cp.value.ttl)
    }

    // --- Tools ---

    @Test
    fun testToolWithCacheControl() {
        val tool = ToolDescriptor(
            name = "search",
            description = "Search",
            requiredParameters = listOf(ToolParameterDescriptor("q", "query", ToolParameterType.String)),
            cacheControl = CacheControl.Bedrock.Default
        )
        val prompt = Prompt.build("test") { user("go") }
        val tools = converseRequest(prompt, listOf(tool)).toolConfig!!.tools!!
        assertEquals(2, tools.size)
        assertIs<BedrockTool.ToolSpec>(tools[0])
        assertIs<BedrockTool.CachePoint>(tools[1])
    }

    @Test
    fun testToolWithoutCacheControl() {
        val tool = ToolDescriptor(
            name = "search",
            description = "Search",
            requiredParameters = listOf(ToolParameterDescriptor("q", "query", ToolParameterType.String))
        )
        val prompt = Prompt.build("test") { user("go") }
        val tools = converseRequest(prompt, listOf(tool)).toolConfig!!.tools!!
        assertEquals(1, tools.size)
        assertIs<BedrockTool.ToolSpec>(tools[0])
    }

    // --- User ---

    @Test
    fun testUserWithCacheControl() {
        val prompt = Prompt.build("test") {
            user(listOf(ai.koog.prompt.message.ContentPart.Text("Hello")), CacheControl.Bedrock.Default)
        }
        val content = converseRequest(prompt).messages!![0].content!!
        assertEquals(2, content.size)
        assertIs<ContentBlock.Text>(content[0])
        assertIs<ContentBlock.CachePoint>(content[1])
    }

    @Test
    fun testUserWithoutCacheControl() {
        val prompt = Prompt.build("test") { user("Hello") }
        val content = converseRequest(prompt).messages!![0].content!!
        assertEquals(1, content.size)
        assertIs<ContentBlock.Text>(content[0])
    }

    // --- Assistant ---

    @Test
    fun testAssistantWithCacheControl() {
        val prompt = Prompt.build("test") {
            user("Hi")
            assistant("Hello!", CacheControl.Bedrock.Default)
        }
        val content = converseRequest(prompt).messages!![1].content!!
        assertEquals(2, content.size)
        assertIs<ContentBlock.Text>(content[0])
        assertIs<ContentBlock.CachePoint>(content[1])
    }

    @Test
    fun testAssistantWithoutCacheControl() {
        val prompt = Prompt.build("test") {
            user("Hi")
            assistant("Hello!")
        }
        val content = converseRequest(prompt).messages!![1].content!!
        assertEquals(1, content.size)
        assertIs<ContentBlock.Text>(content[0])
    }
}
