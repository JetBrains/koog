package ai.grazie.code.agents.core.agent

import ai.grazie.code.agents.core.agent.entity.AgentNode

open class AgentException(problem: String, throwable: Throwable? = null) :
    Throwable("AI Agent has run into a problem: $problem", throwable)

class AgentStuckInTheNodeException(node: AgentNode<*, *>, output: Any?) :
    AgentException(
        "When executing agent graph, stuck in node ${node.name} " +
                "because output $output doesn't match any condition on available edges."
    )

class AgentMaxNumberOfIterationsReachedException(maxNumberOfIterations: Int) :
    AgentException(
        "Agent couldn't finish in given number of steps ($maxNumberOfIterations). " +
                "Please, consider increasing `maxAgentIterations` value in agent's configuration"
    )

class AgentTerminationByClientException(message: String) :
    AgentException("Agent was canceled by the client ($message)")