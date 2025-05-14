package ai.grazie.code.agents.core.engine

class UnexpectedAIAgentMessageException : IllegalStateException("Unexpected message for agent")

class UnexpectedDoubleInitializationException : IllegalStateException("Unexpected initialization message in the middle of execution")
