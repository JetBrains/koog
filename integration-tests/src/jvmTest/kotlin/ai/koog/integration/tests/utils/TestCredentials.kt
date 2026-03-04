package ai.koog.integration.tests.utils

object TestCredentials {
    fun readTestAnthropicKeyFromEnv(): String? {
        return System.getenv("ANTHROPIC_API_TEST_KEY")
    }

    fun readTestOpenAIKeyFromEnv(): String? {
        return System.getenv("OPEN_AI_API_TEST_KEY")
    }

    fun readTestGoogleAIKeyFromEnv(): String? {
        return System.getenv("GEMINI_API_TEST_KEY")
    }

    fun readTestOpenRouterKeyFromEnv(): String? {
        return System.getenv("OPEN_ROUTER_API_TEST_KEY")
    }

    fun readTestMistralAiKeyFromEnv(): String? {
        return System.getenv("MISTRAL_AI_API_TEST_KEY")
    }

    fun readAwsAccessKeyIdFromEnv(): String? {
        return System.getenv("AWS_ACCESS_KEY_ID")
    }

    fun readAwsSecretAccessKeyFromEnv(): String? {
        return System.getenv("AWS_SECRET_ACCESS_KEY")
    }

    fun readAwsBedrockBearerTokenFromEnv(): String? {
        return System.getenv("AWS_BEARER_TOKEN_BEDROCK")
    }

    fun readAwsSessionTokenFromEnv(): String? {
        return System.getenv("AWS_SESSION_TOKEN")
    }

    fun readAwsBedrockGuardrailIdFromEnv(): String? {
        return System.getenv("AWS_BEDROCK_GUARDRAIL_ID")
    }

    fun readAwsBedrockGuardrailVersionFromEnv(): String? {
        return System.getenv("AWS_BEDROCK_GUARDRAIL_VERSION")
    }
}
