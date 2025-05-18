package ai.koog.agents.example

internal object TokenService {
    val openAIToken: String
        get() = System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY env is not set")

    val anthropicToken: String
        get() = System.getenv("ANTHROPIC_API_KEY") ?: throw IllegalArgumentException("ANTHROPIC_API_KEY env is not set")
}
