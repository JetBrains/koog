package ai.koog.integration.tests.utils

object TestCredentials {
    fun readTestAnthropicKeyFromEnv(): String {
        return System.getenv("ANTHROPIC_API_TEST_KEY")
            ?: error("ERROR: environment variable `ANTHROPIC_API_TEST_KEY` is not set")
    }

    fun readTestOpenAIKeyFromEnv(): String {
        return System.getenv("OPEN_AI_API_TEST_KEY")
            ?: error("ERROR: environment variable `OPEN_AI_API_TEST_KEY` is not set")
    }

    fun readTestGoogleAIKeyFromEnv(): String {
        return System.getenv("GEMINI_API_TEST_KEY")
            ?: error("ERROR: environment variable `GEMINI_API_TEST_KEY` is not set")
    }

    fun readTestOpenRouterKeyFromEnv(): String {
        return System.getenv("OPEN_ROUTER_API_TEST_KEY")
            ?: error("ERROR: environment variable `OPEN_ROUTER_API_TEST_KEY` is not set")
    }

    fun readTestMistralAiKeyFromEnv(): String {
        return System.getenv("MISTRAL_AI_API_TEST_KEY")
            ?: error("ERROR: environment variable `MISTRAL_AI_API_TEST_KEY` is not set")
    }

    fun readAwsAccessKeyIdFromEnv(): String {
        return System.getenv("AWS_ACCESS_KEY_ID")
            ?: error("ERROR: environment variable `AWS_ACCESS_KEY_ID` is not set")
    }

    fun readAwsSecretAccessKeyFromEnv(): String {
        return System.getenv("AWS_SECRET_ACCESS_KEY")
            ?: error("ERROR: environment variable `AWS_SECRET_ACCESS_KEY` is not set")
    }

    fun readAwsBedrockBearerTokenFromEnv(): String {
        return System.getenv("AWS_BEARER_TOKEN_BEDROCK")
            ?: error("ERROR: environment variable `AWS_BEARER_TOKEN_BEDROCK` is not set")
    }

    fun readAwsSessionTokenFromEnv(): String? {
        return System.getenv("AWS_SESSION_TOKEN")
            ?: null.also {
                println("WARNING: environment variable `AWS_SESSION_TOKEN` is not set, using default session token")
            }
    }

    fun readAwsBedrockGuardrailIdFromEnv(): String {
        return System.getenv("AWS_BEDROCK_GUARDRAIL_ID")
            ?: error("ERROR: environment variable `AWS_BEDROCK_GUARDRAIL_ID` is not set")
    }

    fun readAwsBedrockGuardrailVersionFromEnv(): String {
        return System.getenv("AWS_BEDROCK_GUARDRAIL_VERSION")
            ?: error("ERROR: environment variable `AWS_BEDROCK_GUARDRAIL_VERSION` is not set")
    }
}
