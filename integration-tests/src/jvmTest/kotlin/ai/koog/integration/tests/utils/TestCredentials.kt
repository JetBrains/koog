package ai.koog.integration.tests.utils

object TestCredentials {
    private const val GUIDE_URL =
        "https://github.com/JetBrains/koog/blob/develop/TESTING.md#required-api-tokens-for-integration-tests"
    private val AWS_BEDROCK_GUARDRAIL_VERSION_PATTERN = Regex("^(?:DRAFT|[1-9][0-9]{0,7})$")

    private fun requireEnv(name: String): String {
        return System.getenv(name)
            ?: error(
                "Environment variable `$name` is not set. " +
                    "See $GUIDE_URL for setup instructions."
            )
    }

    fun readTestAnthropicKeyFromEnv(): String = requireEnv("ANTHROPIC_API_TEST_KEY")

    fun readTestOpenAIKeyFromEnv(): String = requireEnv("OPEN_AI_API_TEST_KEY")

    fun readTestGoogleAIKeyFromEnv(): String = requireEnv("GEMINI_API_TEST_KEY")

    fun readTestOpenRouterKeyFromEnv(): String = requireEnv("OPEN_ROUTER_API_TEST_KEY")

    fun readTestMistralAiKeyFromEnv(): String = requireEnv("MISTRAL_AI_API_TEST_KEY")

    fun readTestDashscopeKeyFromEnv(): String = requireEnv("DASHSCOPE_API_TEST_KEY")

    fun readAwsAccessKeyIdFromEnv(): String = requireEnv("AWS_ACCESS_KEY_ID")

    fun readAwsSecretAccessKeyFromEnv(): String = requireEnv("AWS_SECRET_ACCESS_KEY")

    fun readAwsBedrockBearerTokenFromEnv(): String = requireEnv("AWS_BEARER_TOKEN_BEDROCK")

    fun readAwsSessionTokenFromEnv(): String? {
        return System.getenv("AWS_SESSION_TOKEN")
            ?: null.also {
                println("WARNING: environment variable `AWS_SESSION_TOKEN` is not set, using default session token")
            }
    }

    fun readAwsBedrockGuardrailIdFromEnv(): String = requireEnv("AWS_BEDROCK_GUARDRAIL_ID")

    fun readAwsBedrockGuardrailVersionFromEnv(): String {
        val value = requireEnv("AWS_BEDROCK_GUARDRAIL_VERSION")
        require(AWS_BEDROCK_GUARDRAIL_VERSION_PATTERN.matches(value)) {
            "Environment variable `AWS_BEDROCK_GUARDRAIL_VERSION` has invalid format. " +
                "Expected `DRAFT` or an integer between 1 and 99999999 without leading zeros. " +
                "See $GUIDE_URL for setup instructions."
        }
        return value
    }
}
