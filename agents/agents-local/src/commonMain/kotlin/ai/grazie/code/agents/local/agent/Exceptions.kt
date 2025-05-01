package ai.grazie.code.agents.local.agent

import ai.grazie.code.agents.local.graph.LocalAgentNode


open class LocalAgentException(problem: String) : Exception("Local AI Agent has run into a problem: $problem")

class AgentStuckInTheNodeException(node: LocalAgentNode<*, *>, output: Any?) :
    LocalAgentException(
        "when executing agent graph, stuck in node ${node.name} " +
                "because output $output doesn't match any condition on available edges."
    )

class AgentMaxNumberOfIterationsReachedException(maxNumberOfIterations: Int) :
    LocalAgentException(
        "agent couldn't finish in given number of steps ($maxNumberOfIterations). " +
                "Please, consider increasing `maxAgentIterations` value in agent's configuration"
    )

class AgentTerminationByClientException(message: String) : LocalAgentException("agent was canceled by the client ($message)")