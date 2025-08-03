package ai.koog.agents.core.subagent

import ai.koog.agents.core.agent.AIAgentBase

/**
 * In-process implementation of RemoteAgentInvoker.
 * 
 * This is primarily for testing and development. In production,
 * you would typically use HTTP-based or other remote transport
 * implementations of RemoteAgentInvoker.
 */
public class InProcessRemoteAgentInvoker(
    private val agentRegistry: Map<String, AIAgentBase<*, *>>
) : RemoteAgentInvoker {

    override suspend fun <I : Any, O : Any> invoke(
        spec: RemoteAgentSpec<I, O>,
        input: I
    ): SubagentResult<O> {
        // Get agent
        val agent = agentRegistry[spec.agentId]
            ?: return SubagentResult.Failed(
                "Agent not found: ${spec.agentId}",
                errorCode = SubagentErrorCode.AGENT_NOT_FOUND
            )

        return try {
            @Suppress("UNCHECKED_CAST")
            val typedAgent = agent as AIAgentBase<I, O>
            val result = typedAgent.run(input)
            
            SubagentResult.Success(output = result)
        } catch (e: Exception) {
            SubagentResult.Failed(
                "Agent execution failed: ${e.message}",
                e,
                SubagentErrorCode.EXECUTION_FAILED
            )
        }
    }

    public companion object {
        /**
         * Creates an empty invoker with no registered agents.
         */
        public fun empty(): InProcessRemoteAgentInvoker = InProcessRemoteAgentInvoker(emptyMap())

        /**
         * Creates an invoker with the specified agents.
         */
        public fun of(agents: Map<String, AIAgentBase<*, *>>): InProcessRemoteAgentInvoker = 
            InProcessRemoteAgentInvoker(agents)
    }
}