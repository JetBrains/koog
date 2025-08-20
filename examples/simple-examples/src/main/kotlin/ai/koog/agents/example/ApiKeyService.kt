package ai.koog.agents.example

internal object ApiKeyService {
    val openAIApiKey: String
        get() = System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY env is not set")

    val anthropicApiKey: String
        get() = System.getenv("ANTHROPIC_API_KEY") ?: throw IllegalArgumentException("ANTHROPIC_API_KEY env is not set")

    val googleApiKey: String
        get() = System.getenv("GOOGLE_API_KEY") ?: throw IllegalArgumentException("GOOGLE_API_KEY env is not set")

    val openRouterApiKey: String
        get() = System.getenv("OPENROUTER_API_KEY")
            ?: throw IllegalArgumentException("OPENROUTER_API_KEY env is not set")

    val awsAccessKey: String
        get() = System.getenv("AWS_ACCESS_KEY_ID") ?: throw IllegalArgumentException("AWS_ACCESS_KEY_ID env is not set")

    val awsSecretAccessKey: String
        get() = System.getenv("AWS_SECRET_ACCESS_KEY")
            ?: throw IllegalArgumentException("AWS_SECRET_ACCESS_KEY env is not set")

    val brightDataKey: String
        get() = System.getenv("BRIGHT_DATA_KEY")
            ?: throw IllegalArgumentException("BRIGHT_DATA_KEY env is not set")

    val mistralAIApiKey: String
        get() = System.getenv("MISTRALAI_API_KEY") ?: throw IllegalArgumentException("MISTRALAI_API_KEY env is not set")
}
