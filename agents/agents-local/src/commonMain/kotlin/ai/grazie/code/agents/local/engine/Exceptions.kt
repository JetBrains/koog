package ai.grazie.code.agents.local.engine

class UnexpectedAgentMessageException : IllegalStateException("Unexpected message for agent")

class UnexpectedDoubleInitializationException : IllegalStateException("Unexpected initialization message in the middle of execution")
