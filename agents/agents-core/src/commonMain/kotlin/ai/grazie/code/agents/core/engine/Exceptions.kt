package ai.grazie.code.agents.core.engine

public class UnexpectedAgentMessageException : IllegalStateException("Unexpected message for agent")

public class UnexpectedDoubleInitializationException : IllegalStateException("Unexpected initialization message in the middle of execution")
