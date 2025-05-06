package ai.grazie.code.agents.example

internal object TokenService {
    val openAIToken: String
        get() = System.getenv("OPEN_AI_TOKEN") ?: throw IllegalArgumentException("OPEN_AI_TOKEN env is not set")

    val anthropicToken: String
        get() = System.getenv("ANTHROPIC_TOKEN") ?: throw IllegalArgumentException("ANTHROPIC_TOKEN env is not set")
}
