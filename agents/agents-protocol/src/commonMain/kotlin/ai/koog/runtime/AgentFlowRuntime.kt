package ai.koog.runtime

import ai.koog.agent.FlowAgent

/**
 *
 */
public interface AgentFlowRuntime {

    /**
     *
     */
    public suspend fun executeAgent(agent: FlowAgent): String
}
