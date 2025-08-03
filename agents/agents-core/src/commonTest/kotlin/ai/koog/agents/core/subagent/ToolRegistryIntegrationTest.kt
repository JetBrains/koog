package ai.koog.agents.core.subagent

import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for streamlined agent safety integration with ToolRegistry.
 * 
 * This tests the simplified architecture where agents use asSafeTool()
 * to be registered in ToolRegistry with safety mechanisms.
 */
class ToolRegistryIntegrationTest {

    // Sample traditional tool for comparison
    private class SampleTool : Tool<SampleTool.Args, SampleTool.Result>() {
        @Serializable
        data class Args(val value: String) : ToolArgs
        
        @Serializable  
        data class Result(val processed: String) : ToolResult {
            override fun toStringDefault(): String = "SampleResult(processed=$processed)"
        }
        
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "sample-tool",
            description = "A sample traditional tool"
        )
        
        override val argsSerializer: KSerializer<Args> = Args.serializer()
        
        override suspend fun execute(args: Args): Result {
            return Result("Processed: ${args.value}")
        }
    }

    // Test agent for safe tool creation
    private class TestAgent(private val prefix: String) : AIAgentBase<String, String> {
        override val id: String = "test-agent"
        
        override suspend fun run(agentInput: String): String {
            return "$prefix: $agentInput"
        }
    }

    @Test
    fun `ToolRegistry should accept both traditional tools and safe agent tools`() {
        // Given
        val agent = TestAgent("SafeAgent")
        val traditionalTool = SampleTool()
        
        val safeAgentTool = agent.asSafeTool(
            agentName = "safe-agent-tool",
            agentDescription = "Agent wrapped with safety mechanisms",
            inputDescriptor = ToolParameterDescriptor(
                name = "input",
                description = "Input text",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.safe()
        )
        
        // When
        val registry = ToolRegistry {
            tool(traditionalTool)
            tool(safeAgentTool)
        }
        
        // Then
        assertEquals(2, registry.tools.size)
        assertNotNull(registry.tools.find { it.descriptor.name == "sample-tool" })
        assertNotNull(registry.tools.find { it.descriptor.name == "safe-agent-tool" })
    }

    @Test
    fun `safe agent tool should have correct descriptor`() {
        // Given
        val agent = TestAgent("TestAgent")
        
        // When
        val safeTool = agent.asSafeTool(
            agentName = "test-safe-tool",
            agentDescription = "A test agent with safety policies",
            inputDescriptor = ToolParameterDescriptor(
                name = "input",
                description = "Input text",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.safe(maxDepth = 3)
        )
        
        // Then
        assertEquals("test-safe-tool", safeTool.descriptor.name)
        assertEquals("A test agent with safety policies", safeTool.descriptor.description)
        assertEquals(1, safeTool.descriptor.requiredParameters.size)
        assertEquals("input", safeTool.descriptor.requiredParameters[0].name)
    }

    @Test
    fun `safe agent tool should integrate seamlessly with existing tool patterns`() {
        // Given
        val dataAgent = TestAgent("DataProcessor")
        val analysisAgent = TestAgent("AnalysisEngine")
        
        val dataProcessorTool = dataAgent.asSafeTool(
            agentName = "data-processor",
            agentDescription = "Processes raw data with safety limits",
            inputDescriptor = ToolParameterDescriptor(
                name = "data", 
                description = "Data to process",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.safe(maxDepth = 2)
        )
        
        val analysisEngineTool = analysisAgent.asSafeTool(
            agentName = "analysis-engine", 
            agentDescription = "Analyzes processed data with trusted policies",
            inputDescriptor = ToolParameterDescriptor(
                name = "processedData",
                description = "Processed data to analyze", 
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.trusted(maxDepth = 5)
        )
        
        // When
        val registry = ToolRegistry {
            tool(SampleTool()) // Traditional tool
            tool(dataProcessorTool) // Safe agent tool (conservative)
            tool(analysisEngineTool) // Safe agent tool (trusted)
        }
        
        // Then
        assertEquals(3, registry.tools.size)
        
        // Verify each tool type is registered correctly
        val tools = registry.tools.associateBy { it.descriptor.name }
        assertNotNull(tools["sample-tool"])
        assertNotNull(tools["data-processor"])
        assertNotNull(tools["analysis-engine"])
    }

    @Test
    fun `convenience policy functions should work in tool creation`() {
        // Given
        val agent = TestAgent("PolicyTest")
        
        // When - using convenience functions
        val safeTool = agent.asSafeTool(
            agentName = "safe-policy-tool",
            agentDescription = "Tool using convenience safe policy",
            inputDescriptor = ToolParameterDescriptor(
                name = "input",
                description = "Input text",
                type = ToolParameterType.String
            ),
            safetyPolicy = safePolicy(maxDepth = 1, maxChildren = 2)
        )
        
        val trustedTool = agent.asSafeTool(
            agentName = "trusted-policy-tool", 
            agentDescription = "Tool using convenience trusted policy",
            inputDescriptor = ToolParameterDescriptor(
                name = "input",
                description = "Input text",
                type = ToolParameterType.String
            ),
            safetyPolicy = trustedPolicy(maxDepth = 7, maxChildren = 15)
        )
        
        // Then
        assertEquals("safe-policy-tool", safeTool.descriptor.name)
        assertEquals("trusted-policy-tool", trustedTool.descriptor.name)
        
        // Both should be valid tools that can be registered
        val registry = ToolRegistry {
            tool(safeTool)
            tool(trustedTool)
        }
        
        assertEquals(2, registry.tools.size)
    }
}