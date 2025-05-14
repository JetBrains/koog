package ai.grazie.code.agents.core.engine

internal class UnexpectedAIAgentMessageException : IllegalStateException("Unexpected message for agent")

internal class UnexpectedDoubleInitializationException : IllegalStateException("Unexpected initialization message in the middle of execution")
