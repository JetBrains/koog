package ai.koog.agents.core.subagent

import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the streamlined safe agent execution system.
 */
class SafeAgentExecutionTest {

    @Test
    fun `AgentSafetyPolicy should have correct defaults`() {
        // Given
        val policy = SafetyPolicies.safe()
        
        // Then
        assertEquals(2, policy.maxDepth)
        assertEquals(3, policy.maxChildrenPerCall)
        assertEquals(30.seconds, policy.timeout)
        assertTrue(policy.allowedChildren.isEmpty())
    }

    @Test
    fun `SafetyPolicies should create correct safe policy`() {
        // Given
        val policy = SafetyPolicies.safe(maxDepth = 3, maxChildren = 5, timeout = 45.seconds)
        
        // Then
        assertEquals(3, policy.maxDepth)
        assertEquals(5, policy.maxChildrenPerCall)
        assertEquals(45.seconds, policy.timeout)
    }

    @Test
    fun `SafetyPolicies should create correct trusted policy`() {
        // Given
        val policy = SafetyPolicies.trusted()
        
        // Then
        assertEquals(5, policy.maxDepth)
        assertEquals(10, policy.maxChildrenPerCall)
        assertEquals(120.seconds, policy.timeout)
    }

    @Test
    fun `asSafeTool should create tool with safety wrapper`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "test-agent"
            
            override suspend fun run(agentInput: String): String {
                return "Processed: $agentInput"
            }
        }
        
        // When
        val safeTool = testAgent.asSafeTool(
            agentName = "test-tool",
            agentDescription = "A test tool with safety mechanisms",
            inputDescriptor = ToolParameterDescriptor(
                name = "input",
                description = "Input text to process",
                type = ToolParameterType.String
            ),
            safetyPolicy = SafetyPolicies.safe(maxDepth = 1)
        )
        
        // Then
        assertEquals("test-tool", safeTool.descriptor.name)
        assertEquals("A test tool with safety mechanisms", safeTool.descriptor.description)
    }

    @Test
    fun `convenience functions should work correctly`() {
        // Test safePolicy function
        val safe = safePolicy(maxDepth = 1, maxChildren = 2, timeout = 15.seconds)
        assertEquals(1, safe.maxDepth)
        assertEquals(2, safe.maxChildrenPerCall)
        assertEquals(15.seconds, safe.timeout)
        
        // Test trustedPolicy function  
        val trusted = trustedPolicy(maxDepth = 7, maxChildren = 15, timeout = 180.seconds)
        assertEquals(7, trusted.maxDepth)
        assertEquals(15, trusted.maxChildrenPerCall)
        assertEquals(180.seconds, trusted.timeout)
    }
    
    @Test
    fun `SafeAgentWrapper should enforce depth limits`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "test-agent"
            override suspend fun run(agentInput: String): String = "result"
        }
        val policy = SafetyPolicies.safe(maxDepth = 2)
        val invoker = InProcessRemoteAgentInvoker.of(mapOf(testAgent.id to testAgent))
        val wrapper = SafeAgentWrapper(testAgent, policy, invoker)
        
        // When - context with depth that would exceed limit
        val depthElement = AgentDepthElement(depth = 2) 
        
        withContext(depthElement) {
            // Then
            val exception = assertFailsWith<AgentSafetyException> {
                wrapper.run("test input")
            }
            assertEquals(AgentSafetyErrorCode.MAX_DEPTH_EXCEEDED, exception.errorCode)
            assertTrue(exception.message!!.contains("Maximum agent depth exceeded"))
        }
    }
    
    @Test
    fun `SafeAgentWrapper should allow execution within depth limits`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "test-agent"
            override suspend fun run(agentInput: String): String = "result"
        }
        val policy = SafetyPolicies.safe(maxDepth = 3)
        val invoker = InProcessRemoteAgentInvoker.of(mapOf(testAgent.id to testAgent))
        val wrapper = SafeAgentWrapper(testAgent, policy, invoker)
        
        // When - context with depth within limit
        val depthElement = AgentDepthElement(depth = 1)
        
        withContext(depthElement) {
            // Then - should not throw
            val result = wrapper.run("test input")
            assertEquals("result", result)
        }
    }
    
    @Test
    fun `SafeAgentWrapper should increment depth in context`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "test-agent"
            override suspend fun run(agentInput: String): String {
                // Verify depth was incremented
                val currentDepth = kotlin.coroutines.coroutineContext[AgentDepthKey()]?.depth ?: 0
                return "depth:$currentDepth"
            }
        }
        val policy = SafetyPolicies.safe(maxDepth = 5)
        val invoker = InProcessRemoteAgentInvoker.of(mapOf(testAgent.id to testAgent))
        val wrapper = SafeAgentWrapper(testAgent, policy, invoker)
        
        // When - start with depth 1
        val depthElement = AgentDepthElement(depth = 1)
        
        withContext(depthElement) {
            // Then - depth should be incremented to 2
            val result = wrapper.run("test input")
            assertEquals("depth:2", result)
        }
    }
    
    @Test
    fun `SafeAgentWrapper should enforce allowedChildren policy`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "restricted-agent"
            override suspend fun run(agentInput: String): String = "result"
        }
        val policy = SafetyPolicies.safe(allowedChildren = setOf("allowed-agent"))
        val invoker = InProcessRemoteAgentInvoker.of(mapOf(testAgent.id to testAgent))
        val wrapper = SafeAgentWrapper(testAgent, policy, invoker)
        
        // When/Then - agent not in allowedChildren should be rejected
        val exception = assertFailsWith<AgentSafetyException> {
            wrapper.run("test input")
        }
        assertEquals(AgentSafetyErrorCode.AGENT_NOT_ALLOWED, exception.errorCode)
        assertTrue(exception.message!!.contains("restricted-agent not in allowed children"))
    }
    
    @Test
    fun `SafeAgentWrapper should allow agents in allowedChildren`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "allowed-agent"
            override suspend fun run(agentInput: String): String = "result"
        }
        val policy = SafetyPolicies.safe(allowedChildren = setOf("allowed-agent"))
        val invoker = InProcessRemoteAgentInvoker.of(mapOf(testAgent.id to testAgent))
        val wrapper = SafeAgentWrapper(testAgent, policy, invoker)
        
        // When/Then - agent in allowedChildren should work
        val result = wrapper.run("test input")
        assertEquals("result", result)
    }
    
    @Test
    fun `SafetyPolicies trusted should allow any agents`() = runTest {
        // Given
        val testAgent = object : AIAgentBase<String, String> {
            override val id: String = "any-agent"
            override suspend fun run(agentInput: String): String = "result"
        }
        val policy = SafetyPolicies.trusted()
        val invoker = InProcessRemoteAgentInvoker.of(mapOf(testAgent.id to testAgent))
        val wrapper = SafeAgentWrapper(testAgent, policy, invoker)
        
        // When/Then - trusted policy should allow any agent
        val result = wrapper.run("test input")
        assertEquals("result", result)
    }
}