package ai.koog.agents.core.engine

internal class UnexpectedAIAgentMessageException : IllegalStateException("Unexpected message for agent")

internal class UnexpectedDoubleInitializationException : IllegalStateException(
    "Unexpected initialization message in the middle of execution"
)
