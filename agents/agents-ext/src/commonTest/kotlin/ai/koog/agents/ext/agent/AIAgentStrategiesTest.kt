package ai.koog.agents.ext.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.JsonStructuredData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AIAgentStrategiesTest {
    private val defaultName = "re_act"

    @Test
    fun testChatStrategyDefaultName() = runTest {
        val strategy = chatAgentStrategy()
        assertEquals("chat", strategy.name)
    }

    @Test
    fun testReActStrategyDefaultName() = runTest {
        val strategy = reActStrategy()
        assertEquals(defaultName, strategy.name)
    }

    @Test
    fun testReActStrategyCustomName() = runTest {
        val customName = "custom_$defaultName"
        val strategy = reActStrategy(name = customName)
        assertEquals(customName, strategy.name)
    }

    @Test
    fun testReActStrategyWithCustomReasoningInterval() = runTest {
        val strategy = reActStrategy(reasoningInterval = 2)
        assertEquals(defaultName, strategy.name)
    }

    @Test
    fun testReActStrategyInvalidReasoningInterval() = runTest {
        assertFailsWith<IllegalArgumentException> {
            reActStrategy(reasoningInterval = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            reActStrategy(reasoningInterval = -1)
        }
    }

    @Test
    fun testStructuredOutputWithToolsStrategyDefaultName() = runTest {
        @Serializable
        data class TestOutput(
            @property:LLMDescription("Test field")
            val field: String
        )

        val structure = JsonStructuredData.createJsonStructure<TestOutput>()
        val config = StructuredOutputConfig(
            default = StructuredOutput.Manual(structure)
        )

        val strategy = structuredOutputWithToolsStrategy<String, TestOutput>(config) { input ->
            "Processed: $input"
        }

        assertEquals("structured_output_with_tools_strategy", strategy.name)
    }

    @Test
    fun testStructuredOutputWithToolsStrategyWithParallelTools() = runTest {
        @Serializable
        data class TestResult(
            @property:LLMDescription("Result message")
            val message: String,
            @property:LLMDescription("Success status")
            val success: Boolean
        )

        val structure = JsonStructuredData.createJsonStructure<TestResult>()
        val config = StructuredOutputConfig(
            default = StructuredOutput.Manual(structure)
        )

        val strategyWithParallel = structuredOutputWithToolsStrategy<String, TestResult>(
            config = config,
            parallelTools = true
        ) { input ->
            "Processing with parallel tools: $input"
        }

        assertNotNull(strategyWithParallel)
        assertEquals("structured_output_with_tools_strategy", strategyWithParallel.name)

        val strategyWithoutParallel = structuredOutputWithToolsStrategy<String, TestResult>(
            config = config,
            parallelTools = false
        ) { input ->
            "Processing without parallel tools: $input"
        }

        assertNotNull(strategyWithoutParallel)
        assertEquals("structured_output_with_tools_strategy", strategyWithoutParallel.name)
    }

    @Test
    fun testStructuredOutputWithToolsStrategyComplexTypes() = runTest {
        @Serializable
        data class Address(
            @property:LLMDescription("Street address")
            val street: String,
            @property:LLMDescription("City")
            val city: String,
            @property:LLMDescription("ZIP code")
            val zipCode: String
        )

        @Serializable
        data class ComplexOutput(
            @property:LLMDescription("User ID")
            val id: Int,
            @property:LLMDescription("User name")
            val name: String,
            @property:LLMDescription("User addresses")
            val addresses: List<Address>,
            @property:LLMDescription("User preferences")
            val preferences: Map<String, String>? = null
        )

        @Serializable
        data class ComplexInput(
            val userId: String,
            val requestType: String
        )

        val structure = JsonStructuredData.createJsonStructure<ComplexOutput>()
        val config = StructuredOutputConfig(
            default = StructuredOutput.Manual(structure)
        )

        val strategy = structuredOutputWithToolsStrategy<ComplexInput, ComplexOutput>(config) { input ->
            "Fetch user data for ID: ${input.userId}, type: ${input.requestType}"
        }

        assertNotNull(strategy)
        assertEquals("structured_output_with_tools_strategy", strategy.name)
    }

    @Test
    fun testStructuredOutputWithToolsStrategyDifferentConfigs() = runTest {
        @Serializable
        data class SimpleOutput(
            @property:LLMDescription("Value")
            val value: String
        )

        val manualStructure = JsonStructuredData.createJsonStructure<SimpleOutput>()
        val nativeStructure = JsonStructuredData.createJsonStructure<SimpleOutput>()

        // Test with manual mode
        val manualConfig = StructuredOutputConfig(
            default = StructuredOutput.Manual(manualStructure)
        )

        val manualStrategy = structuredOutputWithToolsStrategy<String, SimpleOutput>(manualConfig) { input ->
            "Manual mode: $input"
        }

        assertNotNull(manualStrategy)

        // Test with native mode
        val nativeConfig = StructuredOutputConfig(
            default = StructuredOutput.Native(nativeStructure)
        )

        val nativeStrategy = structuredOutputWithToolsStrategy<String, SimpleOutput>(nativeConfig) { input ->
            "Native mode: $input"
        }

        assertNotNull(nativeStrategy)

        // Test with both modes in config
        val mixedConfig = StructuredOutputConfig(
            default = StructuredOutput.Manual(manualStructure),
            byProvider = mapOf(
                ai.koog.prompt.llm.LLMProvider.OpenAI to StructuredOutput.Native(nativeStructure)
            )
        )

        val mixedStrategy = structuredOutputWithToolsStrategy<String, SimpleOutput>(mixedConfig) { input ->
            "Mixed mode: $input"
        }

        assertNotNull(mixedStrategy)
    }
}
