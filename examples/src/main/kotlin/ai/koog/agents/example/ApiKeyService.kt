package ai.koog.agents.example

internal object ApiKeyService {
    val openAIApiKey: String
        get() = System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY env is not set")

    val anthropicApiKey: String
        get() = System.getenv("ANTHROPIC_API_KEY") ?: throw IllegalArgumentException("ANTHROPIC_API_KEY env is not set")
}
