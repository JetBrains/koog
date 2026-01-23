package ai.koog.runtime

import ai.koog.agent.FlowAgent

/**
 *
 */
public class AgentFlowKoogRuntime : AgentFlowRuntime {

    override suspend fun executeAgent(agent: FlowAgent): String {
        return agent.execute()
    }
}
