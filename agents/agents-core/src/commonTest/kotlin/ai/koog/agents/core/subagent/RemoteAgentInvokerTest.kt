package ai.koog.agents.core.subagent

import ai.koog.agents.core.agent.AIAgentBase
import kotlinx.coroutines.test.runTest
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the transport abstraction for remote agent execution.
 */
class RemoteAgentInvokerTest {

    private class TestAgent(private val response: String) : AIAgentBase<String, String> {
        override val id: String = "test-agent"
        
        override suspend fun run(agentInput: String): String {
            return "$response: $agentInput"
        }
    }

    @Test
    fun `RemoteAgentSpec should contain correct information`() {
        // Given
        val spec = RemoteAgentSpec<String, Int>(
            agentId = "test-agent",
            inputType = typeOf<String>(),
            outputType = typeOf<Int>()
        )
        
        // Then
        assertEquals("test-agent", spec.agentId)
        assertEquals(typeOf<String>(), spec.inputType)
        assertEquals(typeOf<Int>(), spec.outputType)
    }

    @Test
    fun `InProcessRemoteAgentInvoker should execute agent successfully`() = runTest {
        // Given
        val agent = TestAgent("Processed")
        val invoker = InProcessRemoteAgentInvoker.of(mapOf("test-agent" to agent))
        val spec = RemoteAgentSpec<String, String>(
            agentId = "test-agent",
            inputType = typeOf<String>(),
            outputType = typeOf<String>()
        )
        
        // When
        val result = invoker.invoke(spec, "hello")
        
        // Then
        assertTrue(result is SubagentResult.Success)
        assertEquals("Processed: hello", result.output)
    }

    @Test
    fun `InProcessRemoteAgentInvoker should handle agent not found`() = runTest {
        // Given
        val invoker = InProcessRemoteAgentInvoker.empty()
        val spec = RemoteAgentSpec<String, String>(
            agentId = "nonexistent-agent",
            inputType = typeOf<String>(),
            outputType = typeOf<String>()
        )
        
        // When
        val result = invoker.invoke(spec, "hello")
        
        // Then
        assertTrue(result is SubagentResult.Failed)
        assertEquals(SubagentErrorCode.AGENT_NOT_FOUND, result.errorCode)
        assertTrue(result.error.contains("Agent not found: nonexistent-agent"))
    }

    @Test
    fun `InProcessRemoteAgentInvoker should handle agent execution errors`() = runTest {
        // Given
        val failingAgent = object : AIAgentBase<String, String> {
            override val id: String = "failing-agent"
            
            override suspend fun run(agentInput: String): String {
                throw RuntimeException("Simulated failure")
            }
        }
        
        val invoker = InProcessRemoteAgentInvoker.of(mapOf("failing-agent" to failingAgent))
        val spec = RemoteAgentSpec<String, String>(
            agentId = "failing-agent",
            inputType = typeOf<String>(),
            outputType = typeOf<String>()
        )
        
        // When
        val result = invoker.invoke(spec, "hello")
        
        // Then
        assertTrue(result is SubagentResult.Failed)
        assertEquals(SubagentErrorCode.EXECUTION_FAILED, result.errorCode)
        assertTrue(result.error.contains("Agent execution failed"))
    }

    @Test
    fun `inProcessRemoteInvoker DSL should work correctly`() = runTest {
        // Given
        val agent1 = TestAgent("Agent1")
        val agent2 = TestAgent("Agent2")
        
        // When
        val invoker = inProcessRemoteInvoker(
            "agent1" to (agent1 as AIAgentBase<*, *>),
            "agent2" to (agent2 as AIAgentBase<*, *>)
        )
        
        val spec1 = RemoteAgentSpec<String, String>("agent1", typeOf<String>(), typeOf<String>())
        val result1 = invoker.invoke(spec1, "test")
        
        // Then
        assertTrue(result1 is SubagentResult.Success)
        assertEquals("Agent1: test", result1.output)
    }
}