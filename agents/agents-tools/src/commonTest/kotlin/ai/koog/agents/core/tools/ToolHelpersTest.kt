package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(InternalAgentToolsApi::class)
class ToolHelpersTest {

    private val toolCapableModel = LLModel(
        provider = LLMProvider.Google,
        id = "tool-capable",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools)
    )

    private val noToolModel = LLModel(
        provider = LLMProvider.Google,
        id = "no-tools",
        capabilities = listOf(LLMCapability.Completion)
    )

    private val sampleTool = ToolDescriptor(
        name = "get_weather",
        description = "Get weather",
        requiredParameters = listOf(
            ToolParameterDescriptor("city", "City name", ToolParameterType.String)
        )
    )

    private val tools = listOf(sampleTool)

    @Test
    fun modelSupportsTools_passesToolsThrough() {
        val result = tools.resolveEffectiveTools(toolCapableModel, LLMParams.ToolChoice.Auto)

        result shouldBeSameInstanceAs tools
    }

    @Test
    fun modelSupportsTools_passesToolsThroughWithNullChoice() {
        val result = tools.resolveEffectiveTools(toolCapableModel, null)

        result shouldBeSameInstanceAs tools
    }

    @Test
    fun modelSupportsTools_passesToolsThroughWithRequiredChoice() {
        val result = tools.resolveEffectiveTools(toolCapableModel, LLMParams.ToolChoice.Required)

        result shouldBeSameInstanceAs tools
    }

    @Test
    fun modelSupportsTools_passesToolsThroughWithNamedChoice() {
        val result = tools.resolveEffectiveTools(toolCapableModel, LLMParams.ToolChoice.Named("get_weather"))

        result shouldBeSameInstanceAs tools
    }

    @Test
    fun emptyToolsList_returnsEmptyRegardlessOfModel() {
        val result = emptyList<ToolDescriptor>().resolveEffectiveTools(noToolModel, LLMParams.ToolChoice.Auto)

        result.shouldBeEmpty()
    }

    @Test
    fun emptyToolsList_returnsEmptyEvenWithRequiredChoice() {
        val result = emptyList<ToolDescriptor>().resolveEffectiveTools(noToolModel, LLMParams.ToolChoice.Required)

        result.shouldBeEmpty()
    }

    @Test
    fun modelNoTools_nullChoice_silentlyDrops() {
        val result = tools.resolveEffectiveTools(noToolModel, null)

        result.shouldBeEmpty()
    }

    @Test
    fun modelNoTools_autoChoice_silentlyDrops() {
        val result = tools.resolveEffectiveTools(noToolModel, LLMParams.ToolChoice.Auto)

        result.shouldBeEmpty()
    }

    @Test
    fun modelNoTools_noneChoice_silentlyDrops() {
        val result = tools.resolveEffectiveTools(noToolModel, LLMParams.ToolChoice.None)

        result.shouldBeEmpty()
    }

    @Test
    fun modelNoTools_requiredChoice_rejects() {
        val error = assertFailsWith<IllegalArgumentException> {
            tools.resolveEffectiveTools(noToolModel, LLMParams.ToolChoice.Required)
        }

        error.message shouldContain "no-tools"
        error.message shouldContain "Required"
    }

    @Test
    fun modelNoTools_namedChoice_rejects() {
        val error = assertFailsWith<IllegalArgumentException> {
            tools.resolveEffectiveTools(noToolModel, LLMParams.ToolChoice.Named("get_weather"))
        }

        error.message shouldContain "no-tools"
        error.message shouldContain "Named"
    }
}
